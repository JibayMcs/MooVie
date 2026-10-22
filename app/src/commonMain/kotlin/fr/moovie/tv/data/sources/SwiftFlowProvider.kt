package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.SourceProvider
import fr.moovie.tv.core.sources.port.getBody
import fr.moovie.tv.shared.maintenantMs
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Provider SwiftFlow — **API JSON indexée par ID TMDB**, films et séries.
 *
 * ### Ce qu'il apporte que les autres n'ont pas
 *
 * Il ne rend pas un lien d'hébergeur mais **le fichier lui-même** : un MP4
 * progressif, servi depuis son propre CDN. Pas d'embed à ouvrir, pas de
 * JavaScript à désobfusquer, donc rien qui casse au prochain changement de
 * format — la classe d'ennuis qui a tué la plupart des scrapers portés depuis
 * Movix. C'est la source la plus robuste du catalogue, et c'est celle qui
 * demande le moins de code.
 *
 * Un MP4 progressif se comporte aussi mieux qu'un HLS sur une liaison correcte :
 * une seule connexion, pas de re-négociation par segment, et un déplacement dans
 * le film qui tombe juste au lieu d'atterrir sur la frontière de segment
 * précédente (voir le compensateur de `VlcjPlayerController`).
 *
 * ### La chaîne
 *
 * ```
 * ?route=movies/{tmdb}   → { success, data: { sources: [{ url, language, size… }] } }
 * ?route=series/{tmdb}   → { success, data: { seasons: [{ season:"S01", episodes:[…] }] } }
 * ```
 *
 * Les `url` rendues sont **non signées**. Elles répondent 302 vers une variante
 * horodatée (`?ff=…`) qu'il suffit de suivre : inutile de passer par la page du
 * lecteur et son couple `ENCRYPTED_PAYLOAD`/`ENCRYPTED_IV`, qui n'existe que
 * pour le lecteur web. Mesuré : 206 `video/mp4`, deux redirections.
 *
 * Le CDN exige en revanche un `Referer`, sans quoi Cloudflare rend 403 — c'est
 * tout l'objet de [SwiftFlowExtractor].
 *
 * ### La clé d'API
 *
 * Publique au sens propre : SwiftFlow l'embarque dans l'URL du lecteur servie à
 * tous les navigateurs. Elle appartient néanmoins au déploiement qui s'en sert,
 * et un jour où elle sera révoquée, elle le sera pour tout le monde d'un coup.
 * D'où le repli silencieux — une liste vide, jamais une erreur — et une constante
 * isolée, facile à remplacer.
 */
class SwiftFlowProvider(private val http: HttpGateway) : SourceProvider {

    override val name = "swiftflow"

    private val json = Json { ignoreUnknownKeys = true }

    /** Le dernier domaine qui a servi du JSON. Essayé en premier au suivant. */
    @Volatile private var cachedBase: String? = null

    /** Instant avant lequel on ne retente rien — voir [apiCall]. */
    @Volatile private var panneJusqua = 0L

    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> {
        val route = when (media) {
            is MediaRef.Movie -> "movies/${media.tmdbId}"
            // Une seule requête pour toute la série : l'API ne sait pas
            // découper par saison, et demander épisode par épisode ferait vingt
            // appels là où un seul rend le catalogue entier.
            is MediaRef.Episode -> "series/${media.tmdbId}"
        }

        val body = apiCall(route) ?: return emptyList()

        val data = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            if (root["success"]?.str() != "true") return emptyList()
            root["data"]?.jsonObject
        }.getOrNull() ?: return emptyList()

        val entries = when (media) {
            is MediaRef.Movie -> data["sources"]?.asArray().orEmpty()
            is MediaRef.Episode -> episodesOf(data, media.season, media.episode)
        }

