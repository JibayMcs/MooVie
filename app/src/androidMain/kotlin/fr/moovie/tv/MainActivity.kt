package fr.moovie.tv

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import fr.moovie.tv.data.download.DownloadRepository
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import fr.moovie.tv.ui.person.PersonScreen
import androidx.compose.runtime.saveable.rememberSaveable
import fr.moovie.tv.ui.splash.MoovieSplash
import fr.moovie.tv.ui.navigation.Screen
import fr.moovie.tv.ui.navigation.rememberNavStack
import fr.moovie.tv.ui.onboarding.OnboardingScreen
import fr.moovie.tv.ui.onboarding.rememberStartScreen
import fr.moovie.tv.data.download.DownloadQueue
import fr.moovie.tv.ui.download.DownloadsScreen
import fr.moovie.tv.ui.download.downloadPlayerScreen
import fr.moovie.tv.data.download.localStream
import fr.moovie.tv.data.pairing.PairingSession
import fr.moovie.tv.data.remote.RemoteFocus
import fr.moovie.tv.data.remote.remoteTarget
import fr.moovie.tv.data.sync.SyncCoordinator
import fr.moovie.tv.data.sync.SyncTrigger
import fr.moovie.tv.ui.profile.ProfileHost
import fr.moovie.tv.ui.details.DetailsScreen
import fr.moovie.tv.ui.history.HistoryScreen
import fr.moovie.tv.ui.home.HomeScreen
import fr.moovie.tv.ui.player.PlayerScreen
import fr.moovie.tv.ui.search.SearchScreen
import fr.moovie.tv.ui.settings.SettingsScreen
import android.content.pm.ActivityInfo
import fr.moovie.tv.ui.adaptive.AdaptiveRoot
import fr.moovie.tv.ui.adaptive.MoovieBottomBar
import fr.moovie.tv.ui.adaptive.UiFlavor
import fr.moovie.tv.ui.adaptive.hidesBottomBar
import fr.moovie.tv.ui.adaptive.isTopLevel
import fr.moovie.tv.ui.adaptive.useBottomNav
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

    /**
     * TV ou téléphone ? La question se pose **à l'exécution** : les deux
     * tournent sur le même APK, donc aucun `expect`/`actual` ni aucune variante
     * de build ne peut trancher.
     *
     * Deux signaux plutôt qu'un, parce qu'ils ratent chacun des cas : le mode
     * d'interface décrit ce que l'appareil est *en train* de faire (une TV
     * branchée en dock, une tablette sur un écran externe), alors que la
     * fonctionnalité `leanback` décrit ce qu'il *est*. Une box qui répond mal à
     * l'un répond en général correctement à l'autre.
     */
    private val uiFlavor: UiFlavor by lazy {
        val onTv = (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
            ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        if (onTv) UiFlavor.TV else UiFlavor.TOUCH
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        handleKeyExtras(intent)

        setContent {
            // L'animation de lancement se pose *au-dessus* de l'app plutôt que
            // devant : l'accueil charge TMDB derrière, et le temps d'animation
            // devient du temps de chargement gagné.
            var splashDone by rememberSaveable { mutableStateOf(false) }
            // `null` tant que le réglage n'est pas lu : on préfère un départ
            // d'animation retardé de quelques millisecondes à un éclair de
            // logo chez quelqu'un qui l'a justement désactivée.
            val splashEnabled by remember { SettingsRepository().splashAnimation }
                .collectAsStateWithLifecycle(initialValue = null)
            LaunchedEffect(splashEnabled) { if (splashEnabled == false) splashDone = true }

            // Thème tv-material (PlayerScreen) autour du thème material3 partagé.
            MooVieTvMaterialTheme {
            MooVieTheme {
                // Repli de focus de la télécommande : le FocusManager appartient à
                // cette composition, il ne peut être capté qu'ici.
                RemoteFocus.Register()
                // Fixe la couleur de contenu par défaut (sinon les Text libres
                // héritent d'une couleur sombre sans Surface parent → invisibles).
                CompositionLocalProvider(LocalContentColor provides Color.White) {
                    AdaptiveRoot(flavor = uiFlavor, modifier = Modifier.fillMaxSize()) {
                    // Le profil est tranché avant que la pile n'existe : les
                    // dépôts lisent le profil actif à leur construction.
                    ProfileHost { _ ->
                    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
                        val updateViewModel: UpdateViewModel = viewModel()
                        // Même instance que celle de DetailsScreen (scope
                        // Activity) : le lecteur peut donc lui rendre la main
                        // quand un flux casse.
                        val detailsViewModel: DetailsViewModel = viewModel()
                        val updateState by updateViewModel.state.collectAsStateWithLifecycle()

                        // Permission de notification, demandée **en contexte** :
                        // au premier téléchargement, pas au lancement. Android
                        // 13+ seulement, et sans elle le service tourne quand
                        // même — seule la barre de progression reste invisible,
                        // ce qui ne justifie pas une boîte de dialogue à
                        // quelqu'un qui n'a rien demandé.
                        if (uiFlavor == UiFlavor.TOUCH &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ) {
                            val ask = rememberLauncherForActivityResult(
                                ActivityResultContracts.RequestPermission(),
                            ) { }
                            val hasDownloads by remember { DownloadRepository().downloads }
                                .collectAsStateWithLifecycle(initialValue = emptyList())
                            LaunchedEffect(hasDownloads.isNotEmpty()) {
                                if (hasDownloads.isEmpty()) return@LaunchedEffect
                                val granted = ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!granted) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        // Miroir du MOOVIE_TEST_STREAM desktop : ouvre le lecteur
                        // directement sur une URL donnée, pour valider la chrome
                        // sans dépendre de l'extraction d'une source réelle.
                        // adb shell am start -n fr.moovie.tv/.MainActivity --es test_stream <url>
                        // `test_key` (ex. tv:1396:s1e1) branche en plus la reprise
                        // et TheIntroDB : sans clé média, le lecteur n'a aucun
                        // titre à interroger et n'affiche ni segments ni boutons.
                        val testStream = remember { intent?.getStringExtra("test_stream") }
                        val testKey = remember { intent?.getStringExtra("test_key").orEmpty() }
                        // `test_source` : un lien d'embed factice, pour faire
                        // apparaître le bouton de téléchargement sans dépendre
                        // d'un hébergeur réel. Sans lui la chrome le masque, à
                        // juste titre — un flux sans source n'a rien à
                        // télécharger.
                        val testSource = remember { intent?.getStringExtra("test_source").orEmpty() }
                        // Racine résolue avant de bâtir la pile : sans clé TMDB
                        // on démarre sur l'écran d'installation, et l'accueil
                        // vide n'apparaît pas même le temps d'une image.
                        val start = rememberStartScreen(
                            override = if (testStream.isNullOrBlank()) {
                                null
                            } else {
                                Screen.Player(
                                    streamUrl = testStream,
                                    mediaKey = testKey,
                                    sourceUrl = testSource,
                                    hoster = "test",
                                    language = "VF",
                                    title = "Flux de test",
                                    subtitle = "S1 · E1 — chrome partagée",
                                )
                            },
                        ) ?: return@Column
                        val nav = rememberNavStack(start)
                        // Pendant la lecture, la bannière rétrécirait la vidéo :
                        // le lecteur affiche une pastille discrète à la place, et
                        // la bannière n'apparaît qu'une fois celle-ci activée.
                        val onPlayer = nav.current is Screen.Player

                        // Publie au lancement, et surtout **en sortant du
                        // lecteur** : c'est là que l'état vient de changer.
                        // Sans ce second cas la TV ne publierait qu'à son
                        // prochain démarrage, et le PC lirait au bureau un
                        // fichier d'avant la soirée.
                        var everPlayed by remember { mutableStateOf(false) }
                        LaunchedEffect(onPlayer) {
                            if (onPlayer) {
                                everPlayed = true
                                return@LaunchedEffect
                            }
                            SyncCoordinator.sync(
                                if (everPlayed) SyncTrigger.PLAYBACK_ENDED else SyncTrigger.LAUNCH,
                                System.currentTimeMillis(),
                            )
                            // Au lancement seulement : un téléchargement coupé
                            // reste RUNNING dans le magasin, personne n'ayant
                            // été là pour écrire autre chose. Sans cette
                            // relance il afficherait une barre qui n'avance
                            // plus — l'état le plus déroutant possible.
                            if (!everPlayed) DownloadQueue.resumePending()
                        }
                        var bannerOnPlayer by remember { mutableStateOf(false) }
                        LaunchedEffect(onPlayer) { if (!onPlayer) bannerOnPlayer = false }

                        // Orientation pilotée par l'écran, sur téléphone seulement.
                        //
                        // On parcourt un catalogue en portrait, une main, le pouce
                        // sur la barre basse ; on regarde une vidéo en paysage.
                        // Faire basculer l'app elle-même évite d'avoir à tourner le
                        // téléphone à chaque lecture — et de le retourner ensuite.
                        //
                        // La TV garde `UNSPECIFIED` : elle est en paysage de toute
                        // façon, et lui imposer une orientation n'apporterait rien.
                        LaunchedEffect(uiFlavor, onPlayer) {
                            requestedOrientation = when {
                                uiFlavor != UiFlavor.TOUCH -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                onPlayer -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }

                            // Plein écran immersif pendant la lecture : l'heure
                            // et la batterie n'ont rien à faire au-dessus d'un
                            // film, et la barre de gestes rognait l'image. Un
                            // balayage depuis le bord les rappelle au besoin.
                            //
                            // Sur TV il n'y a pas de barres système à cacher —
                            // d'où la condition sur le tactile plutôt que sur le
                            // seul écran courant.
                            val bars = WindowInsetsControllerCompat(window, window.decorView)
                            if (uiFlavor == UiFlavor.TOUCH && onPlayer) {
                                bars.systemBarsBehavior =
                                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                bars.hide(WindowInsetsCompat.Type.systemBars())
                            } else {
                                bars.show(WindowInsetsCompat.Type.systemBars())
                            }

                            // Étendre la fenêtre ne suffit pas : le décor continue
                            // d'appliquer l'inset de la découpe en marge, et la
                            // vidéo se retrouvait décalée de sa largeur exacte —
                            // 282 px de bande à gauche contre 170 à droite,
                            // mesurés. Il faut aussi lui dire de ne plus rien
                            // insérer. On ne le fait que pour le lecteur : ailleurs
                            // cet inset est ce qui garde le contenu sous la barre
                            // d'état.
                            WindowCompat.setDecorFitsSystemWindows(
                                window,
                                !(uiFlavor == UiFlavor.TOUCH && onPlayer),
                            )

                            // Dessiner jusque dans la découpe de la caméra.
                            //
                            // `ALWAYS` et non `SHORT_EDGES` : ce dernier n'ouvre
                            // que les bords courts *en portrait*. En paysage, où
                            // la découpe passe sur un côté, il ne s'applique plus
                            // — d'où la bande noire à gauche pendant la lecture,
                            // alors que l'écran continue tout autour de l'objectif.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                window.attributes = window.attributes.apply {
                                    layoutInDisplayCutoutMode = when {
                                        uiFlavor != UiFlavor.TOUCH || !onPlayer ->
                                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                                        else ->
                                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                                    }
                                }
                            }
                        }

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
                                    // Pas de lecture directe : on ouvre la fiche,
                                    // saison de reprise sélectionnée et focus sur
                                    // l'épisode à suivre. Lancer un flux depuis
                                    // l'accueil enlevait tout choix — reprendre
                                    // ailleurs, revoir le résumé, changer de
                                    // source — pour un appui qui ne demandait
                                    // que « où j'en suis ».
                                    nav.push(
                                        Screen.Details(
                                            tmdbId = e.tmdbId,
                                            isTv = e.isTv,
                                            resumeSeason = e.season,
                                            resumeEpisode = e.episode,
                                        ),
                                    )
                                },
                                onOpenSettings = { nav.push(Screen.Settings) },
                                onOpenSearch = { nav.push(Screen.Search) },
                                onOpenHistory = { nav.push(Screen.History) },
                                onOpenDownloads = { nav.push(Screen.Downloads) },
                                onOpenCatalog = { nav.push(Screen.Catalog()) },
                                onOpenCatalogGenre = { nav.push(Screen.Catalog(it)) },
                            )
                            Screen.Onboarding -> OnboardingScreen(
                                onOpenSettings = { nav.push(Screen.Settings) },
                                // Remplace au lieu d'empiler : une fois installé,
                                // revenir en arrière sur l'écran d'installation
                                // n'aurait plus rien à proposer.
                                onReady = { nav.replace(Screen.Home) },
                            )
                            Screen.Downloads -> DownloadsScreen(
                                onPlay = { d -> downloadPlayerScreen(d)?.let(nav::push) },
                                onBack = { nav.pop() },
                                showBackButton = useBottomNav,
                            )

                            Screen.Settings -> SettingsScreen(
                                onBack = { nav.pop() },
                                onPlayDownload = { download ->
                                    // Pas de résolution de sources : le fichier
                                    // est là, et hors ligne personne n'y
                                    // répondrait de toute façon.
                                    localStream(download.key)?.let { local ->
                                        nav.push(
                                            Screen.Player(
                                                streamUrl = local.url,
                                                mediaKey = download.key,
                                                title = download.title,
                                                subtitle = download.subtitle,
                                                posterUrl = download.imageUrl.orEmpty(),
                                            ),
                                        )
                                    }
                                },
                            )
                            is Screen.Catalog -> CatalogScreen(
                                select = s.select,
                                onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                            )
                            Screen.History -> HistoryScreen(
                                onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                            )
                            Screen.Search -> SearchScreen(
                                onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                                onBack = { nav.pop() },
                            )
                            is Screen.Person -> PersonScreen(
                                personId = s.personId,
                                name = s.name,
                                onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                            )
                            is Screen.Details -> DetailsScreen(
                                tmdbId = s.tmdbId,
                                isTv = s.isTv,
                                onOpenPerson = { id, name -> nav.push(Screen.Person(id, name)) },
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
                                sourceUrl = s.sourceUrl,
                                hoster = s.hoster,
                                language = s.language,
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

                        // Sous le contenu, partout sauf là où l'écran est pris
                        // en entier. La restreindre aux destinations de premier
                        // niveau la faisait disparaître sur les fiches — c'est-à-
                        // dire là où l'on passe le plus de temps, et où l'on
                        // perdait donc tout repère.
                        if (useBottomNav && !hidesBottomBar(nav.current)) {
                            MoovieBottomBar(
                                current = nav.current,
                                onSelect = { nav.switchTop(it) },
                            )
                        }
                    }
                    if (!splashDone && splashEnabled == true) {
                        MoovieSplash(onFinished = { splashDone = true })
                    }
                    }
                    }
                }
            }
            }
        }
    }

    // L'app pouvant déjà tourner, l'intent d'injection arrive ici (pas onCreate).
    /**
     * Retour au premier plan : on retente une synchro.
     *
     * Ce déclencheur ne peut pas vivre en `jvmCommon` — `lifecycle-runtime-compose`
     * est propre à Android, le code partagé n'a aucun rappel de reprise.
     */
    override fun onResume() {
        super.onResume()
        // Cible des touches de la télécommande virtuelle. La poser ici et la
        // retirer en pause borne la fonctionnalité au premier plan : c'est ce
        // qui garantit qu'aucun serveur ne reste à l'écoute du réseau une fois
        // Moo-vie quittée.
        remoteTarget = this
        lifecycleScope.launch {
            SyncCoordinator.sync(SyncTrigger.FOREGROUND, System.currentTimeMillis())
        }
    }

    override fun onPause() {
        super.onPause()
        remoteTarget = null
        // Le serveur d'appairage meurt avec le premier plan, télécommande armée
        // ou non : personne ne regarde plus l'écran qui affiche l'adresse.
        PairingSession.stop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleKeyExtras(intent)
    }

    /**
     * Enregistre une clé passée en extra, pour saisir sans clavier de télé :
     *
     *   adb shell am start -n fr.moovie.tv/.MainActivity --es tmdb_key <clé>
     *   adb shell am start -n fr.moovie.tv/.MainActivity --es introdb_key <clé>
     *
     * `adb shell input text` ne convient pas : il tronque les chaînes longues
     * et déforme ponctuation et casse. La clé TheIntroDB, qui fait 90 caractères
     * avec des `:`, `_` et `-`, en ressort systématiquement fausse — d'où cette
     * porte d'entrée, réservée à la mise au point.
     */
    private fun handleKeyExtras(intent: Intent?) {
        val settings = SettingsRepository()
        intent?.getStringExtra("tmdb_key")?.takeIf { it.isNotBlank() }?.let { key ->
            lifecycleScope.launch { settings.setTmdbApiKey(key) }
        }
        intent?.getStringExtra("introdb_key")?.takeIf { it.isNotBlank() }?.let { key ->
            lifecycleScope.launch { settings.setIntroDbApiKey(key) }
        }
    }
}
