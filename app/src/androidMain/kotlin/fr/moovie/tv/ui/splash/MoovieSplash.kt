package fr.moovie.tv.ui.splash

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.ImageLoader
import coil3.request.ImageRequest
import fr.moovie.tv.R
import fr.moovie.tv.resources.Res
import fr.moovie.tv.shared.SPLASH_FILE
import kotlinx.coroutines.delay

/**
 * Durée de l'animation, mesurée sur le fichier : 48 images, 1 980 ms dont une
 * pause de 396 ms sur la dernière. On la joue en entier plutôt que de couper
 * dès que l'app est prête — elle dure deux secondes, et un logo tronqué en
 * plein milieu de son tracé fait plus négligé que lent.
 */
private const val SPLASH_MS = 1_980L

/** Fondu de sortie : assez court pour ne pas rallonger, assez long pour ne pas claquer. */
private const val FADE_MS = 220

/**
 * Violet de la première image de l'animation. Le splash **système** porte
 * exactement la même couleur : c'est ce qui rend le passage de l'un à l'autre
 * invisible, là où deux fonds différents produiraient un flash.
 */
internal val SPLASH_BACKGROUND = Color(0xFF1A073C)

/**
 * Écran de lancement animé, affiché par-dessus l'app le temps de son animation.
 *
 * Pourquoi un écran à nous plutôt que le splash système : sur Android 12+,
 * `windowSplashScreenAnimatedIcon` est **masqué dans un cercle** dont seule la
 * partie centrale est visible — d'où le logo rogné. Ce masque n'est pas
 * désactivable, et l'attribut n'accepte de toute façon qu'un
 * `AnimatedVectorDrawable`, ni WebP ni GIF, avec une animation plafonnée à une
 * seconde et inerte avant Android 12. Le splash système est donc réduit à un
 * aplat de couleur, et tout le rendu se fait ici : plein cadre, sans masque,
 * identique de l'API 23 à aujourd'hui.
 *
 * @param onFinished appelé une fois l'animation jouée et le fondu terminé.
 */
@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
@Composable
fun MoovieSplash(onFinished: () -> Unit) {
    val context = LocalContext.current
    val finish by rememberUpdatedState(onFinished)
    var fading by remember { mutableStateOf(false) }

    // Le décodage d'un WebP *animé* passe par ImageDecoder, absent avant
    // Android 9. En dessous, Coil ne rendrait que la première image — presque
    // noire, ce qui donnerait un splash cassé. On sert donc la dernière image,
    // celle qui porte le logo achevé.
    val animated = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    // Le fichier vit dans les ressources partagées : le desktop lit exactement
    // le même octet pour octet, plutôt qu'une copie qui dériverait.
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(animated) {
        if (animated) bytes = runCatching { Res.readBytes(SPLASH_FILE) }.getOrNull()
    }

    val loader = remember(animated) {
        ImageLoader.Builder(context)
            .components { if (animated) add(AnimatedImageDecoder.Factory()) }
            .build()
    }

    val alpha by animateFloatAsState(
        targetValue = if (fading) 0f else 1f,
        animationSpec = tween(durationMillis = FADE_MS, easing = LinearEasing),
        label = "splashFade",
    )

    LaunchedEffect(Unit) {
        delay(if (animated) SPLASH_MS else SPLASH_MS / 3)
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
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(if (animated) bytes else R.drawable.moovie_splash_last)
                .build(),
            imageLoader = loader,
            contentDescription = null,
            // L'animation est en 16:9 comme l'écran d'un téléviseur : `Fit` la
            // pose bord à bord sans rien rogner. `Crop` couperait le logo sur
            // un écran au rapport inhabituel.
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
