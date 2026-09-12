package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.SourceProvider
import fr.moovie.tv.core.sources.port.getBody
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Provider stremio — les flux de **l'addon de l'utilisateur** (Torrentio, Comet…),
 * servis par son debrid.
 *
 * Seul catalogue de cette application qui n'a aucun site à scraper : le protocole
 * des addons Stremio est public et rend du JSON. `GET {addon}/stream/{type}/{id}.json`,
 * et `streams[]` arrive. Rien à désobfusquer, rien qui dérive au prochain
 * changement de thème WordPress.
 *
 * Ce qui le distingue vraiment des sept autres : les liens sont des **fichiers**
 * (mkv, mp4) sur le CDN du debrid, pas des pages d'hébergeur. Pas de cascade
 * d'extraction, pas de `Referer` à deviner — voir [StremioExtractor].
 *
 * ## Le pont d'identifiants
 *
 * Stremio n'indexe que l'IMDB, cette application que le TMDB. La traduction
 * passe par `/external_ids` et se mémorise ; c'est le seul appel réseau
 * supplémentaire, et il est mutualisé avec le reste de l'app.
 *
 * Un titre que TMDB ne relie à aucun IMDB rend une liste vide. Ce n'est pas une
 * panne : il n'y a simplement rien à demander à l'addon.
 *
 * ## Rien n'est fourni, tout se configure
 *
 * L'application n'embarque **aucune URL d'addon et aucun abonnement** : ce
 * catalogue ne sert que ce que l'utilisateur a déjà. Tant que le réglage est
 * vide, il rend une liste vide **sans émettre la moindre requête**, et les
 * autres catalogues travaillent comme avant. L'URL se colle depuis le téléphone,
 * par la page d'appairage, parce qu'elle fait une centaine de caractères et
 * qu'aucun clavier de télécommande ne rend ça supportable.
 *
 * ## Pourquoi les flux non mis en cache sont écartés
 *
 * Un flux marqué `[RD+]` (ou `[AD+]`, `[PM+]`, `[TB+]`) est **déjà sur les
 * serveurs du debrid** : son URL joue tout de suite. Sans ce marqueur, le lien
 * ne fait que *demander* au debrid d'aller chercher le torrent — il répond au
 * bout de plusieurs minutes, ou jamais si plus personne ne partage.
 *
 * Or la promesse de cette application est un seul appui sur **Lire**. Servir un
 * lien qui met dix minutes à exister, c'est un bouton qui ne fait rien, et
 * l'utilisateur n'a aucun moyen de savoir lequel des deux cas il vient de
 * toucher. On ne rapporte donc que ce qui est jouable maintenant.
 *
 * Mesuré par `stremio-mpv` sur 77 flux d'un même titre : Torrentio **ne déclare
 * ni taille ni seeders en champ structuré** (`behaviorHints.videoSize` absent
 * des 77). Tout est dans le texte de `title`, d'où l'analyse par motif.
 */