        return entries.mapNotNull { element ->
            val o = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val url = o["url"]?.str()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            EmbedLink(
                url = url,
                hoster = HOSTER,
                language = languageOf(o["language"]?.str()),
                // La taille départage deux copies du même épisode bien mieux que
                // la qualité, qui vaut « Unknown » presque partout.
                variant = o["quality"]?.str()?.takeIf { it.isNotBlank() && it != "Unknown" }
                    ?: o["size"]?.str()?.takeIf { it.isNotBlank() },
            )
        }.distinctBy { it.url }
    }

    /**
     * Appelle l'API sur le **premier domaine qui répond**, et retient lequel.
     *
     * ## Pourquoi ce n'est pas une constante
     *
     * Ce partenaire a déménagé trois fois — `deliciouss`, `blinkflux.lol`,
     * `swiftflow.lol` — et chaque déménagement a coûté une release, donc une
     * mise à jour à installer, pour une ligne. Entre les deux, la source rend
     * zéro titre sans rien dire.
     *
     * ## Ce que la sonde doit prouver
     *
     * Qu'on parle bien à **l'API**, pas qu'un serveur a répondu. Un domaine
     * revendu rend volontiers 200 avec une page de parking, et un `522`
     * Cloudflare prouve seulement que le nom existe encore. On exige donc du
     * JSON : c'est l'appel qu'on allait faire de toute façon, il sert de sonde
     * sans coûter une requête de plus — contrairement à Frembed, qui doit
     * interroger `/api/dns/domains` à part.
     *
     * ## Pourquoi en parallèle
     *
     * La cascade donne **douze secondes** à chaque catalogue, et un domaine mort
     * ne répond pas « non » : il ne répond pas, et le client attend jusqu'à son
     * délai de connexion. Essayés l'un après l'autre, un domaine mort en tête
     * consommait tout le budget ; l'appel était annulé avant d'atteindre le bon,
     * qui n'était donc **jamais retenu** — et tout recommençait à la fiche
     * suivante. Relevé le 22/10/2026 sur la box : `swiftflow.lol` muet en tête de
     * l'annuaire, `blinkflux.lol` sain derrière, et swiftflow « injoignable » sur
     * tous les titres. Le desktop ne le voyait pas, sa sonde n'ayant pas de
     * budget.
     *
     * Les candidats partent donc **en échelon** : le suivant n'attend pas que le
     * précédent ait renoncé, seulement [ECHELON_MS] — ou moins, si tous ceux
     * d'avant ont déjà échoué. Le premier qui rend du JSON gagne et les autres
     * sont annulés. Dans le cas sain, le premier répond bien avant l'échelon et
     * c'est **une seule requête**, comme avant ; un domaine muet ne coûte plus
     * que l'échelon, au lieu du budget entier.
     *
     * ## Pourquoi un délai après un échec
     *
     * Sans lui, chaque fiche ouverte pendant une panne relancerait la marche
     * complète. Une minute de silence coûte moins cher qu'une application qui
     * rame à chaque titre.
     */
    private suspend fun apiCall(route: String): String? {
        if (maintenantMs() < panneJusqua) return null

        // Le domaine retenu d'abord, seul : c'est le cas courant, et il ne doit
        // pas payer la course. S'il ne répond plus, on l'oublie et on relance
        // la découverte.
        cachedBase?.let { base ->
            appel(base, route)?.let { return it }
            cachedBase = null
        }

        // L'annuaire avant les domaines compilés : c'est ce qu'on sait
        // aujourd'hui, eux sont le plancher du jour de la compilation.
        val candidats = DomainBook.bases(http, name, BASES_CONNUES)
        val (base, body) = premierQuiRepond(candidats, route) ?: run {
            panneJusqua = maintenantMs() + ECHEC_TTL_MS
            return null
        }
        cachedBase = base
        SwiftFlowBase.courant = base
        panneJusqua = 0L
        return body
    }

    /** Le premier candidat à servir du JSON, avec son corps ; les autres sont annulés. */
    private suspend fun premierQuiRepond(candidats: List<String>, route: String): Pair<String, String>? =
        coroutineScope {
            val gagnant = CompletableDeferred<Pair<String, String>?>()
            // Complété quand le candidat de même rang a renoncé : c'est ce qui
            // permet au suivant de partir sans attendre son échelon.
            val renonces = candidats.map { CompletableDeferred<Unit>() }
            val essais = candidats.mapIndexed { i, base ->
                launch {
                    withTimeoutOrNull(i * ECHELON_MS) { renonces.take(i).awaitAll() }
                    val body = appel(base, route)
                    if (body != null) gagnant.complete(base to body) else renonces[i].complete(Unit)
                }
            }
            launch {
                essais.joinAll()
                gagnant.complete(null)
            }
            gagnant.await().also { coroutineContext.cancelChildren() }
        }

    /**
     * Le corps rendu par [base], ou null s'il ne sert pas l'API : injoignable,
     * 5xx, ou un corps qui n'est pas du JSON — une page de parking répond 200,
     * et la prendre pour l'API arrêterait la bascule sur le mauvais domaine.
     */
    private suspend fun appel(base: String, route: String): String? =
        http.getBody(
            "$base/api/v1/index.php?route=$route&api_key=$API_KEY",
            mapOf("User-Agent" to Ua.BROWSER, "Accept" to "application/json"),
        )?.takeIf { it.trimStart().startsWith("{") }

    /**
     * L'épisode demandé, dans la saison demandée.
     *
     * Les saisons se déclarent « S01 », « S1 » ou « Saison 1 » selon l'entrée du
     * catalogue : on ne compare donc que les chiffres. Comparer les chaînes
     * faisait manquer des saisons entières sans que rien ne le signale.
     */
    private fun episodesOf(data: JsonObject, season: Int, episode: Int): List<JsonElement> {
        val seasons = data["seasons"]?.asArray().orEmpty()
        val match = seasons.firstOrNull { element ->
            val label = runCatching { element.jsonObject["season"]?.str() }.getOrNull()
            label?.filter { it.isDigit() }?.toIntOrNull() == season
        } ?: return emptyList()

        return runCatching { match.jsonObject["episodes"]?.asArray().orEmpty() }
            .getOrDefault(emptyList())
            .filter { element ->
                runCatching {
                    element.jsonObject["episode_number"]?.str()?.toIntOrNull() == episode
                }.getOrDefault(false)
            }
    }

    private fun JsonElement.asArray(): List<JsonElement> =
        runCatching { (this as JsonArray).jsonArray.toList() }.getOrDefault(emptyList())

    private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.content

    /**
     * L'API déclare déjà « VF » ou « VOSTFR ». On normalise quand même : c'est
     * cette valeur qui décide de la lecture directe et du tri, et une casse
     * inattendue la ferait passer pour une langue inconnue.
     */
    private fun languageOf(raw: String?): String? = when (raw?.trim()?.uppercase()) {
        "VF", "FRENCH", "FR" -> "VF"
        "VOSTFR", "VOST" -> "VOSTFR"
        "VO", "VOSTA", "ENGLISH", "EN" -> "VO"
        else -> raw?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
    }

    /** `internal` : [SwiftFlowBase] lit [API_BASE] comme valeur par défaut. */
    internal companion object {
        /**
         * Domaine du partenaire — **il tourne, et c'est déjà la troisième fois**.
         *
         * `deliciouss` → `blinkflux.lol` → `swiftflow.lol`. Le nouveau vient du
         * `.env.example` de l'amont (movixcorp/MovixOpenSource), qui ne mentionne
         * plus blinkflux nulle part.
         *
         * Les routes, elles, n'ont pas changé : `movies/{id}` et `series/{id}`
         * rendent toujours le JSON des sources. Les variantes `/player` que
         * l'amont documente à côté ne servent qu'à fabriquer une iframe pour son
         * frontend — nous lisons le catalogue directement.
         */
        const val API_BASE = "https://blinkflux.lol"

        /**
         * Les points d'entrée essayés dans l'ordre — voir [apiCall].
         *
         * `blinkflux.lol` est **conservé derrière** le domaine courant plutôt que
         * supprimé, et ce n'est pas de la nostalgie : au 23/09/2026 les deux sont
         * à terre — `swiftflow.lol` rend 522 depuis huit pays, `blinkflux.lol`
         * n'accepte plus aucune connexion — et la production de l'amont pointe
         * encore sur le second, qu'elle masque derrière un cache périmé. Nul ne
         * sait donc lequel reviendra. Les garder tous les deux, c'est laisser
         * l'application le découvrir elle-même, sans release.
         *
         * Au 22/10/2026 c'est `blinkflux.lol` qui est revenu, et il passe donc
         * **devant** : `swiftflow.lol` ne répond plus du tout, et le laisser en
         * tête coûtait le délai d'attente complet du client (~15 s) à la
         * première fiche de chaque lancement, avant que la cascade n'affiche
         * quoi que ce soit.
         */
        private val BASES_CONNUES = listOf(API_BASE, "https://swiftflow.lol")

        /**
         * Délai avant de lancer le candidat suivant sans attendre que le
         * précédent renonce. L'API répond en moins d'une demi-seconde ; trois
         * candidats échelonnés tiennent largement dans les douze secondes que
         * la cascade accorde au catalogue.
         */
        private const val ECHELON_MS = 1_500L

        /** Après un échec des deux, on se tait une minute plutôt que de ramer. */
        private const val ECHEC_TTL_MS = 60L * 1000

        /**
         * Clé du lecteur SwiftFlow. Non secrète — elle circule en clair dans
         * l'URL de l'iframe servie à tous les navigateurs — mais partagée : si
         * elle tombe, la source disparaît proprement plutôt que d'échouer fort.
         */
        const val API_KEY =
            "ff_5ae9661d6220e612a00645cb2889d6da5231504cbb68cc32214030b1a783e8e3"

        const val HOSTER = "swiftflow"
    }
}
