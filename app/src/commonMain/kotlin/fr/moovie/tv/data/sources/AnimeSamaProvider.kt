package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpMethod
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.SourceProvider
import fr.moovie.tv.core.sources.port.getBody
import fr.moovie.tv.core.sources.usecase.isHosterSupported
import fr.moovie.tv.shared.dispatcherEs
import kotlin.concurrent.Volatile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Provider anime-sama (anime-sama.to) - port de API/Mainapi/routes animeSama.
 * Renvoie des liens d'embed (ansembed, vidmoly, sibnet…) à résoudre ensuite.
 *
 * ### Flux de résolution (relevé sur le site le 12/11/2026)
 *
 * Ce format a été relevé sur anime-sama.to le 12/11/2026 et figé dans les
 * fixtures `naruto_saison1_vf_reelle.html` (page de saison) et
 * `lien_reponse.json` (réponse de `lien.php`), lues par `AnimeSamaSeasonPageTest`.
 *
 * 1. `fetch.php` (recherche) puis la page catalogue, qui dit si la saison
 *    existe (`panneauAnime("Nom","saisonX/lang")`).
 * 2. Pour chaque langue, la **page de saison** `<catalogue>/saisonX/<lang>/`.
 *    Les listes y sont désormais en ligne, dans un `<script data-source="bdd">` :
 *    `var eps1 = ["lien:187332", …]` (un tableau par lecteur, index =
 *    épisode), `var epsMeta = {"187332":["ansembed.net"], …}` (hôtes annoncés)
 *    et `var epsJeton = "…"` (jeton de page, horodaté).
 * 3. Un repère `lien:N` se résout par
 *    `GET /api/lien.php?j=<epsJeton>&l=<N>` (Referer = page de saison), qui
 *    rend `{"u":"https://…embed…"}`. **Seul l'épisode demandé** est résolu :
 *    un appel par lecteur, pas un par épisode de la saison.
 * 4. L'ancien format (URL en clair dans `epsN`) peut coexister : il ne passe
 *    qu'en https, comme un lien résolu ; et si la page de saison ne porte aucun tableau, on retente
 *    l'ancien `episodes.js`, devenu 404 sur les catalogues migrés.
 *
 * Ce qui a cassé ce jour-là ne levait rien : `episodes.js` rendait une page
 * 404 en HTML, sans tableau, donc zéro lien sur chaque épisode - le catalogue
 * muet que seules les sondes de couverture voient.
 */
class AnimeSamaProvider(private val http: HttpGateway) : SourceProvider {

    override val name = "animesama"

    // anime-sama s'indexe par slug de catalogue, pas par TMDB : l'ID TMDB de
    // MediaRef ne lui sert à rien.

    /**
     * Le domaine servi. [BASE] tant que l'annuaire ne dit rien d'autre.
     *
     * Résolu à chaque entrée plutôt que figé à la construction : le provider
     * vit aussi longtemps que l'application, et un domaine corrigé en cours de
     * route doit s'appliquer sans redémarrage.
     */
    @Volatile private var baseCourante = BASE

    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> = withContext(dispatcherEs) {
        baseCourante = DomainBook.base(http, name, BASE)
        when (media) {
            is MediaRef.Movie -> linksFor(media.title, seasonPrefix = "film", episode = 1)
            is MediaRef.Episode ->
                linksFor(media.title, seasonPrefix = "saison${media.season}", episode = media.episode)
        }
    }