class StremioProvider(
    private val http: HttpGateway,
    /**
     * N'accepter que les flux déjà présents chez le debrid. À passer à false
     * pour un usage où l'attente est acceptable — jamais sur une télévision.
     */
    private val cachedOnly: Boolean = true,
    /**
     * Valeurs imposées, au lieu de celles des réglages. Coutures pour les
     * **sondes**, qui tournent hors de l'application et n'ont donc pas de
     * magasin de réglages à interroger. Null en fonctionnement normal.
     */
    private val addonUrlImpose: String? = null,
    private val cleTmdbImposee: String? = null,
    /**
     * Dépendances **différées**, et pas par goût de l'indirection : `all` de
     * `ProviderRegistry` se construit au premier accès, y compris depuis l'écran
     * des réglages qui ne veut que la liste des noms. Ouvrir un DataStore à ce
     * moment-là serait un effet de bord invisible et hors sujet. Elles ne se
     * construisent donc qu'à la première recherche de sources — et jamais dans
     * les tests d'analyse, qui n'appellent que [parse].
     */
    fabriqueReglages: () -> SettingsRepository = { SettingsRepository() },
    fabriqueTmdb: () -> TmdbRepository = { TmdbRepository() },
) : SourceProvider {

    override val name = "stremio"

    private val settings by lazy(fabriqueReglages)
    private val tmdb by lazy(fabriqueTmdb)

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> {
        val base = (addonUrlImpose ?: settings.stremioAddonUrl.first()).trim().trimEnd('/')
        if (base.isBlank()) return emptyList()

        val apiKey = cleTmdbImposee ?: settings.tmdbApiKey.first()
        val isTv = media is MediaRef.Episode
        val imdb = tmdb.imdbId(apiKey, isTv, media.tmdbId) ?: return emptyList()

        val (type, id) = when (media) {
            is MediaRef.Movie -> "movie" to imdb
            is MediaRef.Episode -> "series" to "$imdb:${media.season}:${media.episode}"
        }

        // Le `User-Agent` n'est pas décoratif : mesuré, Torrentio répond **403**
        // à une requête qui n'en porte pas d'ordinaire (`Python-urllib` refusé,
        // `stremio-mpv/1.0` accepté). OkHttp enverrait `okhttp/4.x`, qui tombe
        // dans la même catégorie. La panne aurait été muette — un catalogue qui
        // rend une liste vide sur chaque fiche, sans erreur nulle part.
        val body = http.getBody(
            "$base/stream/$type/$id.json",
            headers = mapOf("User-Agent" to Ua.BROWSER, "Accept" to "application/json"),
        ) ?: return emptyList()
        return parse(body, if (media is MediaRef.Episode) CIBLE_EPISODE else CIBLE_FILM)
    }

    /**
     * Sans réseau, donc testable sur une réponse enregistrée — c'est là que vit
     * tout ce qui peut se tromper.
     */
    internal fun parse(body: String, cible: Long = CIBLE_FILM): List<EmbedLink> {
        val streams = runCatching {
            json.parseToJsonElement(body).jsonObject["streams"]?.jsonArray
        }.getOrNull() ?: return emptyList()

        val seen = mutableSetOf<String>()
        return streams.mapNotNull { element ->
            val o = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            // Pas d'`url` = flux magnet pur, à jouer par un moteur torrent que
            // cette application n'a pas. L'écarter ici plutôt que de le laisser
            // échouer dans le lecteur.
            val url = o["url"]?.jsonPrimitive?.contentOrNull() ?: return@mapNotNull null

            val name = o["name"]?.jsonPrimitive?.contentOrNull().orEmpty()
            val title = o["title"]?.jsonPrimitive?.contentOrNull().orEmpty()
            val cached = CACHED_RE.containsMatchIn(name)
            if (cachedOnly && !cached) return@mapNotNull null

            // Dédoublonnage sur l'**URL exacte**, jamais sur l'infohash : mesuré
            // sur 77 flux, 75 infohash distincts mais 77 URL — deux entrées
            // partagent un torrent et diffèrent par le fichier visé, le film
            // d'un côté et un bonus de l'autre. Ce ne sont pas des doublons.
            if (!seen.add(url)) return@mapNotNull null

            val filename = o["behaviorHints"]?.jsonObject
                ?.get("filename")?.jsonPrimitive?.contentOrNull()
                ?: title.lineSequence().firstOrNull().orEmpty()

            Classe(
                lien = EmbedLink(
                    url = url,
                    hoster = StremioExtractor.HOSTER,
                    language = languageOf(filename),
                    variant = variantOf(name, title, cached),
                ),
                definition = qualityRank(qualityOf(name)),
                octets = sizeBytes(title),
            )
        }.sortedWith(compareBy({ it.definition }, { ecart(it.octets, cible) })).map { it.lien }
    }

    /** Un lien et les deux nombres qui décident de sa place. Interne au tri. */
    private data class Classe(val lien: EmbedLink, val definition: Int, val octets: Long?)

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        content.takeIf { it.isNotBlank() && it != "null" }

    internal companion object {

        /** `[RD+]`, `[AD+]`, `[PM+]`, `[TB+]`, `[DL+]` — le `+` dit « déjà chez eux ». */
        val CACHED_RE = Regex("""\[[A-Z]{2}\+]""")

        /** `💾 1.78 GB` — le poids, lu dans le texte de `title` faute de champ. */
        val SIZE_RE = Regex("""(\d+(?:[.,]\d+)?)\s*(TB|GB|MB)""", RegexOption.IGNORE_CASE)

        /** Deuxième ligne de `name` chez Torrentio : `1080p`, `4k`, `720p`… */
        fun qualityOf(name: String): String? =
            name.lineSequence().drop(1).firstOrNull()?.trim()?.takeIf { it.isNotBlank() }

        /** Facteurs des unités que Torrentio écrit dans son texte. */
        val UNITES = mapOf("MB" to 1_000_000L, "GB" to 1_000_000_000L, "TB" to 1_000_000_000_000L)

        /**
         * Poids en octets, ou null si le texte n'en porte pas.
         *
         * Sert au tri, pas à l'affichage : il faut un nombre pour comparer, et
         * la chaîne « 3.41 GB » n'en est pas un.
         */
        fun sizeBytes(title: String): Long? {
            val m = SIZE_RE.find(title) ?: return null
            val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            val u = UNITES[m.groupValues[2].uppercase()] ?: return null
            return (n * u).toLong()
        }

        /**
         * Poids visé, par type de média. Ni un plancher ni un plafond : le point
         * dont on s'éloigne le moins.
         *
         * Trier simplement du plus léger au plus lourd paraissait prudent — il
         * mettait en tête, mesuré sur *Dune*, un fichier de **500 Mo** pour deux
         * heures et demie, soit un débit qui se voit à l'écran. Trier à
         * l'inverse mettait un REMUX de 40 Go que le téléviseur ne décode pas.
         * Les deux extrêmes sont mauvais, et c'est ce qui disqualifie un tri par
         * poids : ce qu'on cherche est une plage, pas un bout de liste.
         *
         * 4 Go pour un film de deux heures, 1,5 Go pour un épisode de
         * quarante-cinq minutes : à peu près le même débit, environ 4 Mb/s, qui
         * est le palier où un encodage 1080p cesse de montrer ses défauts sans
         * demander plus que ce qu'une connexion domestique sert en continu.
         */
        const val CIBLE_FILM = 4_000_000_000L
        const val CIBLE_EPISODE = 1_500_000_000L

        /**
         * Écart au poids visé. Un flux dont l'addon ne déclare pas le poids
         * passe en dernier de son rang plutôt que d'être écarté : il est
         * peut-être parfait, mais rien ne permet de le dire, et le mettre devant
         * un flux mesuré serait un pari fait à la place de l'utilisateur.
         */
        fun ecart(octets: Long?, cible: Long): Long =
            if (octets == null) Long.MAX_VALUE else kotlin.math.abs(octets - cible)

        /**
         * Rang de définition, du plus jouable au moins jouable **sur la télé
         * visée** — et non de la meilleure image à la moins bonne.
         *
         * L'ordre 1080p, 720p, puis 4K n'est pas une erreur de frappe. Le
         * téléviseur est un TCL sous Android 9 en ARM 32 bits : un remux 2160p
         * HEVC 10 bits avec piste TrueHD n'y sera pas décodé, et il pèse de
         * toute façon plus que ce qu'une connexion domestique sait servir en
         * temps réel. Mesuré sur *Dune* : le premier flux en cache que l'addon
         * propose est un REMUX de **39,9 Go** en DTS-HD MA / TrueHD Atmos.
         *
         * Sans ce classement, le bouton **Lire** — qui joue le premier lien de
         * la langue voulue — tomberait dessus à chaque fois, et le catalogue
         * passerait pour cassé alors qu'il a rendu 181 flux dont 75 jouables.
         * Les autres restent dans le panneau des sources, à un appui près.
         */
        fun qualityRank(quality: String?): Int = when {
            quality == null -> 3
            quality.contains("1080", ignoreCase = true) -> 0
            quality.contains("720", ignoreCase = true) -> 1
            quality.contains("2160", ignoreCase = true) || quality.contains("4k", ignoreCase = true) -> 4
            quality.contains("480", ignoreCase = true) -> 2
            else -> 3
        }

        /**
         * Langue déduite du **nom de fichier**, seul endroit où la release la
         * déclare — l'addon, lui, n'a pas de champ pour ça.
         *
         * `MULTi` compte comme VF : la convention des releases francophones est
         * qu'il porte au moins une piste française en plus de l'originale.
         * Faute de marqueur, on conclut VO et non VF : les catalogues Stremio
         * sont anglophones par défaut, et se tromper vers la VO fait manquer un
         * doublage, alors que se tromper vers la VF met un film anglais dans la
         * section française — l'erreur qui se voit au moment de lire.
         */
        fun languageOf(filename: String): String {
            val f = filename.uppercase()
            return when {
                Regex("""\bVOSTFR\b""").containsMatchIn(f) -> "VOSTFR"
                Regex("""\b(TRUEFRENCH|FRENCH|VFF|VFQ|VF2?|MULTI)\b""").containsMatchIn(f) -> "VF"
                else -> "VO"
            }
        }

        /**
         * Ce qui sépare deux liens du même debrid dans la même langue : la
         * définition et le poids. Sur ce catalogue il y en a des dizaines par
         * titre, c'est donc le seul critère de choix qui reste à l'écran.
         *
         * Le poids n'est pas décoratif : c'est lui qui dit qu'un remux de 60 Go
         * ne passera pas sur une connexion domestique, là où la définition seule
         * laisserait croire que tous les « 1080p » se valent.
         */
        fun variantOf(name: String, title: String, cached: Boolean): String? {
            val parts = buildList {
                qualityOf(name)?.let { add(it) }
                SIZE_RE.find(title)?.let { add(it.value.replace(',', '.')) }
                if (!cached) add("à mettre en cache")
            }
            return parts.joinToString(" · ").takeIf { it.isNotBlank() }
        }
    }
}
