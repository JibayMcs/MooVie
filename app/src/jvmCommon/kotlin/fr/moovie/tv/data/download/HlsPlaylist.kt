package fr.moovie.tv.data.download

import java.net.URI

/**
 * Une ressource distante et le nom qu'elle portera sur le disque.
 *
 * Les noms sont **plats et numérotés**, jamais dérivés de l'URL : un hébergeur
 * qui sert `index.ts?token=…` ou deux segments de même nom dans des dossiers
 * différents produirait sinon des collisions ou des chemins invalides.
 */
data class HlsResource(val url: String, val localName: String)

/** Une playlist média rendue lisible hors ligne. */
data class LocalPlaylist(
    /** Le m3u8 à écrire à côté des segments. */
    val text: String,
    val resources: List<HlsResource>,
)

/**
 * Lecture et localisation des playlists HLS.
 *
 * Douze extracteurs sur quinze livrent du m3u8 : télécharger, pour Moo-vie,
 * c'est d'abord récupérer des segments et réécrire une playlist qui les
 * désigne **en relatif**. Le dossier obtenu s'ouvre alors tel quel dans
 * ExoPlayer comme dans VLC, sans que ni l'un ni l'autre ne sache qu'il vient
 * d'ailleurs.
 *
 * Tout est pur : pas de réseau, pas de fichier. C'est la partie où une erreur
 * coûte un téléchargement de deux gigaoctets pour rien, donc celle qu'on veut
 * pouvoir éprouver sans rien télécharger.
 */
object HlsPlaylist {

    private val EXT_X_KEY = Regex("""#EXT-X-KEY:[^\n]*""")
    private val EXT_X_MAP = Regex("""#EXT-X-MAP:[^\n]*""")
    private val URI_ATTR = Regex("""URI="([^"]*)"""")
    private val BANDWIDTH = Regex("""BANDWIDTH=(\d+)""")

    /** Vrai si le texte est une playlist de variantes plutôt que de segments. */
    fun isMaster(text: String): Boolean = "#EXT-X-STREAM-INF" in text

    /**
     * Choisit une variante dans une master playlist, et rend son URL absolue.
     *
     * **La meilleure qualité annoncée**, parce qu'un téléchargement se regarde
     * plus tard, souvent sur un plus grand écran, et qu'on ne peut pas revenir
     * la chercher une fois hors ligne. Le débit disponible au moment du
     * téléchargement ne dit rien de celui qu'on aura à la lecture.
     */
    fun pickVariant(text: String, baseUrl: String): String? {
        var best: Pair<Long, String>? = null
        val lines = text.lines()
        lines.forEachIndexed { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF")) return@forEachIndexed
            val bandwidth = BANDWIDTH.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val target = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                ?: return@forEachIndexed
            if (best == null || bandwidth > best!!.first) best = bandwidth to target.trim()
        }
        return best?.second?.let { resolve(baseUrl, it) }
    }

    /**
     * Réécrit une playlist média pour qu'elle désigne des fichiers voisins.
     *
     * Trois sortes de références y renvoient vers l'extérieur, et les oublier
     * donne un dossier qui ne se lit qu'en ligne — c'est-à-dire pas du tout :
     *
     * - les **segments**, les seules lignes sans `#` ;
     * - la **clé** `#EXT-X-KEY`, quand le flux est chiffré en AES-128. Sans
     *   elle les segments sont du bruit ;
     * - le **segment d'initialisation** `#EXT-X-MAP` des flux fMP4, sans lequel
     *   rien ne se décode.
     */
    fun localize(text: String, baseUrl: String): LocalPlaylist {
        val resources = mutableListOf<HlsResource>()
        var segments = 0
        var keys = 0
        var maps = 0

        val rewritten = text.lines().joinToString("\n") { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> raw

                line.startsWith("#EXT-X-KEY") -> replaceUri(line, EXT_X_KEY) { uri ->
                    // Une clé « NONE » ne référence rien : la réécrire
                    // fabriquerait un fichier à télécharger qui n'existe pas.
                    if (uri.isBlank()) return@replaceUri null
                    "key${keys++}.bin".also { resources += HlsResource(resolve(baseUrl, uri), it) }
                }

                line.startsWith("#EXT-X-MAP") -> replaceUri(line, EXT_X_MAP) { uri ->
                    "init${maps++}.mp4".also { resources += HlsResource(resolve(baseUrl, uri), it) }
                }

                line.startsWith("#") -> raw

                else -> {
                    val name = "seg%05d%s".format(segments++, extensionOf(line))
                    resources += HlsResource(resolve(baseUrl, line), name)
                    name
                }
            }
        }
        return LocalPlaylist(text = rewritten, resources = resources)
    }

    private fun replaceUri(line: String, tag: Regex, rename: (String) -> String?): String {
        val found = URI_ATTR.find(line) ?: return line
        val local = rename(found.groupValues[1]) ?: return line
        return line.replaceRange(found.range, """URI="$local"""")
    }

    /**
     * Conserve l'extension d'origine : ExoPlayer et VLC se fient au conteneur,
     * et un `.ts` renommé en `.bin` se lit moins bien qu'il ne le devrait.
     */
    private fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val dot = path.substringAfterLast('/').lastIndexOf('.')
        if (dot < 0) return ".ts"
        val ext = path.substringAfterLast('/').substring(dot)
        return if (ext.length in 2..5) ext else ".ts"
    }

    /**
     * Résout une référence relative contre la playlist qui la porte.
     *
     * `URI.resolve` échoue sur les caractères qu'un hébergeur laisse passer
     * sans les encoder ; on rend alors la référence telle quelle plutôt que de
     * faire tomber tout le téléchargement pour un segment mal formé.
     */
    private fun resolve(baseUrl: String, reference: String): String =
        runCatching { URI(baseUrl).resolve(reference).toString() }.getOrElse { reference }
}
