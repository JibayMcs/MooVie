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
import fr.moovie.tv.data.net.AppDns
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.navigation.Screen
import fr.moovie.tv.ui.theme.MooVieTheme
import fr.moovie.tv.ui.update.UpdateBanner
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
        var screen: Screen by remember {
            mutableStateOf(if (testStream.isNullOrBlank()) Screen.Home else Screen.Player(testStream))
        }
        // Retour arrière piloté par l'écran courant (Échap) — le panneau des
        // sources de la fiche est fermé par son propre wrapper.
        var backHandler: (() -> Unit)? by remember { mutableStateOf(null) }
        val windowState = rememberWindowState(width = 1280.dp, height = 720.dp)
        val isFullscreen = windowState.placement == WindowPlacement.Fullscreen

        Window(
            onCloseRequest = ::exitApplication,
            title = "Moo-vie",
            state = windowState,
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown || event.key != Key.Escape) return@Window false
                // Échap quitte d'abord le plein écran, puis fait retour.
                when {
                    isFullscreen -> {
                        windowState.placement = WindowPlacement.Floating
                        true
                    }
                    screen != Screen.Home -> {
                        backHandler?.invoke() ?: run { screen = Screen.Home }
                        true
                    }
                    else -> false
                }
            },
        ) {
            MooVieTheme {
                DesktopApp(
                    screen = screen,
                    onNavigate = { screen = it },
                    onRegisterBack = { backHandler = it },
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
    screen: Screen,
    onNavigate: (Screen) -> Unit,
    onRegisterBack: ((() -> Unit)?) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
) {
    // Fiche d'origine de la lecture en cours : le retour du lecteur revient
    // dessus (sans relancer l'auto-lecture) au lieu de l'accueil.
    var lastDetails by remember { mutableStateOf<Screen.Details?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        // Bannière de mise à jour : tout en haut, sur toutes les pages.
        val updateViewModel = remember { DesktopUpdateViewModel() }
        val updateState by updateViewModel.state.collectAsState()
        UpdateBanner(
            state = updateState,
            onInstall = updateViewModel::install,
            onDismiss = updateViewModel::dismiss,
        )

        when (val s = screen) {
            Screen.Home -> DesktopHomeScreen(
                onOpenTitle = { id, isTv -> onNavigate(Screen.Details(id, isTv)) },
                onResume = { e ->
                    onNavigate(
                        Screen.Details(
                            tmdbId = e.tmdbId,
                            isTv = e.isTv,
                            autoSources = true,
                            resumeSeason = e.season,
                            resumeEpisode = e.episode,
                        ),
                    )
                },
                onOpenSettings = { onNavigate(Screen.Settings) },
                onOpenSearch = { onNavigate(Screen.Search) },
            )
            Screen.Settings -> DesktopSettingsScreen(onBack = { onNavigate(Screen.Home) })
            Screen.Search -> DesktopSearchScreen(
                onOpenTitle = { id, isTv -> onNavigate(Screen.Details(id, isTv)) },
            )
            is Screen.Details -> DesktopDetailsScreen(
                params = s,
                onPlay = { url, headers, key, subs ->
                    lastDetails = s.copy(autoSources = false)
                    onNavigate(Screen.Player(url, headers, key, subs))
                },
                onBack = { onNavigate(Screen.Home) },
                onRegisterBack = onRegisterBack,
            )
            is Screen.Player -> {
                val backFromPlayer = { onNavigate(lastDetails ?: Screen.Home) }
                LaunchedEffect(s) { onRegisterBack(backFromPlayer) }
                DesktopPlayerScreen(
                    streamUrl = s.streamUrl,
                    headers = s.headers,
                    mediaKey = s.mediaKey,
                    subtitles = s.subtitles,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    onBack = backFromPlayer,
                )
            }
        }
    }
    // Hors fiche et lecteur, Échap revient simplement à l'accueil.
    LaunchedEffect(screen) {
        if (screen !is Screen.Details && screen !is Screen.Player) onRegisterBack(null)
    }
}

