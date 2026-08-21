package fr.moovie.tv.data.cast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ce qu'on diffuse, et vers quoi. Null quand rien ne part d'ici. */
data class CastPlayback(
    val device: CastDevice,
    val title: String,
    val subtitle: String = "",
    val artwork: String = "",
    /** Clé de lecture, pour recopier la progression dans le magasin local. */
    val mediaKey: String = "",
)

/**
 * La diffusion Chromecast en cours, vue de partout.
 *
 * ## Pourquoi un objet global
 *
 * Trois parties doivent voir la même session sans se connaître : l'écran qui la
 * lance, celui qui la pilote, et le service en avant-plan qui la maintient en
 * vie. C'est le même motif que [fr.moovie.tv.data.remote.RemoteNowPlaying], et
 * pour la même raison — un service et une composition n'ont aucun moyen de se
 * tenir par la main.
 *
 * ## Une seule à la fois
 *
 * Deux diffusions simultanées voudraient dire deux relais, deux connexions et
 * deux notifications, pour un téléphone qui n'a qu'un écran. [start] remplace
 * donc la précédente en la fermant — sans quoi le premier relais survivrait,
 * invisible, à servir des octets que plus personne ne demande.
 */
object CastNow {

    private val _playback = MutableStateFlow<CastPlayback?>(null)

    /** Ce qui est diffusé, ou null. */
    val playback: StateFlow<CastPlayback?> = _playback.asStateFlow()

    @Volatile
    private var courante: CastSession? = null

    /** La session vivante, pour qui doit lui parler. */
    val session: CastSession? get() = courante

    fun start(session: CastSession, playback: CastPlayback) {
        // La précédente d'abord : voir la note de l'objet.
        courante?.stop()
        courante = session
        _playback.value = playback
    }

    /**
     * Coupe tout.
     *
     * Ne demande **pas** au récepteur d'arrêter : l'appelant décide s'il rend
     * l'écran (l'utilisateur ferme la diffusion) ou s'il lâche simplement la
     * session (l'application s'arrête, et laisser le film finir vaut mieux que
     * de l'interrompre). Voir [CastSession.stopPlayback] pour le premier cas.
     */
    fun clear() {
        courante?.stop()
        courante = null
        _playback.value = null
    }
}
