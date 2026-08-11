package fr.moovie.tv.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * Ce que le téléviseur est en train de lire, tel qu'il le raconte au téléphone.
 *
 * Sérialisable et **partagé par les deux bouts** : le même type est écrit par le
 * téléviseur et lu par le téléphone. Un modèle recopié de chaque côté aurait
 * divergé au premier champ ajouté, et le symptôme aurait été une jaquette qui ne
 * s'affiche plus sans que rien ne casse.
 *
 * [artwork] est une **URL absolue** (TMDB), pas une image. Le téléphone a déjà
 * son accès réseau et son cache Coil : lui faire télécharger la vignette
 * lui-même évite de faire transiter des octets par le serveur du téléviseur, qui
 * n'est pas fait pour ça.
 *
 * Les valeurs par défaut existent pour la compatibilité : un téléviseur d'une
 * version antérieure omettra les champs qu'il ne connaît pas, et le téléphone
 * doit lire ce qu'il reçoit plutôt que d'échouer sur ce qui manque.
 */
@Serializable
data class NowPlaying(
    val title: String = "",
    val subtitle: String = "",
    val artwork: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
)

/**
 * Le lecteur du téléviseur, vu du serveur d'appairage.
 *
 * ### Pourquoi un objet global
 *
 * Le serveur répond sur un fil de socket, le lecteur vit dans la composition :
 * les deux n'ont aucun moyen de se tenir par la main. Le lecteur dépose ici ce
 * qu'il sait, le serveur le lit. C'est le même motif que
 * [fr.moovie.tv.data.remote.remoteTarget], pour la même raison.
 *
 * ### Renseigné seulement pendant la lecture
 *
 * [clear] est appelé en quittant le lecteur : sans cela, le téléphone
 * continuerait d'afficher un mini-lecteur figé sur l'épisode d'hier, ce qui est
 * pire que de n'afficher rien. « Rien en cours » est une réponse, et le serveur
 * la rend telle quelle.
 */
object RemoteNowPlaying {

    private val _state = MutableStateFlow<NowPlaying?>(null)

    /** L'état courant, ou null si rien ne joue. */
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    /**
     * Comment déplacer la lecture, posé par le lecteur tant qu'il est à l'écran.
     *
     * Une lambda plutôt qu'une référence au contrôleur : le fil de socket n'a
     * pas le droit de toucher au lecteur, et c'est au lecteur — qui connaît son
     * fil principal — de dire comment on lui parle. `@Volatile` parce que
     * l'écriture et la lecture n'arrivent pas du même fil.
     */
    @Volatile
    private var seeker: ((Long) -> Unit)? = null

    fun publish(now: NowPlaying) {
        _state.value = now
    }

    /** Le lecteur s'annonce joignable, et dit par quel chemin. */
    fun attachSeek(seek: (Long) -> Unit) {
        seeker = seek
    }

    /** Fin de la lecture : plus rien à raconter, et plus rien à déplacer. */
    fun clear() {
        _state.value = null
        seeker = null
    }

    /** Déplace la lecture. Faux si aucun lecteur n'est là pour obéir. */
    fun seek(positionMs: Long): Boolean {
        val seek = seeker ?: return false
        seek(positionMs.coerceAtLeast(0))
        return true
    }
}
