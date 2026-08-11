package fr.moovie.tv.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import fr.moovie.tv.core.sources.model.PlayableStream
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import java.util.concurrent.Executors

/**
 * Aperçu muet de la bande-annonce dans le fond de la fiche, sur desktop.
 *
 * Même intention que son pendant Android (muet, recadré, hors du chemin du
 * focus), mais un moteur différent : libVLC rend ses images dans un tampon que
 * Compose dessine, via [ComposeVideoSurface] — la même classe que le lecteur
 * plein écran, extraite pour l'occasion plutôt que recopiée. Ses deux invariants
 * de thread-safety sont ce qui sépare une lecture d'un SIGSEGV, et deux copies
 * auraient divergé.
 *
 * ## Tous les appels natifs sur un fil à nous
 *
 * Première version : lecteur créé dans un `remember` (donc sur le thread UI),
 * lancé sur `Dispatchers.IO`, libéré sur un `Thread` jeté là. L'application est
 * tombée en **SIGABRT**, sur une assertion de la glibc :
 *
 * ```
 * __pthread_tpp_change_priority: assertion « new_prio == -1 || … » failed
 * ```
 *
 * libVLC ajuste la priorité des threads qu'il touche ; le faire depuis des
 * threads dont il ne maîtrise pas la politique d'ordonnancement — le thread AWT,
 * un fil du pool d'entrées-sorties partagé — fait avorter le processus entier.
 * D'où un `Executor` à **un seul fil**, non démon quant à l'ordre : création,
 * lecture, arrêt et libération y passent tous, dans cet ordre. C'est exactement
 * la discipline de `VlcjPlayerController`, pour la même raison.
 */
@Composable
fun TrailerPreview(stream: PlayableStream, modifier: Modifier = Modifier) {
    val factory = vlcFactory ?: return
    val surface = remember { ComposeVideoSurface() }

    // Un fil par aperçu, créé avec le composable et arrêté avec lui. Un seul :
    // l'ordre des commandes compte (on ne libère pas un lecteur avant de
    // l'avoir arrêté).
    val vlc = remember {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "moovie-trailer-preview").apply { isDaemon = true }
        }
    }

    DisposableEffect(stream.url) {
        // Le lecteur naît et meurt avec le flux, sur le fil dédié. `@Volatile`
        // n'est pas nécessaire : seul ce fil y touche.
        var player: MediaPlayer? = null

        vlc.execute {
            runCatching {
                val p = factory.mediaPlayers().newEmbeddedMediaPlayer().apply {
                    videoSurface().set(
                        CallbackVideoSurface(
                            surface.bufferFormatCallback,
                            surface.renderCallback,
                            true,
                            VideoSurfaceAdapters.getVideoSurfaceAdapter(),
                        ),
                    )
                }
                player = p
                // Muet **avant** de lancer : régler le volume une fois la
                // lecture partie laisse passer une fraction de seconde de son,
                // ce qui est précisément ce qu'on cherche à éviter.
                p.audio().isMute = true
                // libVLC ne transmet pas ses en-têtes aux requêtes de segments
                // (voir LocalStreamProxy), mais googlevideo ne réclame pas de
                // Referer : seul le User-Agent compte, et `:http-user-agent`
                // est honoré sur l'accès principal comme sur les segments.
                val options = stream.headers["User-Agent"]
                    ?.let { arrayOf(":http-user-agent=$it") }
                    ?: emptyArray()
                p.media().play(stream.url, *options)
            }
        }

        onDispose {
            vlc.execute {
                player?.let {
                    runCatching { it.controls().stop() }
                    runCatching { it.release() }
                }
                player = null
            }
            // Après la libération, plus rien à exécuter : `shutdown` (et non
            // `shutdownNow`) laisse l'arrêt et la libération se terminer.
            runCatching { vlc.shutdown() }
        }
    }

    Box(modifier) {
        // `frameTick` lu ici pour que la recomposition suive la production
        // d'images : sans lui, Compose ne voit qu'un `ImageBitmap` qui change
        // d'identité et redessine de façon erratique.
        @Suppress("UNUSED_EXPRESSION")
        surface.frameTick
        surface.image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                // Recadré comme l'affiche qu'il remplace : des bandes noires
                // trahiraient une vidéo posée là au lieu d'un décor.
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
