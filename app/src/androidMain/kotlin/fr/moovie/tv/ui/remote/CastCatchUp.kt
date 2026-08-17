package fr.moovie.tv.ui.remote

import fr.moovie.tv.data.remote.RemoteClient
import fr.moovie.tv.data.remote.RemoteStatus
import fr.moovie.tv.data.remote.RemoteTargetRepository
import fr.moovie.tv.data.watch.WatchProgressRepository
import kotlinx.coroutines.flow.first

/**
 * Rattrape ce que le téléviseur a joué pendant que ce téléphone était fermé.
 *
 * ## Le trou que ça bouche
 *
 * La progression ne remontait que tant que l'écran de télécommande restait
 * ouvert. On diffusait un épisode, on rangeait le téléphone, la box finissait —
 * et le téléphone en restait à la position du départ. Avec deux comptes de
 * synchronisation distincts, rien ne réconciliait jamais : le rail « Reprendre »
 * du téléphone mentait indéfiniment.
 *
 * ## Pourquoi un rattrapage et pas un relevé permanent
 *
 * Suivre la box en continu demanderait un service en arrière-plan — une
 * notification permanente, de la radio réveillée toutes les secondes, et une
 * batterie qui s'en ressent — pour une information dont on n'a besoin qu'au
 * moment où l'on rouvre l'application. Le téléviseur, lui, retient déjà sa
 * dernière lecture ([fr.moovie.tv.data.remote.RemoteNowPlaying.last]) : il
 * suffit de la lui demander une fois, au lancement.
 *
 * L'appel est silencieux et sans conséquence s'il échoue : pas de téléviseur
 * appairé, box éteinte, réseau absent — on repartira au prochain lancement.
 */
suspend fun catchUpWithTelevision() {
    val target = RemoteTargetRepository().target.first() ?: return
    val state = (RemoteClient(target).status() as? RemoteStatus.Known)?.state ?: return
    val last = state.now ?: state.lastPlayed ?: return

    // `lastWrittenMs = 0` : on ne sait rien de ce que le téléphone a déjà
    // enregistré, et mirrorProgress refuse de toute façon un écart trop faible.
    mirrorProgress(WatchProgressRepository(), last, lastWrittenMs = 0)
}
