package fr.moovie.tv.data.cast

import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.subtitles.usecase.srtToVtt
import fr.moovie.tv.data.net.LocalStreamProxy
import kotlinx.coroutines.flow.StateFlow

/**
 * Une diffusion vers un Chromecast, du flux résolu jusqu'à l'image.
 *
 * ## Ce qui change par rapport à la diffusion entre deux Moo-vie
 *
 * C'est la différence de fond, et elle se voit à l'usage. Vers un téléviseur qui
 * fait tourner Moo-vie, on envoie **l'intention** — un identifiant TMDB — et
 * c'est lui qui résout, avec ses propres extracteurs et sa propre connexion.
 *
 * Un Chromecast ne sait rien résoudre. Il lit une URL, point. Donc :
 *
 * - **le téléphone résout**, avec la cascade complète (30 s à froid, mesuré) ;
 * - **le téléphone relaie**, parce que le récepteur n'enverra ni `Referer` ni
 *   `User-Agent`, et que beaucoup de CDN répondent 403 sans eux ;
 * - **le téléphone doit rester là**. Tant que le film joue, c'est lui qui sert
 *   les octets. Fermer l'application, quitter le Wi-Fi ou laisser le système
 *   tuer le processus arrête la lecture.
 *
 * Ce dernier point n'est pas un défaut à corriger : c'est la contrepartie
 * assumée de n'avoir ni backend ni récepteur à nous. Il justifie en revanche de
 * tenir la session dans un service en avant-plan, comme la diffusion entre
 * applications le fait déjà.
 *
 * ## L'ordre des opérations
 *
 * Le relais **avant** la connexion : l'URL locale doit exister pour être
 * envoyée, et son adresse dépend de l'interface qui joint le récepteur — d'où
 * [CastDevice.host] passé au relais.
 */
class CastSession(private val device: CastDevice) {

    private var relais: LocalStreamProxy? = null
    private val client = CastClient(device.host)

    val status: StateFlow<CastStatus> get() = client.status
    val connecte: StateFlow<Boolean> get() = client.connecte

    /** Le son de l'appareil, qu'on le règle d'ici ou avec la télécommande de la télé. */
    val volume: StateFlow<CastVolume> get() = client.volume

    /**
     * Ouvre la session et charge le flux. Rend faux si le récepteur n'a pas pris.
     *
     * @param stream le flux **déjà résolu**. Ses en-têtes deviennent ceux du
     *   relais : c'est tout ce qui sépare un 403 d'une lecture.
     */
    suspend fun start(
        stream: PlayableStream,
        title: String,
        subtitle: String = "",
        artwork: String = "",
        positionMs: Long = 0,
        /**
         * Un fichier `.srt` local à envoyer comme piste de sous-titres.
         *
         * Converti en WebVTT et servi par le relais : le récepteur ne lit que ce
         * format, et va le chercher lui-même — d'où le CORS, que le relais pose
         * déjà en mode réseau. Null = pas de sous-titres, comme avant.
         */
        sousTitres: java.io.File? = null,
        langueSousTitres: String = "fr",
    ): Boolean {
        stop()

        val proxy = LocalStreamProxy(
            headers = stream.headers,
            ouvertAuReseau = true,
            versHote = device.host,
        )
        relais = proxy

        if (!client.connect()) {
            stop()
            return false
        }

        // Les sous-titres avant le LOAD : leur URL doit exister pour y figurer,
        // et une piste ajoutée après coup demanderait un EDIT_TRACKS_INFO que
        // rien ici ne déclenche.
        val piste = sousTitres
            ?.takeIf { it.isFile }
            ?.let { fichier ->
                runCatching {
                    CastPisteTexte(
                        url = proxy.serviTexte(
                            srtToVtt(fichier.readText()),
                            "text/vtt; charset=utf-8",
                        ),
                        langue = langueSousTitres,
                    )
                }.getOrNull()
            }

        val charge = client.load(
            url = proxy.localUrl(stream.url),
            // **Le type vient du flux, pas de l'URL relayée.** Celle-ci finit
            // par du base64 : la déduction par extension retombait sur MP4 et le
            // récepteur refusait tout HLS. Voir castContentType.
            contentType = castContentType(stream.format, stream.url),
            title = title,
            subtitle = subtitle,
            artwork = artwork,
            positionMs = positionMs,
            sousTitres = piste,
        )
        if (!charge) stop()
        return charge
    }

    suspend fun playPause() = client.playPause()

    suspend fun seek(positionMs: Long) = client.seek(positionMs)

    suspend fun setVolume(level: Double) = client.setVolume(level)

    suspend fun setMuted(muted: Boolean) = client.setMuted(muted)

    /**
     * Arrête la lecture **et** coupe le relais.
     *
     * Dans cet ordre : couper le relais d'abord laisserait le récepteur réclamer
     * des segments qui ne viennent plus, et afficher une erreur de lecture au
     * lieu de rendre la main proprement.
     */
    suspend fun stopPlayback() {
        client.stop()
        stop()
    }

    /** Ferme tout, sans rien demander au récepteur. */
    fun stop() {
        runCatching { relais?.shutdown() }
        relais = null
        runCatching { client.close() }
    }
}
