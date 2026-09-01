package fr.moovie.tv.ui.components

import fr.moovie.tv.shared.maintenantMs
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.moovie.tv.ui.theme.MoovieGradient
import fr.moovie.tv.ui.adaptive.isPointerUi
import fr.moovie.tv.ui.adaptive.isTouchUi
import fr.moovie.tv.ui.theme.MoovieShape
import fr.moovie.tv.ui.theme.moovieSurface
import fr.moovie.tv.ui.theme.rememberGlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Couleurs de contenu. Il n'y a plus de couleur de *fond* : au repos un bouton
 * n'est que son libellé ou son icône, et tout le reste — verre, halo, liseré
 * dégradé — n'apparaît qu'au focus (voir `moovieSurface`).
 */
private val REST_FG = Color(0xFFC9C9C9)
private val ACTIVE_FG = Color.White
private val DISABLED_FG = Color(0xFF5A5A5A)

/**
 * Bouton de l'app, 100 % foundation (aucune dépendance tv-material).
 *
 * Au repos : **rien**. Pas de fond, pas de bordure — seulement le libellé ou
 * l'icône. Au focus D-pad ou au survol : verre translucide, halo dégradé et
 * liseré aux couleurs de la bannière. [selected] garde le liseré seul, pour
 * marquer l'option retenue sans rallumer tout l'écran.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoovieButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    /**
     * Appui long OK / clic droit. Même mécanique que [MoovieCard] : sur une
     * télécommande, `combinedClickable` seul ne déclenche rien, Android se
     * contentant de répéter les KeyDown tant que la touche est tenue.
     */
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = enabled && (focused || hovered)
    // Agrandissement plus sobre qu'avant : sans fond plein, un bouton qui gonfle
    // se remarque déjà beaucoup.
    val scale by animateFloatAsState(if (active) 1.03f else 1f, label = "moovieButtonScale")
    val glow = rememberGlow(active)

    val fg = when {
        !enabled -> DISABLED_FG
        active || pressed -> ACTIVE_FG
        selected -> ACTIVE_FG
        else -> REST_FG
    }

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // Cible d'au moins 48 dp au doigt. Les marges du bouton donnent
            // 44 × 40 dp — confortable en face d'un focus de télécommande, qui
            // vise pour vous, mais sous le seuil en dessous duquel un pouce
            // rate sa cible sans regarder. `defaultMinSize` n'agrandit que ce
            // qui est plus petit : les boutons à libellé, déjà plus larges, ne
            // bougent pas.
            .then(
                if (isTouchUi) {
                    Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                } else {
                    Modifier
                },
            )
            .clip(MoovieShape)
            .moovieSurface(
                active = active,
                selected = selected,
                pressed = pressed && enabled,
                glowAlpha = glow,
            )
            .then(longPressKeys(onLongClick))
            .secondaryClick(onLongClick)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Le contenu peut réagir au focus (afficher un ✕, dérouler un titre…)
        // sans que chaque appelant ait à propager l'état.
        CompositionLocalProvider(LocalContentColor provides fg, LocalMoovieCardActive provides active) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                content()
            }
        }
    }
}

/**
 * Détection de l'appui long au D-pad, partagée par [MoovieButton] et
 * [MoovieCard].
 *
 * `combinedClickable` ne déclenche `onLongClick` qu'au pointeur : sur une
 * télécommande, maintenir OK ne produisait rien. Android répète les KeyDown tant
 * que la touche est tenue — on les compte, et on avale le KeyUp final pour que
 * le clic simple ne parte pas en plus de l'appui long.
 *
 * L'appui long ne se déclenche qu'au **relâchement**, jamais pendant que la
 * touche est tenue : une popup ouverte sous une touche encore enfoncée recevait
 * aussitôt la fin de l'appui et validait sa première action.
 */
@Composable
private fun longPressKeys(onLongClick: (() -> Unit)?): Modifier {
    if (onLongClick == null) return Modifier
    val confirm = remember { ConfirmKeyPress() }
    val scope = rememberCoroutineScope()
    DisposableEffect(confirm) { onDispose { confirm.reset() } }
    return Modifier.onPreviewKeyEvent { event ->
        if (event.key !in CONFIRM_KEYS) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                confirm.press()
                confirm.watchdog?.cancel()
                confirm.watchdog = scope.launch {
                    delay(CONFIRM_RELEASE_MS)
                    confirm.reset()
                }
                confirm.isLong()
            }
            KeyEventType.KeyUp -> {
                val long = confirm.isLong()
                confirm.reset()
                if (long) onLongClick()
                long
            }
            else -> false
        }
    }
}

/**
 * Clic droit → la même action que l'appui long.
 *
 * `combinedClickable` ne connaît que le bouton principal : à la souris, ouvrir un
 * menu contextuel demandait de *maintenir* le clic gauche, ce que personne ne
 * fait sur un ordinateur. Le geste attendu y est le clic droit, comme l'appui
 * long l'est sur une télécommande et sous le doigt.
 *
 * Pris à la passe `Main` et consommé : sans ça, le clic droit continue sa route
 * et peut déclencher un clic simple sur un parent.
 */
private fun Modifier.secondaryClick(onTrigger: (() -> Unit)?): Modifier {
    if (onTrigger == null) return this
    return pointerInput(onTrigger) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    event.changes.forEach { it.consume() }
                    onTrigger()
                }
            }
        }
    }
}

