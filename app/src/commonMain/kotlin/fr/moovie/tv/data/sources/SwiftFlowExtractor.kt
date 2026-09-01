package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.SourceExtractor

/**
 * Extracteur SwiftFlow — **il n'extrait rien, et c'est tout l'intérêt**.
 *
 * L'URL rendue par [SwiftFlowProvider] est déjà le fichier. Ce qui manque n'est
 * pas une adresse mais un **en-tête** : le CDN est derrière Cloudflare et rend
 * 403 sans `Referer`. Mesuré, sur la même URL :
 *
 * | Requête | Réponse |
 * |---|---|
 * | sans en-tête | 403, page Cloudflare |
 * | avec `Referer` | 200 `video/mp4` |
 * | avec `Referer` + `Range` | 206 `video/mp4` |
 *
 * ### Pourquoi il passe avant `DirectStreamExtractor`
 *
 * Celui-ci revendique toute URL dont l'extension est jouable, `.mp4` compris, et
 * il est **premier** dans le registre — un lien SwiftFlow lui tomberait dessus
 * en premier et repartirait avec le seul `User-Agent`, donc en 403. L'ordre
 * n'est pas une préférence ici, c'est la correction.
 *
 * ### Le `Referer` doit survivre jusqu'au lecteur
 *
 * L'adresse répond 302 vers une variante horodatée ; le lecteur suit la
 * redirection lui-même. Sur Android, ExoPlayer applique les en-têtes à toutes
 * ses requêtes. Sur desktop, libVLC ne les propage pas — c'est le défaut
 * documenté qui a donné naissance à `LocalStreamProxy`, et un MP4 progressif y
 * passe comme un HLS.
 */
class SwiftFlowExtractor : SourceExtractor {

    override val hoster = "swiftflow"

    override fun canHandle(url: String): Boolean = HOST.containsMatchIn(url)

    override suspend fun extract(link: EmbedLink): PlayableStream = PlayableStream(
        url = link.url,
        format = StreamFormat.MP4,
        headers = mapOf(
            // La racine du CDN suffit : Cloudflare vérifie l'origine, pas le
            // chemin. Y mettre l'URL du fichier marcherait aussi, mais lierait
            // l'en-tête à la ressource sans rien y gagner.
            "Referer" to REFERER,
            "User-Agent" to Ua.BROWSER,
        ),
        language = link.language,
    )

    private companion object {
        /**
         * Les domaines connus du CDN — **un filet, plus le chemin principal**.
         *
         * SwiftFlow a migré de `deliciouss` vers `edge-nN.site`, avec un
         * intermédiaire `falzey.lol`. Cette liste ne suivra jamais la prochaine
         * rotation : c'est le routage par nom d'hébergeur
         * ([StreamResolution.extractorNamed][fr.moovie.tv.core.sources.usecase.StreamResolution])
         * qui sélectionne désormais cet extracteur, le lien portant
         * `hoster = "swiftflow"` depuis le catalogue. On garde la liste pour les
         * chemins qui ne passent pas par un lien nommé.
         */
        val HOST = Regex(
            """deliciouss|blinkflux|edge-n\d|falzey""",
            RegexOption.IGNORE_CASE,
        )

        /**
         * Le site du lecteur, **et non l'ancien domaine du CDN**.
         *
         * Mesuré le 24/08/2026 sur `alpa.edge-n2.site`, en suivant les deux
         * redirections :
         *
         * | `Referer` | Type rendu |
         * |---|---|
         * | aucun | `text/html` |
         * | `french.deliciouss.lol` (l'ancien) | `text/html` |
         * | `blinkflux.lol` | `video/mp4` |
         *
         * Le piège tient dans la colonne manquante : **les trois répondent
         * 200**. Le refus ne se voit pas au code HTTP, seulement au type — d'où
         * le contrôle de `isPlayableContentType`, sans lequel la source serait
         * passée pour bonne et aurait ouvert le lecteur sur une page web.
         *
         * Doit rester l'origine de `SwiftFlowProvider.API_BASE` : c'est le site
         * qui sert le lecteur, donc celui que le CDN attend.
         */
        const val REFERER = "https://blinkflux.lol/"
    }
}
