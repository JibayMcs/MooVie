package fr.moovie.tv.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
 * - **muet**. Du son qui démarre seul en parcourant un catalogue est une
 *   nuisance, et l'utilisateur n'a rien demandé.
 * - **recadré** (`RESIZE_MODE_ZOOM`), comme l'affiche qu'il remplace : des
 *   bandes noires trahiraient une vidéo posée là, au lieu d'un décor.
 * - **sans contrôles ni focus**. La télécommande doit continuer d'atteindre les
 *   boutons ; un `PlayerView` focusable ajouterait un arrêt invisible au D-pad.
 */
@OptIn(UnstableApi::class)
@Composable
fun TrailerPreview(stream: PlayableStream, modifier: Modifier = Modifier) {
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
                volume = 0f
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                prepare()
            }
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
