package fr.moovie.tv.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.ui.player.MooviePlayerController
import androidx.compose.runtime.LaunchedEffect
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters

/**
 * Aperçu de la bande-annonce dans le fond de la fiche, sur desktop.
 *
 * Même intention que son pendant Android — recadré, hors du chemin du focus —
 * au son près : ici il peut monter, quand la fiche passe en mode cinéma. Le
 * moteur, lui, diffère : libVLC rend ses images dans un tampon que Compose
 * dessine, via [ComposeVideoSurface] — la même classe que le lecteur
 * plein écran, extraite pour l'occasion plutôt que recopiée. Ses deux invariants
 * de thread-safety sont ce qui sépare une lecture d'un SIGSEGV, et deux copies
 * auraient divergé.
 *
 * ## Tous les appels natifs sur [vlcCommands], et sur lui seul
 *
 * Deux plantages successifs ont établi la règle, et ils méritent d'être écrits
 * parce que le second ressemblait à une correction du premier.
 *
 * **SIGABRT.** Lecteur créé dans un `remember` (donc sur le thread AWT), lancé
 * sur `Dispatchers.IO`, libéré sur un `Thread` jeté là. La glibc avorte le
 * processus : `__pthread_tpp_change_priority: assertion « new_prio == -1 || … »
 * failed`. libVLC ajuste la priorité des threads qu'il touche, et ne supporte
 * pas ceux dont il ne maîtrise pas l'ordonnancement.
 *
 * **SIGSEGV**, dans `libvlc_media_player_new`. La correction précédente donnait
 * un exécutif à un seul fil… **par composable**. Sérialiser chacun dans son coin
 * ne sérialise rien : en changeant de fiche, la libération de l'ancien aperçu et
 * la création du nouveau tournaient en parallèle sur la même instance libVLC.
 *
 * D'où un fil **unique pour tout le processus**. Création, lecture, arrêt et
 * libération y passent tous, dans l'ordre où ils sont demandés, quel que soit le
 * nombre d'aperçus qui se succèdent. C'est la discipline de
 * `VlcjPlayerController`, poussée à l'échelle où elle vaut quelque chose.
 *
 * @param volume 0 (muet) à 1, piloté par le mode cinéma de la fiche. Le son ne
 *   monte qu'une fois l'interface effacée : voir `DetailsScreenContent`.
 */
@Composable
fun TrailerPreview(
    stream: PlayableStream,
    volume: Float,
    onController: (MooviePlayerController?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = vlcFactory ?: return
    val surface = remember { ComposeVideoSurface() }

    // Le volume voulu vit hors de la composition parce que **deux sources** le
    // réclament : ce `LaunchedEffect`, et l'écouteur d'événements de libVLC qui
    // le réapplique au démarrage de la lecture (voir plus bas).
    previewVolume = (volume.coerceIn(0f, 1f) * 100).toInt()
    LaunchedEffect(volume) {
        val cible = previewVolume
        vlcCommands.execute {
            currentPreview?.let { runCatching { it.audio().setVolume(cible) } }
        }
    }

    DisposableEffect(stream.url) {
        // Le lecteur naît et meurt avec le flux, sur le fil dédié. `@Volatile`
        // n'est pas nécessaire : seul ce fil y touche.
        var player: MediaPlayer? = null
        var controller: VlcjPlayerController? = null

        vlcCommands.execute {
            // Un aperçu chasse l'autre. L'ordre du fil unique y suffit en
            // théorie ; en pratique c'est la ceinture qui va avec les
            // bretelles, et le défaut qu'elle empêche est très concret —
            // cinq démultiplexeurs vivants à la fois, googlevideo qui répond
            // 403 à des URLs pourtant valides, et le processus qui finit par
            // tomber.
            releaseCurrentPreview()
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
                currentPreview = p
                // Le même lecteur, vu comme un contrôleur : c'est lui que les
                // contrôles de la fiche pilotent. Il n'y a pas de second
                // lecteur pour la bande-annonce, c'est tout l'objet.
                val vue = VlcjPlayerController(p)
                controller = vue
                onController(vue)
                // Le volume ne se règle **pas** avant `play()` : libVLC ne crée
                // sa sortie audio qu'au démarrage de la lecture, et tout ce
                // qu'on lui demande avant est perdu. C'est ce qui faisait
                // sortir du son sur un aperçu censé être muet.
                //
                // On le pose donc sur l'événement « playing », le premier
                // instant où il tient. `:no-audio` réglerait le silence mais
                // interdirait le fondu, qui est justement la fonctionnalité.
                p.events().addMediaPlayerEventListener(
                    object : MediaPlayerEventAdapter() {
                        override fun playing(mediaPlayer: MediaPlayer) {
                            runCatching { mediaPlayer.audio().setVolume(previewVolume) }
                        }
                    },
                )
                // libVLC ne transmet pas ses en-têtes aux requêtes de segments
                // (voir LocalStreamProxy). Sans conséquence ici : **mesuré**,
                // googlevideo sert ses segments en 206 avec le User-Agent iOS,
                // avec celui de VLC, et sans aucun User-Agent. On le pose
                // quand même sur l'accès principal, gratuitement, mais il ne
                // faut pas compter dessus — un 403 sur un segment vient
                // d'ailleurs, en pratique de plusieurs lecteurs qui tirent la
                // même URL en même temps.
                val options = stream.headers["User-Agent"]
                    ?.let { arrayOf(":http-user-agent=$it") }
                    ?: emptyArray()
                p.media().play(stream.url, *options)
            }
        }

        onDispose {
            vlcCommands.execute {
                // Par identité : si un aperçu plus récent a déjà pris la
                // place, c'est lui qui joue et il ne faut surtout pas le
                // libérer à la place de l'ancien.
                if (player != null && player === currentPreview) releaseCurrentPreview()
                player = null
                controller?.shutdown()
                controller = null
                onController(null)
            }
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

/**
 * Le lecteur d'aperçu en cours, ou null. **Touché uniquement depuis
 * [vlcCommands]** : c'est ce qui permet de s'en passer de synchronisation.
 *
 * Il n'y en a qu'un parce qu'il n'y a qu'un fond de fiche à l'écran. En laisser
 * plusieurs en vie ne se voyait pas — ils sont muets et invisibles — mais se
 * lisait dans les journaux de libVLC, et se payait en 403 puis en SIGSEGV.
 */
private var currentPreview: MediaPlayer? = null

/**
 * Volume voulu pour l'aperçu, de 0 à 100.
 *
 * Hors composition parce qu'il est lu depuis le fil de libVLC — par l'écouteur
 * « playing », qui doit connaître la valeur courante au moment exact où la
 * sortie audio apparaît, sans rien savoir de Compose.
 */
@Volatile
private var previewVolume: Int = 0

/** À n'appeler que depuis [vlcCommands]. */
private fun releaseCurrentPreview() {
    currentPreview?.let {
        runCatching { it.controls().stop() }
        runCatching { it.release() }
    }
    currentPreview = null
}
