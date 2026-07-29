package fr.moovie.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.moovie_icon
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/** Largeur de l'affiche rebondissante (ratio 2:3 conservé). */
private val POSTER_WIDTH = 200.dp

/** Vitesse de déplacement, en pixels par seconde. */
private const val SPEED_PX_PER_SEC = 90f

/**
 * Écran de veille : l'affiche du média rebondit sur les bords, à la manière du
 * logo DVD, au-dessus de cette même affiche floutée et assombrie.
 *
 * Le déplacement est calculé à partir du temps écoulé entre deux frames, pas
 * d'un pas fixe : la vitesse reste identique quel que soit le rafraîchissement
 * de la TV.
 *
 * N'importe quelle touche, clic ou mouvement de souris appelle [onDismiss].
 */
@Composable
fun MoovieScreensaver(
    posterUrl: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Sur la descente uniquement : sinon le KeyUp de la touche qui a
                // réveillé l'écran repartirait aussitôt vers le lecteur.
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                onDismiss()
                true
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press ||
                            event.type == PointerEventType.Move
                        ) {
                            onDismiss()
                        }
                    }
                }
            },
    ) {
        val density = LocalDensity.current
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val posterW = with(density) { POSTER_WIDTH.toPx() }
        val posterH = posterW * 3f / 2f

        var x by remember { mutableStateOf(screenW * 0.25f) }
        var y by remember { mutableStateOf(screenH * 0.25f) }
        var dx by remember { mutableStateOf(1f) }
        var dy by remember { mutableStateOf(1f) }

        LaunchedEffect(screenW, screenH) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val seconds = (now - last) / 1_000_000_000f
                last = now
                val step = SPEED_PX_PER_SEC * seconds
                x += dx * step
                y += dy * step
                val maxX = (screenW - posterW).coerceAtLeast(0f)
                val maxY = (screenH - posterH).coerceAtLeast(0f)
                if (x <= 0f) { x = 0f; dx = 1f }
                if (x >= maxX) { x = maxX; dx = -1f }
                if (y <= 0f) { y = 0f; dy = 1f }
                if (y >= maxY) { y = maxY; dy = -1f }
            }
        }

        // Fond : la même affiche, floutée et assombrie.
        if (posterUrl != null) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(32.dp),
            )
        }
        // Voile volontairement partiel : à 85 % de noir le fond flouté devenait
        // indiscernable, surtout sur les affiches sombres.
        Box(modifier = Modifier.fillMaxSize().background(Color(0xAA0A0A0A)))

        // L'affiche qui rebondit. Sans visuel exploitable, l'icône de l'app
        // prend le relais : un rectangle vide ne dirait rien.
        Box(
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(width = POSTER_WIDTH, height = POSTER_WIDTH * 3 / 2)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                androidx.compose.foundation.Image(
                    painter = painterResource(Res.drawable.moovie_icon),
                    contentDescription = null,
                    modifier = Modifier.size(POSTER_WIDTH * 0.6f),
                )
            }
        }
    }
}
