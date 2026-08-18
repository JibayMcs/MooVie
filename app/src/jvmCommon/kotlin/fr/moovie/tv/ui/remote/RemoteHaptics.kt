package fr.moovie.tv.ui.remote

import androidx.compose.runtime.Composable
import fr.moovie.tv.data.remote.RemoteKey

/**
 * Les trois intensités du retour haptique de la télécommande.
 *
 * Au niveau supérieur et non imbriquée dans [RemoteHaptics] : un `expect object`
 * ne peut pas porter de type imbriqué, et l'énumération n'a de toute façon rien
 * de spécifique à une plateforme — c'est le vocabulaire du geste, pas son
 * exécution.
 *
 * Trois et non une, parce qu'une seule ne dit rien. Le doigt ne regarde pas
 * l'écran : un cran sec quand la direction change, une frappe plus franche sur
 * OK, un motif à deux temps sur Retour. C'est ce qui permet de sentir *ce qu'on
 * vient de faire* sans lever les yeux.
 */
enum class HapticTick { STEP, PRESS, BACK }

/**
 * Retour haptique de la télécommande, quand la plateforme en a un.
 *
 * La télécommande a d'abord été une page web, et sa vibration n'a jamais
 * fonctionné : l'API du navigateur ne demande aucune autorisation, elle est
 * simplement inopérante sur Chrome Android selon les réglages, et absente sur
 * iOS. Aucun correctif n'était possible côté page — c'était le mauvais support.
 *
 * Un poste de travail n'a pas de vibreur, et n'en aura pas : son implémentation
 * est vide, et [available] le dit franchement plutôt que de laisser croire à un
 * retour qui n'arrivera jamais.
 */
expect object RemoteHaptics {

    /** Faux quand l'appareil n'a pas de vibreur : l'écran le dit plutôt que de laisser douter. */
    val available: Boolean

    fun tick(kind: HapticTick)
}

/**
 * Détourne les touches physiques de volume vers le téléviseur, tant que l'écran
 * de télécommande est affiché.
 *
 * C'est le geste qu'on fait sans y penser en tenant une télécommande, et la
 * seule chose qui manquait pour que celle-ci se substitue vraiment à la vraie :
 * régler le son en gardant les yeux sur l'écran. Google Cast détourne les mêmes
 * touches, de la même façon.
 *
 * **Seul le point de capture est partagé.** Le traitement de l'événement reste
 * côté Android : `dispatchKeyEvent` est une affaire d'`Activity`, et un poste de
 * travail n'a pas de touches de volume dont l'application puisse s'emparer — ce
 * qu'un clavier en a appartient au système, hors de portée d'une JVM.
 */
@Composable
expect fun CaptureVolumeKeys(onKey: (RemoteKey) -> Unit)

/**
 * Horloge monotone, en millisecondes.
 *
 * Remplace `SystemClock.uptimeMillis()`, qui n'existe que sur Android. Ce qu'on
 * en attend est la **monotonie**, pas une date : elle sert à décider si un
 * déplacement demandé est encore trop frais pour croire ce que le téléviseur
 * raconte. `currentTimeMillis` ferait l'affaire jusqu'au jour où l'horloge
 * système recule — un fuseau, un NTP — et où la fenêtre de confiance durerait
 * alors des heures.
 */
fun monotonicMs(): Long = System.nanoTime() / 1_000_000
