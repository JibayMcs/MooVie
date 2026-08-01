package fr.moovie.tv.desktop

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import fr.moovie.tv.resources.Res
import fr.moovie.tv.shared.SPLASH_FILE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

/** Violet de la première image : même valeur que côté Android, même raccord. */
private val SPLASH_BACKGROUND = Color(0xFF1A073C)

/** Fondu de sortie, aligné sur celui d'Android. */
private const val FADE_MS = 220

/**
 * Écran de lancement animé du desktop.
 *
 * Coil ne sert à rien ici : son décodeur d'images animées (`coil-gif`) n'est
 * publié que pour Android. En revanche Compose Desktop embarque déjà Skia, dont
 * le `Codec` sait lire un WebP animé — d'où ce décodage image par image plutôt
 * qu'une dépendance de plus.
 *
 * **Une image décodée = une bitmap neuve, rendue immuable avant d'être publiée.**
 * C'est l'invariant qui a coûté cher au lecteur vidéo : réutiliser une bitmap
 * pendant que Skia la recopie pour l'afficher provoque une lecture hors bornes,
 * et un SIGSEGV en plein milieu du rendu. Ici le décodage écrit dans une bitmap
 * que personne ne lit encore, et n'expose que des images figées.
 *
 * Le fichier lu est celui des ressources partagées, le même qu'Android.
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun DesktopSplash(onFinished: () -> Unit) {
    val finish by rememberUpdatedState(onFinished)
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var fading by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (fading) 0f else 1f,
        animationSpec = tween(durationMillis = FADE_MS, easing = LinearEasing),
        label = "desktopSplashFade",
    )

    LaunchedEffect(Unit) {
        runCatching {
            val bytes = Res.readBytes(SPLASH_FILE)
            val codec = withContext(Dispatchers.Default) {
                Codec.makeFromData(Data.makeFromBytes(bytes))
            }
            val info = codec.imageInfo

            // Une seule bitmap de travail, qui porte le composite courant : les
            // images d'un WebP animé sont différentielles, chacune se peint
            // par-dessus la précédente. Décoder dans une bitmap neuve en
            // annonçant qu'elle contient déjà l'image d'avant produit une image
            // corrompue — c'est ce qui arrivait ici.
            val canvas = Bitmap().apply { allocPixels(info) }
            val rowBytes = info.minRowBytes

            for (i in 0 until codec.frameCount) {
                val snapshot = withContext(Dispatchers.Default) {
                    if (i == 0) codec.readPixels(canvas, i) else codec.readPixels(canvas, i, i - 1)
                    // Copie explicite des pixels : `makeClone` partagerait le
                    // pixel ref, et la bitmap publiée changerait sous Skia
                    // pendant qu'il la dessine. C'est le SIGSEGV déjà payé sur
                    // le lecteur vidéo. Chaque image publiée est donc la sienne,
                    // figée avant d'être exposée.
                    val pixels = canvas.readPixels(info, rowBytes, 0, 0)
                    Bitmap().apply {
                        allocPixels(info)
                        pixels?.let { installPixels(info, it, rowBytes) }
                        setImmutable()
                    }
                }
                frame = snapshot.asComposeImageBitmap()
                delay(codec.getFrameInfo(i).duration.toLong().coerceAtLeast(16L))
            }
        }
        // Décodage impossible : on ne bloque pas le démarrage sur une décoration.
        fading = true
        delay(FADE_MS.toLong())
        finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(SPLASH_BACKGROUND),
    ) {
        frame?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
