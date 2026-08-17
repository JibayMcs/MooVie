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
 *
 * [mediaKey] est ce qui rend la progression **réversible**. Sans lui le
 * téléphone reçoit une position sans savoir de quoi : il peut l'afficher, pas
 * l'enregistrer. Or c'est le magasin du téléphone qui fait foi dès que les deux
 * appareils ne partagent pas le même compte de synchronisation — chacun a alors
 * le sien, et rien ne les réconcilie jamais.
 */
@Serializable
data class NowPlaying(
    val title: String = "",
    val subtitle: String = "",
    val artwork: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    /** `movie:123` ou `tv:123:s2e6`. Vide sur un téléviseur d'avant cette version. */
    val mediaKey: String = "",
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
     * Le dernier relevé, **conservé après la fin de la lecture**.
     *
     * ## Ce que ça règle
     *
     * La progression ne remontait vers le téléphone que tant que son écran de
     * télécommande restait ouvert. Fermer l'application pendant que la box
     * continue laissait le téléphone en arrière, et avec deux comptes de
     * synchronisation distincts, rien ne rattrapait jamais l'écart.
     *
     * Le téléviseur garde donc ce qu'il a joué en dernier et le publie dans son
     * relevé d'état. Le téléphone le récupère à **n'importe quelle** sonde — au
     * lancement suivant, à l'ouverture de la télécommande — sans qu'il ait fallu
     * un service en arrière-plan sur le téléphone ni un serveur qu'il n'a pas.
     *
     * ## Ce que ça ne couvre pas
     *
     * Un seul titre. Si la box en enchaîne deux pendant que le téléphone est
     * absent, seul le dernier est récupéré : le premier est perdu pour lui. Le
     * cas est rare et le prix d'un historique complet serait une file à
     * maintenir des deux côtés, pour une information dont personne ne s'aperçoit
     * qu'elle manque.
     */
    @Volatile
    var last: NowPlaying? = null
        private set

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
        // Retenu même après l'arrêt : c'est tout l'objet de [last].
        if (now.mediaKey.isNotBlank()) last = now
    }

    /** Le lecteur s'annonce joignable, et dit par quel chemin. */
    fun attachSeek(seek: (Long) -> Unit) {
        seeker = seek
    }

    /** Fin de la lecture : plus rien à raconter, et plus rien à déplacer. */
    fun clear() {
        _state.value = null
        seeker = null
        // `last` survit volontairement : « rien ne joue » n'efface pas « voilà
        // où en était la dernière chose jouée ».
    }

    /** Déplace la lecture. Faux si aucun lecteur n'est là pour obéir. */
    fun seek(positionMs: Long): Boolean {
        val seek = seeker ?: return false
        seek(positionMs.coerceAtLeast(0))
        return true
    }
}
