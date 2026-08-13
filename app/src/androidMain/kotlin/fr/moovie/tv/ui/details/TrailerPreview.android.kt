package fr.moovie.tv.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.ui.player.ExoPlayerController
import fr.moovie.tv.ui.player.MooviePlayerController

/**
 * Aperçu muet de la bande-annonce dans le fond de la fiche, sur Android.
 *
 * C'est un ExoPlayer **jetable**, distinct de celui du lecteur : il ne partage
 * ni sa position, ni ses pistes, ni son cycle de vie, et il meurt avec la fiche.
 * Le faire passer par `ExoPlayerController` aurait mêlé un aperçu décoratif à la
 * machinerie qui suit la progression de lecture — celle-là même qui alimente
 * « Reprendre », où deux minutes de promotion n'ont rien à faire.
 *
 * Trois partis pris, tous pour la même raison — c'est un **fond**, pas une
 * lecture :
 *
 * - **muet tant qu'il reste un fond.** Le son ne monte pas tout seul : il faut
 *   avoir ouvert la bande-annonce et demandé le son. Mais alors il monte
 *   vraiment — le volume est **suivi**, pas seulement posé à la création. Il ne
 *   l'était pas, et le bouton de son de la chrome n'avait donc aucun effet sur
 *   Android : le lecteur gardait le volume qu'il avait au premier instant,
 *   c'est-à-dire zéro.
 * - **recadré** (`RESIZE_MODE_ZOOM`), comme l'affiche qu'il remplace : des
 *   bandes noires trahiraient une vidéo posée là, au lieu d'un décor.
 * - **sans contrôles ni focus**. La télécommande doit continuer d'atteindre les
 *   boutons ; un `PlayerView` focusable ajouterait un arrêt invisible au D-pad.
 */
@OptIn(UnstableApi::class)
@Composable
fun TrailerPreview(
    stream: PlayableStream,
    volume: Float,
    onController: (MooviePlayerController?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val player = remember(stream.url) {
        // Les en-têtes ne sont pas décoratifs : googlevideo sert les segments
        // au User-Agent du client qui a obtenu l'URL, et le refuse aux autres.
        val http = DefaultHttpDataSource.Factory().apply {
            val ua = stream.headers["User-Agent"]
            if (!ua.isNullOrBlank()) setUserAgent(ua)
            setDefaultRequestProperties(stream.headers.filterKeys { !it.equals("User-Agent", true) })
        }
        // `DefaultDataSource` et non le seul HTTP : le manifeste est un `file://`
        // écrit dans le cache, ses segments sont en HTTPS. Il faut les deux.
        val sources = DefaultDataSource.Factory(context, http)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(sources))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(stream.url))
                this.volume = volume.coerceIn(0f, 1f)
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                prepare()
            }
    }

    // Le volume **suit** son paramètre. Posé au seul `remember`, il était figé à
    // sa valeur du premier instant — zéro, puisque le son ne monte qu'une fois
    // la bande-annonce ouverte : le bouton de son de la chrome ne produisait
    // alors rien du tout. Le desktop tenait déjà cet effet ; Android l'avait
    // oublié, et le paramètre le laissait croire branché.
    LaunchedEffect(player, volume) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    // Le même lecteur, vu comme un contrôleur : c'est lui que les contrôles de
    // la fiche pilotent. `ExoPlayerController` n'est qu'une façade — aucune
    // machinerie de reprise n'y est branchée, la bande-annonce reste hors de
    // l'historique.
    DisposableEffect(player) {
        onController(ExoPlayerController(player))
        onDispose { onController(null) }
    }

    // Libération liée à l'URL, pas à la seule sortie d'écran : changer de fiche
    // recompose avec un nouveau flux, et sans ça l'ancien lecteur continuerait
    // de télécharger dans le vide.
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        update = { it.player = player },
    )
}
