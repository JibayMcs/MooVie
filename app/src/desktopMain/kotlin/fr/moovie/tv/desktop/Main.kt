package fr.moovie.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.compose.resources.painterResource
import fr.moovie.tv.data.net.AppDns
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.moovie_icon
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.navigation.NavStack
import fr.moovie.tv.ui.navigation.Screen
import fr.moovie.tv.ui.navigation.rememberNavStack
import fr.moovie.tv.ui.theme.MooVieTheme
import fr.moovie.tv.ui.update.UpdateBanner
import fr.moovie.tv.ui.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Point d'entrée desktop : mêmes écrans que la TV (jvmCommon), navigation par
 * état `Screen`, Échap = retour. Le lecteur vidéo (VLCJ) n'est pas encore
 * branché — écran d'attente à la place.
 */
fun main() {
    // Même rôle que MooVieApp côté Android : applique la préférence DoH au
    // client d'extraction au démarrage puis à chaque changement de réglage.
    val settings = SettingsRepository()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        combine(settings.dohEnabled, settings.dohProvider) { enabled, provider ->
            enabled to provider
        }.collect { (enabled, provider) ->
            AppDns.configure(enabled, provider)
        }
    }

    application {
        // Crochet de dev : MOOVIE_TEST_STREAM=<url> ouvre directement le lecteur
        // (test du pipeline VLCJ sans dépendre des hébergeurs).
        val testStream = remember { System.getenv("MOOVIE_TEST_STREAM") }
        val nav = rememberNavStack(
            if (testStream.isNullOrBlank()) Screen.Home else Screen.Player(testStream),
        )
        // Retour *interne* à un écran uniquement (panneau des sources, fiche
        // d'épisode). Null quand l'écran n'a rien à fermer : Échap dépile alors.
        var innerBack: (() -> Unit)? by remember { mutableStateOf(null) }
        val windowState = rememberWindowState(width = 1280.dp, height = 720.dp)
        val isFullscreen = windowState.placement == WindowPlacement.Fullscreen

        Window(
            onCloseRequest = ::exitApplication,
            title = "Moo-vie",
            // Sans icône explicite, Compose Desktop affiche celle de Kotlin
            // dans la barre des tâches et le gestionnaire de fenêtres.
            icon = painterResource(Res.drawable.moovie_icon),
            state = windowState,
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown || event.key != Key.Escape) return@Window false
                // Échap quitte d'abord le plein écran, puis fait retour.
                when {
                    isFullscreen -> {
                        windowState.placement = WindowPlacement.Floating
                        true
                    }
                    innerBack != null -> {
                        innerBack?.invoke()
                        true
                    }
                    nav.canGoBack -> {
                        nav.pop()
                        true
                    }
                    else -> false
                }
            },
        ) {
            MooVieTheme {
                DesktopApp(
                    nav = nav,
                    onRegisterBack = { innerBack = it },
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = {
                        windowState.placement =
                            if (isFullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
                    },
                )
            }
        }
    }
}

@Composable
private fun DesktopApp(
    nav: NavStack,
    onRegisterBack: ((() -> Unit)?) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        val updateViewModel = remember { DesktopUpdateViewModel() }
        val updateState by updateViewModel.state.collectAsState()
        // Pendant la lecture, la bannière rétrécirait la vidéo : le lecteur
        // affiche une pastille discrète, et la bannière n'apparaît qu'une fois
        // celle-ci activée.
        val onPlayer = nav.current is Screen.Player
        var bannerOnPlayer by remember { mutableStateOf(false) }
        LaunchedEffect(onPlayer) { if (!onPlayer) bannerOnPlayer = false }

        UpdateBanner(
            state = if (onPlayer && !bannerOnPlayer) UpdateState.None else updateState,
            onInstall = updateViewModel::install,
            onDismiss = {
                bannerOnPlayer = false
                updateViewModel.dismiss()
            },
        )

        when (val s = nav.current) {
            Screen.Home -> DesktopHomeScreen(
                onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                onResume = { e ->
                    nav.push(
                        Screen.Details(
                            tmdbId = e.tmdbId,
                            isTv = e.isTv,
                            autoSources = true,
                            resumeSeason = e.season,
                            resumeEpisode = e.episode,
                        ),
                    )
                },
                onOpenSettings = { nav.push(Screen.Settings) },
                onOpenSearch = { nav.push(Screen.Search) },
            )
            Screen.Settings -> DesktopSettingsScreen(onBack = { nav.pop() })
            Screen.Search -> DesktopSearchScreen(
                onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
            )
            is Screen.Details -> DesktopDetailsScreen(
                params = s,
                onPlay = { player ->
                    // Neutralise l'auto-lecture sur l'entrée de la fiche avant
                    // d'empiler le lecteur : sinon en revenir relancerait la
                    // lecture, qui repousserait le lecteur — boucle sans issue.
                    if (s.autoSources) nav.replace(s.copy(autoSources = false))
                    nav.push(player)
                },
                onBack = { nav.pop() },
                onRegisterBack = onRegisterBack,
            )
            is Screen.Player -> {
                DesktopPlayerScreen(
                    streamUrl = s.streamUrl,
                    headers = s.headers,
                    mediaKey = s.mediaKey,
                    subtitles = s.subtitles,
                    title = s.title,
                    subtitle = s.subtitle,
                    nextSeason = s.nextSeason,
                    nextEpisode = s.nextEpisode,
                    updateVersion = (updateState as? UpdateState.Available)?.version,
                    onUpdateSelected = { bannerOnPlayer = true },
                    posterUrl = s.posterUrl,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    onBack = { nav.pop() },
                    // Le flux a cassé une fois ouvert : retour à la fiche, qui
                    // reprend la cascade sur l'hébergeur suivant. Si plus rien
                    // n'est à tenter, elle affiche son erreur habituelle.
                    onPlaybackFailed = {
                        nav.pop()
                        Vm.details.retryAfterPlaybackFailure()
                    },
                    // Enchaînement : repasse par la fiche, qui résout la source
                    // du nouvel épisode puis relance le lecteur.
                    // Enchaînement : remplace l'entrée du lecteur par la fiche
                    // du nouvel épisode, sinon chaque épisode ajouterait une
                    // marche à remonter pour revenir à la série.
                    onNextEpisode = { season, episode ->
                        val details = nav.current as? Screen.Details
                        nav.replace(
                            details?.copy(
                                autoSources = true,
                                resumeSeason = season,
                                resumeEpisode = episode,
                            ) ?: Screen.Home,
                        )
                    },
                )
            }
        }
    }
}

