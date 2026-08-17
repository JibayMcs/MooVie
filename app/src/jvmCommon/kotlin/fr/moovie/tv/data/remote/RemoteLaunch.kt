package fr.moovie.tv.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable

/**
 * Un titre que le téléphone demande au téléviseur de lire.
 *
 * ## Ce qui circule, et ce qui ne circule pas
 *
 * **L'intention, jamais le flux.** Le téléphone envoie un identifiant TMDB, pas
 * une URL d'hébergeur : c'est le téléviseur qui résout la source, avec son propre
 * DoH, ses propres extracteurs et son propre ordre de catalogues. Aucun octet de
 * vidéo ne traverse le téléphone.
 *
 * C'est précisément ce qui rend cette fonctionnalité possible là où le Chromecast
 * bute : nos flux exigent des en-têtes `Referer` et `User-Agent` qu'un récepteur
 * tiers n'enverrait pas, et il faudrait relayer le média pour les poser. Entre
 * deux Moo-vie, la question ne se pose pas — le lecteur qui télécharge est celui
 * qui a extrait le lien.
 *
 * Corollaire assumé : la source jouée sur le téléviseur **peut différer** de
 * celle que le téléphone affichait. C'est voulu. Les deux appareils n'ont ni la
 * même connexion, ni forcément les mêmes catalogues actifs, et le téléviseur est
 * mieux placé que le téléphone pour choisir ce qu'il saura lire.
 *
 * @param title titre affiché pendant la résolution. Purement décoratif — le
 *   téléviseur le connaît par TMDB — mais il évite un écran d'attente muet
 *   pendant les quelques secondes de cascade.
 * @param artwork affiche, URL absolue TMDB. Voir [NowPlaying] : c'est le
 *   téléviseur qui la télécharge, pas le serveur qui la relaie.
 * @param positionMs où en est **le téléphone**, pour reprendre là plutôt qu'au
 *   début. Sans lui, « continuer sur la TV » ne continue rien : le lecteur lit
 *   la reprise dans le magasin de l'appareil qui joue, et le téléviseur n'a
 *   aucune raison de connaître un épisode commencé ailleurs. La synchro B2 le
 *   ferait, mais elle est facultative et différée — or ce geste-ci veut dire
 *   « maintenant, là où j'en suis ».
 * @param durationMs durée connue du téléphone, enregistrée avec la position :
 *   une reprise sans durée n'a pas de quoi calculer ce qu'il reste.
 */
@Serializable
data class PlayRequest(
    val tmdbId: Int,
    val isTv: Boolean,
    val season: Int = 0,
    val episode: Int = 0,
    val title: String = "",
    val subtitle: String = "",
    val artwork: String = "",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /**
     * Le téléviseur a-t-il le droit d'enregistrer ce qu'il diffuse ?
     *
     * Faux quand les deux appareils n'écrivent pas au même endroit — comptes de
     * synchro différents, phrases secrètes différentes, ou simplement aucune
     * synchro des deux côtés. Le téléviseur n'est alors qu'un **écran** : y
     * laisser une progression et une ligne d'historique polluerait un compte qui
     * n'a rien demandé, et l'utilisateur retrouverait sur sa box des titres
     * qu'il n'y a jamais regardés.
     *
     * Vrai **seulement si on peut le prouver** : deux empreintes non vides et
     * identiques. L'absence de synchro des deux côtés ne prouve rien — elle
     * garantit au contraire que rien ne réconciliera jamais — donc elle vaut
     * faux. Voir [fr.moovie.tv.data.sync.SyncSettingsRepository.syncFingerprint].
     *
     * Par défaut vrai, pour qu'un téléphone d'avant cette version — qui n'envoie
     * pas le champ — garde le comportement qu'il avait.
     */
    val record: Boolean = true,
)

/**
 * Les demandes de lecture reçues du téléphone, vues de la composition.
 *
 * Même motif que [RemoteNowPlaying] et [RemoteTyping], et pour la même raison :
 * le serveur répond sur un fil de socket, la navigation vit dans la composition,
 * et les deux n'ont aucun moyen de se tenir par la main.
 *
 * Un **flux d'événements** et non un état : une demande se consomme une fois. Un
 * `StateFlow` rejouerait la dernière à chaque recomposition, et quitter le
 * lecteur relancerait aussitôt le titre qu'on vient d'arrêter.
 *
 * `extraBufferCapacity` pour que l'émission depuis le fil de socket ne bloque
 * jamais : le serveur doit répondre au téléphone en quelques millisecondes,
 * indépendamment de ce que la composition fait de la demande.
 */
object RemoteLaunch {

    private val _requests = MutableSharedFlow<PlayRequest>(extraBufferCapacity = 4)

    val requests: SharedFlow<PlayRequest> = _requests.asSharedFlow()

    /**
     * Vrai si la demande a été acceptée pour traitement.
     *
     * Faux signifie que personne n'écoute — l'application n'a pas fini de
     * démarrer, typiquement. Le serveur le répercute en 409 plutôt qu'en 204 :
     * le téléphone doit pouvoir dire « la TV n'a pas pris » au lieu de basculer
     * sur une télécommande qui ne montrera rien.
     */
    fun request(play: PlayRequest): Boolean = _requests.tryEmit(play)
}
