package fr.moovie.tv.desktop.mpv

import fr.moovie.tv.ui.player.MooviePlayerController
import fr.moovie.tv.ui.player.PlayerTrack
import fr.moovie.tv.ui.player.PlayerTracks

/**
 * [MooviePlayerController] adossé au moteur mpv.
 *
 * ## Ce que ce contrôleur n'a plus à faire
 *
 * Son prédécesseur vlcj passait l'essentiel de sa longueur à rattraper libVLC :
 * un exécutif à un fil parce qu'appeler libVLC du fil d'interface gelait la
 * fenêtre, une compensation de saut apprise parce qu'un seek HLS atterrissait
 * sept à dix secondes avant la cible, une position « en vol » tenue à la main
 * parce que libVLC rendait la position demandée au lieu de la sienne.
 *
 * mpv règle les trois : ses appels sont une file de commandes sans verrou
 * partagé avec l'appelant, son seek `exact` atterrit sur la trame demandée, et
 * la position en vol vit dans le moteur, qui sait quand elle cesse d'être
 * vraie.
 *
 * ## Identifiants de piste
 *
 * L'identifiant exposé à la chrome est l'`id` mpv de la piste, en texte.
 * L'interface ne l'interprète jamais : elle le repasse tel quel.
 */
internal class MpvPlayerController(private val moteur: MpvEngine) : MooviePlayerController {

    override val isPlaying: Boolean get() = moteur.enLecture

    override fun positionMs(): Long = moteur.positionMs()

    override fun durationMs(): Long = moteur.dureeMs()

    override fun bufferedMs(): Long = moteur.tamponMs()

    override fun togglePause() = moteur.bascule()

    override fun pause() = moteur.pause(true)

    override fun seekTo(positionMs: Long) = moteur.seek(positionMs)

    override fun seekBy(deltaMs: Long) {
        val duree = moteur.dureeMs()
        val maximum = if (duree > 0) duree else Long.MAX_VALUE
        // Calculé sur la position du moteur, qui pendant un saut rend la cible :
        // deux appuis rapprochés avancent bien de deux pas.
        moteur.seek((moteur.positionMs() + deltaMs).coerceIn(0L, maximum))
    }

    override val speed: Float get() = moteur.vitesseCourante

    override fun setSpeed(value: Float) = moteur.regleVitesse(value)

    override fun tracks(): PlayerTracks = PlayerTracks(
        subtitles = moteur.pistes("sub").map { PlayerTrack(it.id.toString(), it.libelle, it.active) },
        audio = moteur.pistes("audio").map { PlayerTrack(it.id.toString(), it.libelle, it.active) },
    )

    override fun selectSubtitle(trackId: String?) =
        moteur.selectionneSousTitre(trackId?.toLongOrNull())

    override fun selectAudio(trackId: String) {
        trackId.toLongOrNull()?.let { moteur.selectionneAudio(it) }
    }

    override fun loadExternalSubtitle(path: String?) = moteur.sousTitreExterne(path)

    override fun videoFps(): Double = moteur.fps()
}