    private suspend fun linksFor(title: String, seasonPrefix: String, episode: Int): List<EmbedLink> {
        val catalogueUrl = search(title) ?: return emptyList()
        val page = get(catalogueUrl) ?: return emptyList()

        // (nom, chemin) ; on ne garde que les vraies entrées (chemin saisonX/lang ou film/lang).
        val panels = PANEL.findAll(page).map { it.groupValues[1] to it.groupValues[2] }.toList()

        // Les panneaux servent à savoir **si la saison existe**, pas dans quelle
        // langue : la page n'en annonce généralement qu'une, le VOSTFR, alors
        // que la VF est là, en chemin frère, sans être listée nulle part.
        //
        // Mesuré sur anime-sama : `naruto/saison1/vf/episodes.js` rend 660 URL
        // quand la page ne montre que `saison1/vostfr`. Idem pour Frieren,
        // Fullmetal Alchemist, One Piece, Demon Slayer — tous doublés, tous
        // invisibles. Lire les panneaux revenait donc à ne jamais proposer de VF
        // sur le seul catalogue d'animés de l'application.
        if (panels.none { it.second.startsWith("$seasonPrefix/") }) return emptyList()

        val links = mutableListOf<EmbedLink>()
        for ((suffix, lang) in LANGS) {
            val dossier = "$catalogueUrl$seasonPrefix/$suffix/"
            links += linksForLanguage(catalogueUrl, dossier, episode, lang)
        }
        return links.distinctBy { it.url }
    }

    /**
     * Les liens d'une langue pour [episode] : la page de saison d'abord, puis
     * l'ancien `episodes.js` si elle ne porte aucun tableau `epsN`.
     */
    private suspend fun linksForLanguage(
        catalogueUrl: String,
        dossier: String,
        episode: Int,
        lang: String,
    ): List<EmbedLink> {
        get(dossier, referer = catalogueUrl)?.let { page ->
            val saison = parseSeasonPage(page)
            if (saison.lecteurs.isNotEmpty()) return resolve(saison, episode, dossier, lang)
        }
        // Repli : catalogues pas encore migrés, dont les listes vivent encore
        // dans un fichier à part.
        val js = get("${dossier}episodes.js", referer = dossier) ?: return emptyList()
        return resolve(parseSeasonPage(js), episode, dossier, lang)
    }