/**
 * Bouton icône compact (actions secondaires : réglages, tri, vu/non vu…).
 * Même langage visuel que [MoovieButton] : au repos, seule l'icône est visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoovieIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    /**
     * Retourne l'icône horizontalement.
     *
     * Material fournit `Replay` (flèche circulaire antihoraire) sans son
     * symétrique horaire. Plutôt que d'attraper une icône d'une autre famille
     * pour l'avance — qui n'aurait ni le même trait ni le même poids —, on
     * retourne celle-ci. Le miroir porte sur l'icône seule et non sur le
     * bouton : son liseré dégradé s'inverserait, et deux boutons voisins
     * n'auraient plus le même dégradé.
     */
    mirrored: Boolean = false,
) {
    val button = @Composable {
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
                modifier = Modifier
                    .size(20.dp)
                    .then(if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier),
            )
        }
    }

    // Infobulle au survol, **au pointeur seulement**.
    //
    // Une icône seule se devine tant qu'on en a trois ; le lecteur en aligne
    // neuf, et là on cherche. À la télécommande le focus se déplace d'un bouton
    // à l'autre et le libellé n'aurait rien à survoler ; au doigt il n'y a pas
    // de survol du tout. C'est donc un manque propre au bureau, et lui seul.
    //
    // Le texte est le `contentDescription` déjà fourni : il décrit précisément
    // ce que fait le bouton, et le doubler d'un libellé séparé garantirait que
    // les deux finissent par se contredire.
    if (isPointerUi && !contentDescription.isNullOrBlank()) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(contentDescription) } },
            state = rememberTooltipState(),
            // Non focalisable : l'infobulle ne doit pas s'intercaler dans le
            // parcours au clavier entre deux boutons.
            focusable = false,
        ) { button() }
    } else {
        button()
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
    var startedAt = 0L
    var watchdog: Job? = null

    fun press() {
        if (downs == 0) startedAt = maintenantMs()
        downs++
    }

    /**
     * Deux déclencheurs, et il suffit d'un.
     *
     * Compter les répétitions supposait qu'Android en envoie : c'est le cas sur
     * la plupart des télécommandes, pas sur toutes, et une box qui n'en envoie
     * aucune n'avait alors **aucun** appui long — la touche restait simplement
     * enfoncée sans rien produire. La durée, elle, ne dépend d'aucun firmware.
     *
     * Le seuil de temps est plus généreux que le seuil de répétitions : il n'est
     * là que comme filet, et un appui franc doit rester un clic simple.
     */
    fun isLong(): Boolean =
        downs >= LONG_PRESS_DOWNS ||
            (startedAt > 0L && maintenantMs() - startedAt >= LONG_PRESS_MS)

    fun reset() {
        watchdog?.cancel()
        watchdog = null
        downs = 0
        startedAt = 0L
    }
}

/**
 * Nombre de KeyDown (Android répète tant que la touche est tenue) à partir
 * duquel l'appui est considéré long. Deux = une répétition, soit ~400 ms.
 */
private const val LONG_PRESS_DOWNS = 2

/**
 * Durée de maintien qui vaut appui long, quand la télécommande ne répète pas.
 * Au-delà du délai avant la 1re répétition d'Android (~400 ms), pour que les
 * deux mesures s'accordent sur les appareils qui répètent bien.
 */
private const val LONG_PRESS_MS = 500L

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
    val scale by animateFloatAsState(if (active) focusedScale else 1f, label = "moovieCardScale")

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

    // Le menu qui va s'ouvrir détruira ce nœud : on note où rendre le focus
    // à sa fermeture, sans quoi il repart en haut de l'écran (voir
    // [MoovieFocusMemory]). Vaut pour l'appui long télécommande comme souris.
    val selfFocus = remember { FocusRequester() }
    val focusMemory = LocalMoovieFocusMemory.current
    val openMenu = onLongClick?.let {
        {
            focusMemory.capture(selfFocus)
            it()
        }
    }

    val longPressKeys = if (openMenu == null) {
        Modifier
    } else {
        Modifier.onPreviewKeyEvent { event ->
            if (event.key !in CONFIRM_KEYS) return@onPreviewKeyEvent false
            when (event.type) {
                KeyEventType.KeyDown -> {
                    confirm.press()
                    // Chaque répétition repousse le relâchement présumé.
                    confirm.watchdog?.cancel()
                    confirm.watchdog = scope.launch {
                        delay(CONFIRM_RELEASE_MS)
                        confirm.reset()
                    }
                    // Les répétitions au-delà du seuil sont avalées : le clic
                    // simple ne doit pas partir en plus de l'appui long.
                    confirm.isLong()
                }
                KeyEventType.KeyUp -> {
                    val long = confirm.isLong()
                    confirm.reset()
                    if (long) openMenu()
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
            .clip(MoovieShape)
            .background(Color(0xFF141414))
            // Le focus se lit sur le cadre dégradé, pas sur un aplat : l'affiche
            // reste le sujet, la carte n'est qu'un support.
            .then(
                if (active) {
                    Modifier.border(BorderStroke(3.dp, MoovieGradient), MoovieShape)
                } else {
                    Modifier
                },
            )
            .then(longPressKeys)
            .secondaryClick(openMenu)
            .focusRequester(selfFocus)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = openMenu,
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
    // Le défilement était réservé à la carte focalisée ou survolée. **Au doigt,
    // rien n'est jamais actif** : un titre trop long y restait tronqué pour
    // toujours, sans aucun moyen de le lire en entier. Il défile donc en
    // permanence sur écran tactile — `basicMarquee` ne s'anime que si le texte
    // déborde réellement, une carte au titre court ne coûte rien.
    val active = LocalMoovieCardActive.current || isTouchUi
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
