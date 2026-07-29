package fr.moovie.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.LocalContentColor
import fr.moovie.tv.data.settings.LocaleManager
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.ui.navigation.Screen
import fr.moovie.tv.ui.details.DetailsScreen
import fr.moovie.tv.ui.home.HomeScreen
import fr.moovie.tv.ui.player.PlayerScreen
import fr.moovie.tv.ui.search.SearchScreen
import fr.moovie.tv.ui.settings.SettingsScreen
import fr.moovie.tv.ui.theme.MooVieTheme
import fr.moovie.tv.ui.theme.MooVieTvMaterialTheme
import fr.moovie.tv.ui.update.UpdateBanner
import fr.moovie.tv.ui.update.UpdateViewModel
import kotlinx.coroutines.launch

/**
 * Activity unique. Navigation par état `Screen` (contrôle explicite du focus/back,
 * plus simple à maîtriser sur TV qu'un NavHost).
 */
class MainActivity : ComponentActivity() {

    // Applique la langue choisie (SYSTEM = locale système) avant toute UI.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        handleTmdbKey(intent)

        setContent {
            // Thème tv-material (PlayerScreen) autour du thème material3 partagé.
            MooVieTvMaterialTheme {
            MooVieTheme {
                // Fixe la couleur de contenu par défaut (sinon les Text libres
                // héritent d'une couleur sombre sans Surface parent → invisibles).
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
                        // Bannière de mise à jour : tout en haut, sur toutes les pages.
                        val updateViewModel: UpdateViewModel = viewModel()
                        val updateState by updateViewModel.state.collectAsStateWithLifecycle()
                        UpdateBanner(
                            state = updateState,
                            onInstall = updateViewModel::install,
                            onDismiss = updateViewModel::dismiss,
                        )

                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        var screen: Screen by remember { mutableStateOf(Screen.Home) }

                        // Bouton Retour de la télécommande : revient à l'accueil
                        // depuis n'importe quel écran (pas de bouton Retour à l'écran).
                        BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

                        when (val s = screen) {
                            Screen.Home -> HomeScreen(
                                onOpenTitle = { id, isTv -> screen = Screen.Details(id, isTv) },
                                onResume = { e ->
                                    screen = Screen.Details(
                                        tmdbId = e.tmdbId,
                                        isTv = e.isTv,
                                        autoSources = true,
                                        resumeSeason = e.season,
                                        resumeEpisode = e.episode,
                                    )
                                },
                                onOpenSettings = { screen = Screen.Settings },
                                onOpenSearch = { screen = Screen.Search },
                            )
                            Screen.Settings -> SettingsScreen(
                                onBack = { screen = Screen.Home },
                            )
                            Screen.Search -> SearchScreen(
                                onOpenTitle = { id, isTv -> screen = Screen.Details(id, isTv) },
                                onBack = { screen = Screen.Home },
                            )
                            is Screen.Details -> DetailsScreen(
                                tmdbId = s.tmdbId,
                                isTv = s.isTv,
                                onPlay = { player -> screen = player },
                                onBack = { screen = Screen.Home },
                                autoSources = s.autoSources,
                                resumeSeason = s.resumeSeason,
                                resumeEpisode = s.resumeEpisode,
                            )
                            is Screen.Player -> PlayerScreen(
                                streamUrl = s.streamUrl,
                                headers = s.headers,
                                mediaKey = s.mediaKey,
                                subtitles = s.subtitles,
                                title = s.title,
                                subtitle = s.subtitle,
                                onBack = { screen = Screen.Home },
                                // Passer le générique d'un épisode → enchaîne le
                                // suivant via la fiche (résolution + lecture auto).
                                onNextEpisode = { tmdbId, season, episode ->
                                    screen = Screen.Details(
                                        tmdbId = tmdbId,
                                        isTv = true,
                                        autoSources = true,
                                        resumeSeason = season,
                                        resumeEpisode = episode,
                                    )
                                },
                            )
                        }
                        }
                    }
                }
            }
            }
        }
    }

    // L'app pouvant déjà tourner, l'intent d'injection arrive ici (pas onCreate).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTmdbKey(intent)
    }

    /**
     * Enregistre une clé TMDB passée en extra (test sans clavier TV) :
     * adb shell am start -n fr.moovie.tv/.MainActivity --es tmdb_key <clé>
     */
    private fun handleTmdbKey(intent: Intent?) {
        intent?.getStringExtra("tmdb_key")?.takeIf { it.isNotBlank() }?.let { key ->
            lifecycleScope.launch { SettingsRepository().setTmdbApiKey(key) }
        }
    }
}