    /**
     * L'entrée [episode] de chaque lecteur, rendue en lien d'embed.
     *
     * Une URL en clair passe si elle est en https, avec le même contrôle qu'un
     * lien résolu. Un repère `lien:N` coûte **un**
     * appel à `lien.php`, et seulement pour l'épisode demandé : résoudre la
     * saison entière, ce serait des centaines de requêtes par clic. Un repère
     * qui ne se résout pas est ignoré, les autres continuent.
     */
    private suspend fun resolve(
        saison: SeasonPage,
        episode: Int,
        dossier: String,
        lang: String,
    ): List<EmbedLink> {
        val entrees = saison.lecteurs.mapNotNull { it.getOrNull(episode - 1) }.distinct()
        return coroutineScope {
            entrees.map { entree ->
                async {
                    val url = if (entree.startsWith("https://") || entree.startsWith("http://")) {
                        acceptable(entree)
                    } else {
                        val id = LIEN.matchEntire(entree)?.groupValues?.get(1)
                        when {
                            id == null -> null
                            !worthResolving(saison.meta[id]) -> null
                            else -> saison.jeton?.let { resolveLien(it, id, dossier) }
                        }
                    }
                    url?.let { EmbedLink(url = it, hoster = hosterOf(it), language = lang) }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * `GET /api/lien.php?j=<jeton>&l=<N>` rend `{"u":"https://…"}` ; on rend
     * l'URL, ou null.
     *
     * Le Referer est la page de saison, comme le fait le site. Tout ce qui
     * n'est pas un objet JSON portant une URL https dans `u` vaut null : on ne
     * fabrique jamais d'URL à partir d'une réponse qu'on ne comprend pas.
     */
    private suspend fun resolveLien(jeton: String, id: String, dossier: String): String? {
        val origine = originOf(dossier, baseCourante)
        val corps = get("$origine/api/lien.php?j=$jeton&l=$id", referer = dossier) ?: return null
        val objet = runCatching { Json.parseToJsonElement(corps) as? JsonObject }.getOrNull()
            ?: return null
        val u = (objet["u"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return acceptable(u)
    }

    /**
     * Le contrôle commun aux deux formats : https et un hôte non vide. Une URL
     * en clair (ancien format) et une URL résolue passent par la même porte.
     */
    private fun acceptable(url: String): String? =
        url.takeIf { it.startsWith("https://") && hosterOf(it) != "?" }

    private suspend fun search(title: String): String? {
        val html = http.fetch(
            HttpRequest(
                url = "$baseCourante/template-php/defaut/fetch.php",
                method = HttpMethod.POST,
                form = mapOf("query" to title),
                headers = mapOf("User-Agent" to Ua.BROWSER),
            ),
        )?.takeIf { it.isSuccessful }?.body ?: return null
        val href = cataloguePath(html, title) ?: return null
        return "$baseCourante$href".let { if (it.endsWith("/")) it else "$it/" }
    }

    /**
     * [referer] n'est pas décoratif pour `episodes.js` : depuis octobre 2026, le
     * pare-feu Cloudflare d'anime-sama répond **403 « Attention Required »** à
     * toute requête sur ce fichier qui n'annonce pas la page de saison comme
     * origine. Le catalogue, la recherche et la fiche passaient toujours, donc
     * le provider ne levait rien : il trouvait l'animé, puis rendait zéro lien
     * sur chaque épisode. Relevé du 22/10/2026, `animesama 4 → 0`.
     */
    private suspend fun get(url: String, referer: String? = null): String? =
        http.getBody(
            url,
            buildMap {
                put("User-Agent", Ua.BROWSER)
                referer?.let { put("Referer", it) }
            },
        )

    companion object {
        const val BASE = "https://anime-sama.to"

        /**
         * Ce que déclare une page de saison, ou un ancien `episodes.js`.
         *
         * [lecteurs] : un tableau par lecteur (`eps1`, `eps2`…), index =
         * épisode ; chaque entrée est une URL en clair (ancien format) ou un
         * repère `lien:N` (nouveau). [meta] : `epsMeta`, repère vers hôtes
         * annoncés. [jeton] : `epsJeton`, à repasser à `lien.php`.
         */
        internal class SeasonPage(
            val lecteurs: List<List<String>>,
            val meta: Map<String, List<String>>,
            val jeton: String?,
        )

        internal fun parseSeasonPage(texte: String): SeasonPage {
            val lecteurs = EPS.findAll(texte).map { m ->
                CHAINE.findAll(m.groupValues[1])
                    .map { (it.groups[1] ?: it.groups[2])?.value.orEmpty().trim() }
                    .toList()
            }.filter { it.isNotEmpty() }.toList()
            val meta = META.find(texte)?.groupValues?.get(1)?.let { brut ->
                val objet = runCatching { Json.parseToJsonElement(brut) as? JsonObject }.getOrNull()
                objet?.mapValues { (_, v) ->
                    (v as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                        .orEmpty()
                }
            }.orEmpty()
            return SeasonPage(lecteurs, meta, JETON.find(texte)?.groupValues?.get(1))
        }

        /**
         * Vrai s'il vaut la peine d'appeler `lien.php` pour un repère dont
         * `epsMeta` annonce [hotes].
         *
         * Ce n'est qu'une économie d'appels : l'hébergeur retenu reste celui
         * de l'URL résolue, jamais celui qu'annonce la page. Sans métadonnée,
         * on résout - mieux vaut un appel de trop qu'un lien perdu.
         */
        internal fun worthResolving(hotes: List<String>?): Boolean {
            if (hotes.isNullOrEmpty()) return true
            return hotes.any { isHosterSupported(hosterOf("https://$it")) }
        }

        /**
         * Le chemin de la fiche qui porte **vraiment** [titre], ou null.
         *
         * ### Le défaut que cette vérification ferme
         *
         * `fetch.php` est une recherche *floue* : elle répond toujours quelque
         * chose. Prendre son premier résultat, comme on le faisait, revenait à
         * servir n'importe quel animé pour n'importe quel titre — et sans le
         * moindre signe, puisqu'un lien valide sort d'un catalogue valide.
         * Relevé le 11/09/2026, sur les titres mêmes de la sonde de santé :
         *
         * | demandé                  | servi                            |
         * |--------------------------|----------------------------------|
         * | Mentalist                | `medalist`                       |
         * | Fight Club               | `rymans-club`                    |
         * | Matrix                   | `ill-be-the-matriarch-in-this-life` |
         * | Grey's Anatomy           | `sk8rs`                          |
         * | Intouchables             | `anne-shirley`                   |
         * | New York Unité Spéciale  | `new-game`                       |
         *
         * L'utilisateur lançait un épisode de série et tombait sur un animé sans
         * rapport. Les films étaient largement épargnés par accident : leur
         * chemin est `film/`, que la plupart des séries animées n'ont pas, donc
         * le garde-fou de saison rejetait la fiche après coup. Les épisodes, eux,
         * trouvaient presque toujours une `saison1/`.
         *
         * ### La règle, et pourquoi celle-là
         *
         * Égalité **exacte** entre le titre demandé et l'un des noms que la
         * fiche déclare — son titre, ou l'un de ses titres alternatifs
         * (`asn-search-result-subtitle`, qui liste le romaji et le titre
         * français). Mesurée sur quatorze titres, elle garde les sept animés
         * légitimes et rejette les sept intrus.
         *
         * On balaie **tous** les résultats, pas seulement le premier : pour
         * « L'Attaque des Titans », la bonne fiche est la deuxième
         * (`shingeki-no-kyojin`, dont les alternatives portent ce titre exact),
         * la première étant le spin-off « Junior High School ».
         *
         * Le rapprochement approximatif est délibérément écarté : « Mentalist »
         * et « Medalist » ne diffèrent que d'une lettre. En cas de doute on ne
         * rend rien — l'absence de lien se voit et se contourne, un mauvais film
         * se regarde.
         */
        internal fun cataloguePath(html: String, titre: String): String? {
            val vise = normalise(titre)
            if (vise.isEmpty()) return null
            return RESULTAT.findAll(html)
                .firstOrNull { bloc -> nomsDe(bloc.value).any { normalise(it) == vise } }
                ?.groupValues?.get(1)
        }

        /** Titre et titres alternatifs déclarés par une fiche de résultat. */
        private fun nomsDe(bloc: String): List<String> = buildList {
            TITRE.find(bloc)?.groupValues?.get(1)?.let { add(it) }
            // Les alternatives sont listées séparées par des virgules.
            SOUS_TITRE.find(bloc)?.groupValues?.get(1)?.split(',')?.forEach { add(it) }
        }

        /**
         * Forme comparable d'un titre : sans balise, sans entité HTML, sans
         * accent ni ponctuation.
         *
         * Les entités sont **retirées avant** la ponctuation, et non après : sur
         * `L&#039;Attaque`, l'ordre inverse laisserait les chiffres de `&#039;`
         * et produirait `l039attaque`, qui ne vaut plus `lattaque`. Le titre venu
         * de TMDB, lui, n'a jamais d'entité — les deux côtés se rejoignent donc
         * bien sur la même forme.
         */
        internal fun normalise(brut: String): String {
            val sansBalise = Regex("<[^>]*>").replace(brut, " ")
            val sansEntite = Regex("&(?:#\\d+|#x[0-9a-fA-F]+|[a-zA-Z]+);").replace(sansBalise, "")
            return buildString {
                for (c in sansEntite.lowercase()) {
                    val plie = PLIAGE[c] ?: c
                    if (plie.isLetterOrDigit()) append(plie)
                }
            }
        }

        /**
         * Repli des diacritiques latins. `java.text.Normalizer` n'existe pas en
         * commun ; la table couvre ce que portent les titres français et les
         * translittérations romaji (« Shippûden »).
         */
        private val PLIAGE: Map<Char, Char> = buildMap {
            fun ranger(source: String, cible: Char) = source.forEach { put(it, cible) }
            ranger("àáâãäåāăą", 'a'); ranger("çćĉċč", 'c'); ranger("èéêëēĕėęě", 'e')
            ranger("ìíîïĩīĭįı", 'i'); ranger("ñńņňŉ", 'n'); ranger("òóôõöøōŏő", 'o')
            ranger("ùúûüũūŭůűų", 'u'); ranger("ýÿŷ", 'y'); ranger("śŝşš", 's'); ranger("ţťŧ", 't')
        }

        /**
         * Un résultat entier — le lien **et** ce qu'il annonce. On capture le
         * bloc complet parce que la décision se prend sur le titre, pas sur
         * l'ordre d'apparition.
         *
         * Le domaine est **facultatif** : anime-sama rendait des liens relatifs
         * (`/catalogue/…`) et rend désormais des liens absolus
         * (`https://anime-sama.to/catalogue/…`). N'accepter que la première
         * forme ne faisait pas échouer le provider — il rendait une liste vide,
         * ce qui se confond avec « ce titre n'est pas au catalogue ». Le seul
         * catalogue spécialisé de l'application ne fournissait donc plus **aucun
         * lien, sur aucun animé**, sans que rien ne le signale ; c'est une sonde
         * de couverture qui l'a montré, pas un test ni un rapport d'erreur.
         *
         * On ne capture que le chemin, pour que la reconstruction avec [BASE]
         * reste vraie quel que soit le domaine du jour.
         */
        private val RESULTAT =
            Regex("""<a[^>]+href="(?:https?://[^"/]+)?(/catalogue/[^"]+)"[\s\S]*?</a>""")
        private val TITRE = Regex("""<h3[^>]*>([\s\S]*?)</h3>""")
        private val SOUS_TITRE =
            Regex("""<p[^>]*asn-search-result-subtitle[^>]*>([\s\S]*?)</p>""")
        /**
         * Les chemins de langue à sonder, et l'étiquette qu'ils portent.
         *
         * La VF d'abord : c'est ce que l'application privilégie, et l'ordre
         * décide de ce qui est proposé en premier à qualité égale.
         *
         * `vf1` et `vf2` viennent du portage de Movix, qui connaît neuf
         * identifiants de langue. Ils rendent 404 sur tout ce qu'on a mesuré,
         * mais ils ne coûtent qu'un aller-retour sur un fichier de quelques
         * kilo-octets — et un doublage qui existe sous ce nom vaut mieux qu'une
         * requête épargnée. Les langues non francophones (`vosteng`, `vj`…) sont
         * volontairement laissées de côté : rien dans l'application ne sait quoi
         * en faire.
         */
        private val LANGS = listOf(
            "vf" to "VF",
            "vf1" to "VF",
            "vf2" to "VF",
            "vostfr" to "VOSTFR",
        )
        private val PANEL = Regex("""panneauAnime\("([^"]+)",\s*"([^"]+)"\)""")
        /** `var eps1 = [...]` ; `epsMeta` (un objet) et `epsJeton` (une chaîne) n'y répondent pas. */
        private val EPS = Regex("""var\s+eps\w+\s*=\s*\[([\s\S]*?)\]""")
        /** Une chaîne JS, entre apostrophes (ancien format) ou guillemets (nouveau). */
        private val CHAINE = Regex("""'([^']*)'|"([^"]*)"""")
        /** `epsMeta` ne contient que des tableaux : la première accolade suivie de `;` le ferme. */
        private val META = Regex("""var\s+epsMeta\s*=\s*(\{[\s\S]*?\})\s*;""")
        /** Restreint au jeu de caractères mesuré : le jeton part tel quel dans l'URL. */
        private val JETON = Regex("""var\s+epsJeton\s*=\s*["']([A-Za-z0-9._-]+)["']""")
        private val LIEN = Regex("""lien:(\d+)""")
    }
}
