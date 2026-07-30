package fr.moovie.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Vert « actif/sélectionné » (outline), commun à tous les contrôles. */
val SELECTED_GREEN = Color(0xFF4CAF50)

/** Rouge accent de l'app (focus/hover). */
val MOOVIE_ACCENT = Color(0xFFB5302C)

private val BUTTON_SHAPE = RoundedCornerShape(10.dp)
private val REST_BG = Color(0xFF1E1E1E)
private val REST_FG = Color(0xFFEDEDED)
private val DISABLED_FG = Color(0xFF5A5A5A)
private val PRESSED_BG = Color(0xFF8E2523)

/**
 * Bouton stylé de l'app, 100 % foundation (aucune dépendance tv-material) :
 * sombre au repos, accent rouge au focus D-pad ou au survol souris, [selected]
 * ajoute une outline verte (état actif/choisi).
 */
@Composable
fun MoovieButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = enabled && (focused || hovered)
    val scale by animateFloatAsState(if (active) 1.05f else 1f)

    val bg = when {
        pressed && enabled -> PRESSED_BG
        active -> MOOVIE_ACCENT
        else -> REST_BG
    }
    val fg = when {
        !enabled -> DISABLED_FG
        active || pressed -> Color.White
        else -> REST_FG
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(BUTTON_SHAPE)
            .background(bg)
            .then(if (selected) Modifier.border(2.dp, SELECTED_GREEN, BUTTON_SHAPE) else Modifier)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                content()
            }
        }
    }
}

/**
 * Bouton icône compact (actions secondaires : réglages, tri, vu/non vu…).
 * Même langage visuel que [MoovieButton], avec outline verte si [selected].
 */
@Composable
fun MoovieIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    MoovieButton(
        onClick = onClick,
        modifier = modifier,
        selected = selected,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Vrai quand la [MoovieCard] parente est focalisée (D-pad) ou survolée (souris).
 * Permet au contenu de réagir sans que chaque carte ait à propager l'état :
 * titres qui défilent, synopsis qui se déroule…
 */
val LocalMoovieCardActive = compositionLocalOf { false }

/** Touches « OK » d'une télécommande / d'un clavier. */
private val CONFIRM_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Suivi d'un appui OK au D-pad (mutable hors composition : ces champs ne
 * pilotent aucun rendu). [watchdog] relâche l'état si le KeyUp n'arrive jamais,
 * sans quoi la carte resterait bloquée et ne répondrait plus.
 */
private class ConfirmKeyPress {
    var downs = 0
    var watchdog: Job? = null

    fun reset() {
        watchdog?.cancel()
        watchdog = null
        downs = 0
    }
}

/**
 * Nombre de KeyDown (Android répète tant que la touche est tenue) à partir
 * duquel l'appui est considéré long. Deux = une répétition, soit ~400 ms.
 */
private const val LONG_PRESS_DOWNS = 2

/**
 * Silence au-delà duquel la touche est considérée relâchée. Doit rester
 * nettement supérieur à l'intervalle de répétition d'Android (~50 ms) et au
 * délai avant la 1re répétition (~400 ms).
 */
private const val CONFIRM_RELEASE_MS = 700L

/**
 * Carte cliquable (affiches, épisodes…) : zoom + bordure accent au focus/survol.
 * Remplace tv-material Card dans les écrans partagés. [onLongClick] ouvre un
 * menu contextuel (appui long OK sur TV, clic long/droit à la souris).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoovieCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    focusedScale: Float = 1.1f,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val active = focused || hovered
    val scale by animateFloatAsState(if (active) focusedScale else 1f)
    val shape = RoundedCornerShape(10.dp)

    // `combinedClickable` ne déclenche onLongClick qu'au pointeur : sur une
    // télécommande, maintenir OK ne produisait rien. Android répète les KeyDown
    // tant que la touche est tenue — on les compte, et on avale le KeyUp final
    // pour que le clic simple ne parte pas en plus de l'appui long.
    //
    // L'appui long ne se déclenche qu'au **relâchement**, jamais pendant que la
    // touche est tenue : la popup ouverte sous une touche encore enfoncée
    // recevait aussitôt la fin de l'appui et validait sa première action, sans
    // laisser le temps de choisir.
    val confirm = remember { ConfirmKeyPress() }
    val scope = rememberCoroutineScope()
    DisposableEffect(confirm) { onDispose { confirm.reset() } }
    val longPressKeys = if (onLongClick == null) {
        Modifier
    } else {
        Modifier.onPreviewKeyEvent { event ->
            if (event.key !in CONFIRM_KEYS) return@onPreviewKeyEvent false
            when (event.type) {
                KeyEventType.KeyDown -> {
                    confirm.downs++
                    // Chaque répétition repousse le relâchement présumé.
                    confirm.watchdog?.cancel()
                    confirm.watchdog = scope.launch {
                        delay(CONFIRM_RELEASE_MS)
                        confirm.downs = 0
                    }
                    // Les répétitions au-delà du seuil sont avalées : le clic
                    // simple ne doit pas partir en plus de l'appui long.
                    confirm.downs >= LONG_PRESS_DOWNS
                }
                KeyEventType.KeyUp -> {
                    val long = confirm.downs >= LONG_PRESS_DOWNS
                    confirm.reset()
                    if (long) onLongClick()
                    long
                }
                else -> false
            }
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Color(0xFF181818))
            .then(if (active) Modifier.border(3.dp, MOOVIE_ACCENT, shape) else Modifier)
            .then(longPressKeys)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        CompositionLocalProvider(LocalMoovieCardActive provides active) {
            content()
        }
    }
}

/**
 * Titre d'une carte : tronqué au repos, défile horizontalement quand la carte
 * est focalisée/survolée (les titres longs sont illisibles sur une carte
 * étroite). Ne défile que si le texte déborde réellement.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoovieMarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val active = LocalMoovieCardActive.current
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = if (active) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = if (active) {
            modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 900,
                repeatDelayMillis = 900,
            )
        } else {
            modifier
        },
    )
}
