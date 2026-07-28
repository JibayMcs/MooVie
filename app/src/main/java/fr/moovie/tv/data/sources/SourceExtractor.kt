package fr.moovie.tv.data.sources

/** Type de flux extrait — détermine le pipeline ExoPlayer à utiliser. */
enum class StreamFormat { HLS, DASH, MP4, UNKNOWN }

/**
 * Un flux jouable résolu par un extracteur.
 *
 * @param url URL directe du manifeste/fichier (m3u8 / mpd / mp4).
 * @param headers en-têtes HTTP requis pour lire (Referer, Origin, User-Agent…).
 *        En natif on les passe directement à OkHttp — plus besoin de proxy CORS.
 * @param language piste par défaut (VF / VOSTFR / VO), pour préselection ExoPlayer.
 */
data class PlayableStream(
    val url: String,
    val format: StreamFormat,
    val headers: Map<String, String> = emptyMap(),
    val language: String? = null,
    val quality: String? = null,
    val subtitleUrls: Map<String, String> = emptyMap(),
)

/** Métadonnées d'un embed à résoudre (le lien d'hébergeur trouvé par un provider). */
data class EmbedLink(
    val url: String,
    val hoster: String,
    val language: String? = null,
)

/**
 * Un extracteur transforme un lien d'embed d'hébergeur (voe, uqload, doodstream…)
 * en flux jouable. C'est le portage Kotlin des handlers Python/Node actuels
 * (API/proxiesembed/server.py, API/Mainapi/routes). Un extracteur par hébergeur.
 */
interface SourceExtractor {
    /** Identifiant d'hébergeur, ex: "voe", "uqload". */
    val hoster: String

    /** true si cet extracteur sait traiter cette URL d'embed. */
    fun canHandle(url: String): Boolean

    /** Résout le flux jouable, ou null si échec (source morte, format changé…). */
    suspend fun extract(link: EmbedLink): PlayableStream?
}
