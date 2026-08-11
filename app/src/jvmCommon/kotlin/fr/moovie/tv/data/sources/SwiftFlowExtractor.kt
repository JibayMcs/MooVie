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
        val HOST = Regex("""deliciouss|blinkflux""", RegexOption.IGNORE_CASE)
        const val REFERER = "https://french.deliciouss.lol/"
    }
}
