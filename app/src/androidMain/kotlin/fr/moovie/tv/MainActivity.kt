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
import androidx.compose.runtime.LaunchedEffect
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
import fr.moovie.tv.ui.details.DetailsViewModel
import fr.moovie.tv.ui.catalog.CatalogScreen
import androidx.compose.runtime.saveable.rememberSaveable
import fr.moovie.tv.ui.splash.MoovieSplash
import fr.moovie.tv.ui.navigation.Screen
import fr.moovie.tv.ui.navigation.rememberNavStack
import fr.moovie.tv.ui.details.DetailsScreen
import fr.moovie.tv.ui.history.HistoryScreen
import fr.moovie.tv.ui.home.HomeScreen
import fr.moovie.tv.ui.player.PlayerScreen
import fr.moovie.tv.ui.search.SearchScreen
import fr.moovie.tv.ui.settings.SettingsScreen
import fr.moovie.tv.ui.theme.MooVieTheme
import fr.moovie.tv.ui.theme.MooVieTvMaterialTheme
import fr.moovie.tv.ui.update.UpdateBanner
import fr.moovie.tv.ui.update.UpdateState
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
            // L'animation de lancement se pose *au-dessus* de l'app plutôt que
            // devant : l'accueil charge TMDB derrière, et le temps d'animation
            // devient du temps de chargement gagné.
            var splashDone by rememberSaveable { mutableStateOf(false) }

            // Thème tv-material (PlayerScreen) autour du thème material3 partagé.
            MooVieTvMaterialTheme {
            MooVieTheme {
                // Fixe la couleur de contenu par défaut (sinon les Text libres
                // héritent d'une couleur sombre sans Surface parent → invisibles).
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
                        val updateViewModel: UpdateViewModel = viewModel()
                        // Même instance que celle de DetailsScreen (scope
                        // Activity) : le lecteur peut donc lui rendre la main
                        // quand un flux casse.
                        val detailsViewModel: DetailsViewModel = viewModel()
                        val updateState by updateViewModel.state.collectAsStateWithLifecycle()
                        // Miroir du MOOVIE_TEST_STREAM desktop : ouvre le lecteur
                        // directement sur une URL donnée, pour valider la chrome
                        // sans dépendre de l'extraction d'une source réelle.
                        // adb shell am start -n fr.moovie.tv/.MainActivity --es test_stream <url>
                        // `test_key` (ex. tv:1396:s1e1) branche en plus la reprise
                        // et TheIntroDB : sans clé média, le lecteur n'a aucun
                        // titre à interroger et n'affiche ni segments ni boutons.
                        val testStream = remember { intent?.getStringExtra("test_stream") }
                        val testKey = remember { intent?.getStringExtra("test_key").orEmpty() }
                        val nav = rememberNavStack(
                            if (testStream.isNullOrBlank()) {
                                Screen.Home
                            } else {
                                Screen.Player(
                                    streamUrl = testStream,
                                    mediaKey = testKey,
                                    title = "Flux de test",
                                    subtitle = "S1 · E1 — chrome partagée",
                                )
                            },
                        )
                        // Pendant la lecture, la bannière rétrécirait la vidéo :
                        // le lecteur affiche une pastille discrète à la place, et
                        // la bannière n'apparaît qu'une fois celle-ci activée.
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

                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {

                        // Bouton Retour de la télécommande : dépile. Les retours
                        // internes à un écran (panneau des sources, fiche d'épisode)
                        // sont captés avant, par un BackHandler plus profond.
                        BackHandler(enabled = nav.canGoBack) { nav.pop() }

                        when (val s = nav.current) {
                            Screen.Home -> HomeScreen(
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
                                onOpenHistory = { nav.push(Screen.History) },
                                onOpenCatalog = { nav.push(Screen.Catalog) },
                            )
                            Screen.Settings -> SettingsScreen(
                                onBack = { nav.pop() },
                            )
                            Screen.Catalog -> CatalogScreen(
                                onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                            )
                            Screen.History -> HistoryScreen(
                                onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                            )
                            Screen.Search -> SearchScreen(
                                onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                                onBack = { nav.pop() },
                            )
                            is Screen.Details -> DetailsScreen(
                                tmdbId = s.tmdbId,
                                isTv = s.isTv,
                                onPlay = { player ->
                                    // Neutralise l'auto-lecture sur l'entrée de la
                                    // fiche : sinon en revenir du lecteur relancerait
                                    // la lecture, qui repousserait le lecteur.
                                    if (s.autoSources) {
                                        nav.replace(s.copy(autoSources = false))
                                    }
                                    nav.push(player)
                                },
                                onBack = { nav.pop() },
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
                                // Prépare les sources de l'épisode suivant
                                // pendant que celui-ci joue encore : le
                                // ViewModel de la fiche est à l'échelle de
                                // l'Activity, il connaît donc encore la série.
                                onPrefetchNext = {
                                    detailsViewModel.prefetchEpisodeSources(s.nextSeason, s.nextEpisode)
                                },
                                nextSeason = s.nextSeason,
                                nextEpisode = s.nextEpisode,
                                updateVersion = (updateState as? UpdateState.Available)?.version,
                                onUpdateSelected = { bannerOnPlayer = true },
                                posterUrl = s.posterUrl,
                                expectedMinutes = s.expectedMinutes,
                                onBack = { nav.pop() },
                                // Le flux a cassé une fois ouvert : retour à la
                                // fiche, qui reprend la cascade sur l'hébergeur
                                // suivant. Si plus rien n'est à tenter, elle
                                // affiche son erreur habituelle.
                                onPlaybackFailed = {
                                    nav.pop()
                                    detailsViewModel.retryAfterPlaybackFailure()
                                },
                                // Passer le générique d'un épisode → enchaîne le
                                // suivant via la fiche (résolution + lecture auto).
                                onNextEpisode = { tmdbId, season, episode ->
                                    nav.replace(
                                        Screen.Details(
                                            tmdbId = tmdbId,
                                            isTv = true,
                                            autoSources = true,
                                            resumeSeason = season,
                                            resumeEpisode = episode,
                                        ),
                                    )
                                },
                            )
                        }
                        }
                    }
                    if (!splashDone) {
                        MoovieSplash(onFinished = { splashDone = true })
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
