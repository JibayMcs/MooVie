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
import android.view.KeyEvent
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
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import fr.moovie.tv.data.remote.PlayRequest
import fr.moovie.tv.ui.remote.rememberTvSender
import fr.moovie.tv.data.cast.CastNow
import fr.moovie.tv.data.cast.CastPresence
import fr.moovie.tv.ui.remote.CastPlayerScreen
import fr.moovie.tv.ui.remote.CastLaunchScreen
import fr.moovie.tv.ui.remote.CastSessionService
import fr.moovie.tv.ui.remote.catchUpWithTelevision
import fr.moovie.tv.ui.remote.RemoteScreen
import fr.moovie.tv.ui.remote.RemoteVolumeKeys
import fr.moovie.tv.ui.pairing.RemoteHost
import fr.moovie.tv.data.remote.RemoteTargetRepository
import fr.moovie.tv.data.remote.RemotePresence
import fr.moovie.tv.data.remote.RemoteTarget
import fr.moovie.tv.ui.remote.RemoteFab
import fr.moovie.tv.ui.onboarding.OnboardingScreen
import fr.moovie.tv.ui.pairing.PairingDialog
import fr.moovie.tv.ui.pairing.pairingOffered
import fr.moovie.tv.ui.onboarding.rememberStartScreen
import fr.moovie.tv.data.download.DownloadQueue
import fr.moovie.tv.ui.download.DownloadsScreen
import fr.moovie.tv.ui.download.downloadPlayerScreen
import fr.moovie.tv.data.download.localStream
import fr.moovie.tv.data.pairing.PairingSession
import fr.moovie.tv.data.remote.RemoteFocus
import fr.moovie.tv.data.remote.RemoteCast
import fr.moovie.tv.data.remote.RemoteSyncIdentity
import fr.moovie.tv.data.remote.parseRemoteLink
import fr.moovie.tv.data.remote.RemoteLaunch
import fr.moovie.tv.data.watch.ResumeEntry
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.data.remote.remoteTarget
import fr.moovie.tv.data.sync.SyncCoordinator
import fr.moovie.tv.data.sync.SyncSettingsRepository
import fr.moovie.tv.data.sync.SyncTrigger
import fr.moovie.tv.ui.profile.ProfileHost
import fr.moovie.tv.ui.details.DetailsScreen
import fr.moovie.tv.ui.history.HistoryScreen
import fr.moovie.tv.ui.home.HomeScreen
import fr.moovie.tv.data.net.Connectivity
import fr.moovie.tv.ui.offline.OfflineScreen
import fr.moovie.tv.ui.offline.OfflineSearchScreen
import fr.moovie.tv.ui.offline.playableFor
import fr.moovie.tv.ui.player.PlayerHost
import fr.moovie.tv.ui.player.PlayerScreen
import fr.moovie.tv.ui.discovery.DiscoveryScreen
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
        handleRemoteLink(intent)
        handleCastNotification(intent)

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

                        // Le téléviseur écoute et s'annonce tant qu'il est au
                        // premier plan, pour qu'un téléphone le trouve sans
                        // qu'on ait à toucher la télécommande physique.
                        if (uiFlavor == UiFlavor.TV) RemoteHost()

                        // Cible de la télécommande : c'est elle qui décide si
                        // l'icône existe, et l'écran qu'elle ouvre.
                        val remoteTarget by remember { RemoteTargetRepository().target }
                            .collectAsStateWithLifecycle(initialValue = null)
                        val openRemote by pendingRemote.collectAsStateWithLifecycle()
                        LaunchedEffect(openRemote, remoteTarget) {
                            // On attend que la cible soit *lue* : ouvrir avant
                            // afficherait un écran sans téléviseur.
                            if (openRemote && remoteTarget != null) {
                                nav.push(Screen.Remote)
                                pendingRemote.value = false
                            }
                        }
                        // Pendant la lecture, la bannière rétrécirait la vidéo :
                        // le lecteur affiche une pastille discrète à la place, et
                        // la bannière n'apparaît qu'une fois celle-ci activée.
                        val onPlayer = nav.current is Screen.Player

                        // La bande-annonce plein écran est une **lecture**, mais
                        // pas une destination : elle vit dans un état de la fiche,
                        // et l'orientation ne la voyait donc jamais — on regardait
                        // une vidéo dans un téléphone resté en portrait pendant
                        // que le lecteur de film, lui, basculait. Ce n'est pas
                        // l'écran qui décide du paysage, c'est le fait qu'une
                        // vidéo occupe l'écran.
                        val trailerExpanded by detailsViewModel.trailerExpanded
                            .collectAsStateWithLifecycle()
                        // Bornée à la fiche : l'état survit à sa fermeture (le
                        // ViewModel a le scope de l'Activity) et bloquerait
                        // l'accueil en paysage.
                        val onVideo = onPlayer ||
                            (trailerExpanded && nav.current is Screen.Details)

                        // Publie au lancement, et surtout **en sortant du
                        // lecteur** : c'est là que l'état vient de changer.
                        // Sans ce second cas la TV ne publierait qu'à son
                        // prochain démarrage, et le PC lirait au bureau un
                        // fichier d'avant la soirée.
                        // L'empreinte de synchro de cet appareil, pour que le
                        // serveur d'appairage puisse la donner sans interroger
                        // DataStore depuis un fil de socket.
                        //
                        // Elle couvre le **profil actif**, et se recalcule donc
                        // au changement de profil : cet effet vit sous le
                        // `key(current)` de ProfileHost, qui remonte tout l'arbre.
                        // Ce n'est pas un hasard heureux — une empreinte restée
                        // sur l'ancien profil autoriserait le téléviseur à écrire
                        // dans le mauvais.
                        LaunchedEffect(Unit) {
                            runCatching {
                                RemoteSyncIdentity.publish(SyncSettingsRepository().syncFingerprint())
                            }
                        }

                        // Au lancement, sur téléphone : demander au téléviseur ce
                        // qu'il a joué en dernier et l'enregistrer ici. C'est ce
                        // qui rattrape une diffusion suivie jusqu'au bout pendant
                        // que l'application était fermée — sans service en fond.
                        //
                        // Téléphone seulement : une box n'a personne à rattraper,
                        // et s'interrogerait elle-même.
                        if (uiFlavor == UiFlavor.TOUCH) {
                            LaunchedEffect(Unit) { runCatching { catchUpWithTelevision() } }
                        }

                        // Diffuser depuis l'accueil, sans ouvrir de fiche. Le
                        // même composant que la fiche — il porte sa modale de
                        // remplacement — donc une seule règle pour les deux
                        // points d'entrée.
                        // Ce qui part vers un Chromecast, s'il y a lieu : c'est
                        // ce qui décide de la forme de l'écran Télécommande.
                        val castPlayback by CastNow.playback.collectAsStateWithLifecycle()

                        // **La veille Cast vit ici**, et non dans un écran. Elle
                        // ne tournait que sur une fiche de titre : ouvrir
                        // l'application et rester ailleurs ne cherchait aucun
                        // récepteur, et l'icône ne pouvait donc pas apparaître là
                        // où on l'attend — dans le lecteur, notamment. Voir
                        // CastPresence.veille pour la cadence.
                        LaunchedEffect(Unit) { CastPresence.veille() }

                        val homeSender = rememberTvSender(onSent = { nav.push(Screen.Remote) })

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
                        LaunchedEffect(uiFlavor, onVideo) {
                            requestedOrientation = when {
                                uiFlavor != UiFlavor.TOUCH -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                onVideo -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
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
                            if (uiFlavor == UiFlavor.TOUCH && onVideo) {
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
                                !(uiFlavor == UiFlavor.TOUCH && onVideo),
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
                                        uiFlavor != UiFlavor.TOUCH || !onVideo ->
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

                        // Réseau : c'est lui qui décide de quelle application on
                        // se sert. Voir OfflineScreen.
                        val online by Connectivity.online.collectAsStateWithLifecycle()
                        // Collecté ici parce que deux écrans en dépendent hors
                        // ligne — l'historique pour savoir quoi lire, la barre
                        // basse pour sa pastille — et que le flux est le même.
                        val downloads by remember { DownloadRepository().downloads }
                            .collectAsStateWithLifecycle(initialValue = emptyList())

                        // ── Bascule hors ligne ──────────────────────────
                        //
                        // Rentrer dépile jusqu'à l'accueil, qui devient la
                        // bibliothèque : rester sur une fiche que plus rien ne
                        // peut charger n'apprendrait la coupure qu'en la faisant
                        // échouer. **Sauf dans le lecteur** — un fichier
                        // téléchargé se lit très bien sans réseau, et dépiler
                        // sous les pieds de quelqu'un qui regarde un épisode
                        // serait la pire façon de lui annoncer la nouvelle.
                        LaunchedEffect(online) {
                            if (!online && nav.current !is Screen.Player) nav.popToRoot()
                        }

                        // Sur téléphone, le lecteur ne vit pas ici : il part dans
                        // sa propre Activity, seule façon d'avoir une vignette qui
                        // flotte au-dessus d'un Moo-vie encore utilisable. Voir
                        // PlayerHost. On le retire de la pile aussitôt qu'il y
                        // arrive, pour que l'écran resté derrière la vignette soit
                        // la fiche et non un lecteur fantôme — c'est aussi ce qui
                        // fait qu'un retour depuis le lecteur retombe au bon
                        // endroit. Le téléviseur, lui, ne passe jamais par là.
                        LaunchedEffect(nav.current, uiFlavor) {
                            val ecran = nav.current
                            if (ecran !is Screen.Player || uiFlavor != UiFlavor.TOUCH) {
                                return@LaunchedEffect
                            }
                            // `replace` quand la pile n'a que lui : c'est le cas du
                            // flux de test injecté par adb, qui démarre sur le
                            // lecteur. Dépiler laisserait une pile vide.
                            if (nav.canGoBack) nav.pop() else nav.replace(Screen.Home)
                            PlayerHost.ouvre(this@MainActivity, ecran)
                        }

                        // Ce que le lecteur détaché ne peut pas faire lui-même :
                        // les trois passent par `detailsViewModel`, qui a le scope
                        // de cette Activity et connaît la série en cours.
                        // Un titre envoyé depuis le téléphone. Même destination
                        // que l'enchaînement d'épisodes — `autoSources` charge,
                        // résout et ouvre le lecteur sans qu'on touche à rien —
                        // parce que c'est le chemin déjà éprouvé plutôt qu'un
                        // second à maintenir.
                        //
                        // `replace` et non `push` : rien ne doit ramener sur ce
                        // qu'on regardait avant, l'ordre vient d'ailleurs.
                        LaunchedEffect(Unit) {
                            RemoteLaunch.requests.collect { demande ->
                                // La position **ne passe plus par le magasin** :
                                // elle est portée jusqu'au lecteur. Y passer liait
                                // la reprise à la persistance, si bien qu'un
                                // téléviseur sans droit d'écriture repartait du
                                // début. Voir Screen.Player.startAtMs.
                                val castKey = if (demande.isTv) {
                                    "tv:${demande.tmdbId}:s${demande.season}e${demande.episode}"
                                } else {
                                    "movie:${demande.tmdbId}"
                                }
                                // Diffusion depuis un compte étranger : ce titre
                                // ne laissera **rien** sur ce téléviseur — ni
                                // reprise, ni historique. Voir RemoteCast.
                                if (demande.record) RemoteCast.clear() else RemoteCast.markEphemeral(castKey)

                                nav.replace(
                                    Screen.CastLaunch(
                                        tmdbId = demande.tmdbId,
                                        isTv = demande.isTv,
                                        season = demande.season,
                                        episode = demande.episode,
                                        title = demande.title,
                                        subtitle = demande.subtitle,
                                        artwork = demande.artwork,
                                        positionMs = demande.positionMs,
                                        launchId = System.currentTimeMillis(),
                                    ),
                                )
                            }
                        }

                        LaunchedEffect(Unit) {
                            PlayerHost.demandes.collect { demande ->
                                when (demande) {
                                    is PlayerHost.Demande.Prefetch ->
                                        detailsViewModel.prefetchEpisodeSources(
                                            demande.saison,
                                            demande.episode,
                                        )
                                    // La lecture continue sur un Chromecast :
                                    // `push` et non `replace`, la pile étant
                                    // déjà revenue sur la fiche — c'est elle
                                    // qu'on veut retrouver en quittant la
                                    // diffusion.
                                    PlayerHost.Demande.Diffusion ->
                                        nav.push(Screen.Remote)
                                    // Pas de `pop` : la pile est déjà revenue sur
                                    // la fiche au moment où le lecteur s'est
                                    // détaché.
                                    PlayerHost.Demande.Echec ->
                                        detailsViewModel.retryAfterPlaybackFailure()
                                    is PlayerHost.Demande.Episode -> nav.replace(
                                        Screen.Details(
                                            tmdbId = demande.tmdbId,
                                            isTv = true,
                                            autoSources = true,
                                            resumeSeason = demande.saison,
                                            resumeEpisode = demande.episode,
                                        ),
                                    )
                                }
                            }
                        }

                        when (val s = nav.current) {
                            // Le téléviseur appairé, ou rien : sans cible la
                            // destination est inatteignable (l'icône qui y mène
                            // n'existe pas), mais la composition doit rester
                            // totale.
                            // Une diffusion Chromecast passe **avant** la
                            // télécommande : elle n'a pas de cible appairée, et
                            // c'est un lecteur qu'il faut piloter, pas des menus.
                            Screen.Remote -> if (castPlayback != null) {
                                // Le retour système **coupe la diffusion**, comme
                                // le bouton de l'écran. Ce BackHandler-ci est plus
                                // profond que celui de la navigation, donc il
                                // passe avant : sans lui, le geste de retour
                                // sortirait de l'écran en laissant tourner un
                                // relais que plus rien ne pilote. Voir
                                // CastPlayerScreen pour la raison de fond.
                                BackHandler {
                                    CastNow.stopAndClear()
                                    nav.pop()
                                }
                                CastPlayerScreen(onBack = { nav.pop() })
                            } else {
                                remoteTarget?.let {
                                    RemoteScreen(target = it, onBack = { nav.pop() })
                                }
                            }
                            // Hors ligne, l'accueil cède la place à la
                            // bibliothèque locale : voir OfflineScreen.
                            Screen.Home -> if (!online) OfflineScreen(
                                onPlay = { d -> downloadPlayerScreen(d)?.let(nav::push) },
                                onOpenSettings = { nav.push(Screen.Settings) },
                            ) else HomeScreen(
                                onOpenRemote = remoteTarget
                                    ?.takeIf { uiFlavor != UiFlavor.TV }
                                    ?.let { { nav.push(Screen.Remote) } },
                                onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                                // Null quand aucun téléviseur ne répond : l'entrée
                                // de menu n'existe alors pas, plutôt que d'être là
                                // sans rien faire.
                                onSendResumeToTv = if (!homeSender.available) null else {
                                    { e ->
                                        homeSender.ask(
                                            PlayRequest(
                                                tmdbId = e.tmdbId,
                                                isTv = e.isTv,
                                                season = e.season,
                                                episode = e.episode,
                                                title = e.title,
                                                artwork = e.imageUrl.orEmpty(),
                                                positionMs = e.positionMs,
                                                durationMs = e.durationMs,
                                            ),
                                        )
                                    }
                                },
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
                                onOpenDiscovery = { nav.push(Screen.Discovery) },
                                onOpenHistory = { nav.push(Screen.History) },
                                onOpenDownloads = { nav.push(Screen.Downloads) },
                                onOpenCatalog = { nav.push(Screen.Catalog()) },
                                onOpenCatalogGenre = { nav.push(Screen.Catalog(it)) },
                            )
                            Screen.Onboarding -> OnboardingScreen(
                                // Remplace au lieu d'empiler : une fois installé,
                                // revenir en arrière sur l'écran d'installation
                                // n'aurait plus rien à proposer.
                                onReady = { nav.replace(Screen.Home) },
                                // La modale d'appairage : passée en paramètre parce
                                // qu'elle porte un serveur HTTP local, propre aux
                                // cibles JVM. Voir le KDoc d'OnboardingScreen.
                                pairingDialog = if (pairingOffered()) {
                                    { onDismiss, notice, onSaved ->
                                        PairingDialog(
                                            onDismiss = onDismiss,
                                            notice = notice,
                                            onSaved = onSaved,
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                            // Sans bouton retour : au doigt, les téléchargements
                            // sont une destination de la barre du bas, comme
                            // l'historique et le catalogue — dont aucun n'en
                            // porte. C'était le seul, et la flèche y ramenait à
                            // l'accueil depuis un onglet qu'on venait de choisir.
                            // Sur téléviseur, la télécommande a sa touche.
                            Screen.Downloads -> DownloadsScreen(
                                onPlay = { d -> downloadPlayerScreen(d)?.let(nav::push) },
                                onBack = { nav.pop() },
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
                            // Hors ligne, une vignette d'historique lit le
                            // fichier au lieu d'ouvrir une fiche qui ne
                            // chargerait pas : voir playableFor.
                            Screen.History -> HistoryScreen(
                                onOpenTitle = { id, isTv ->
                                    if (online) {
                                        nav.push(Screen.Details(id, isTv))
                                    } else {
                                        downloads.playableFor(id, isTv)
                                            ?.let { d -> downloadPlayerScreen(d)?.let(nav::push) }
                                    }
                                },
                            )
                            // Hors ligne, chercher veut dire chercher dans ce
                            // qu'on possède : voir OfflineSearchScreen.
                            Screen.Search -> if (!online) OfflineSearchScreen(
                                onPlay = { d -> downloadPlayerScreen(d)?.let(nav::push) },
                            ) else SearchScreen(
                                onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                                // Au doigt, c'est le seul chemin vers la
                                // découverte : la barre basse est pleine à six
                                // onglets, et un septième a déjà été essayé.
                                // Ailleurs, l'en-tête de l'accueil a son icône
                                // et la répéter ici n'apporterait rien.
                                onOpenDiscovery = if (useBottomNav) {
                                    { nav.push(Screen.Discovery) }
                                } else {
                                    null
                                },
                                onBack = { nav.pop() },
                            )
                            // Hors ligne, la découverte est tout entière bâtie
                            // sur TMDB : la bibliothèque locale prend sa place.
                            Screen.Discovery -> if (!online) OfflineScreen(
                                onPlay = { d -> downloadPlayerScreen(d)?.let(nav::push) },
                                onOpenSettings = { nav.push(Screen.Settings) },
                            ) else DiscoveryScreen(
                                onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                                onBack = { nav.pop() },
                                showBackButton = useBottomNav,
                            )
                            is Screen.Person -> PersonScreen(
                                personId = s.personId,
                                name = s.name,
                                onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                            )
                            // Diffusion en cours de préparation : l'affiche et le
                            // titre pendant la recherche, puis le lecteur. Aucun
                            // retour possible vers cet écran une fois la lecture
                            // lancée — `replace`, comme l'arrivée de la demande.
                            is Screen.CastLaunch -> CastLaunchScreen(
                                launch = s,
                                onPlay = { player -> nav.replace(player) },
                                onGiveUp = { nav.replace(Screen.Home) },
                            )

                            is Screen.Details -> DetailsScreen(
                                tmdbId = s.tmdbId,
                                isTv = s.isTv,
                                onOpenPerson = { id, name -> nav.push(Screen.Person(id, name)) },
                                onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                                // Après un titre envoyé au salon : on suit sur
                                // l'écran qui montre ce que la TV fait.
                                onOpenRemote = { nav.push(Screen.Remote) },
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
                            is Screen.Player -> if (uiFlavor != UiFlavor.TOUCH) PlayerScreen(
                                streamUrl = s.streamUrl,
                                headers = s.headers,
                                mediaKey = s.mediaKey,
                                // Le téléviseur rend le lecteur **ici**, pas via
                                // PlayerActivity : il y a deux sites d'appel, et
                                // n'en servir qu'un laissait la position de
                                // diffusion à sa valeur par défaut — donc une
                                // lecture qui repart du début, sur l'appareil
                                // qui est justement la cible du cast.
                                startAtMs = s.startAtMs,
                                sourceUrl = s.sourceUrl,
                                hoster = s.hoster,
                                language = s.language,
                                alternatives = s.alternatives,
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
                                // La lecture est partie sur un Chromecast :
                                // `replace` et non `push`, le lecteur local
                                // n'ayant plus rien à montrer — et un retour
                                // depuis la télécommande doit ramener à la
                                // fiche, pas à un lecteur en pause derrière.
                                onCastStarted = { nav.replace(Screen.Remote) },
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

                        // Accès à la télécommande, flottant au-dessus du
                        // contenu. Il ne s'affiche que si le téléviseur vient de
                        // répondre — c'est `RemoteFab` qui en décide — et jamais
                        // par-dessus le lecteur ni sur l'écran qu'il ouvre.
                        if (useBottomNav &&
                            !hidesBottomBar(nav.current) &&
                            !onVideo &&
                            nav.current !is Screen.Remote
                        ) {
                            RemoteFab(
                                onClick = { nav.push(Screen.Remote) },
                                modifier = Modifier.align(Alignment.BottomEnd),
                            )
                        }
                        }

                        // Sous le contenu, partout sauf là où l'écran est pris
                        // en entier. La restreindre aux destinations de premier
                        // niveau la faisait disparaître sur les fiches — c'est-à-
                        // dire là où l'on passe le plus de temps, et où l'on
                        // perdait donc tout repère.
                        //
                        // `onVideo` en plus de la destination : la bande-annonce
                        // plein écran prend tout l'écran sans être un écran, et
                        // la barre d'onglets restait posée en travers de la
                        // vidéo — ce que la règle d'à côté interdit justement
                        // au lecteur.
                        //
                        // `castPlayback` en plus : l'écran de diffusion **est** un
                        // lecteur — même chrome, même gestes — et la barre
                        // d'onglets posée dessous le faisait lire comme une page
                        // parmi d'autres. C'est une distinction d'état et non de
                        // destination : `Screen.Remote` héberge aussi la
                        // télécommande, qui garde sa barre.
                        val barreEnBas = useBottomNav &&
                            !hidesBottomBar(nav.current) &&
                            !onVideo &&
                            !(nav.current is Screen.Remote && castPlayback != null)

                        // **La barre système prend la couleur de celle qu'elle
                        // prolonge.**
                        //
                        // La fenêtre s'arrête au-dessus de la barre de gestes —
                        // `setDecorFitsSystemWindows` juste au-dessus — donc
                        // c'est Android qui peint cette bande, et il la peignait
                        // en noir. La barre d'onglets s'y terminait donc net sur
                        // un gris qui n'était pas le sien, avec la poignée de
                        // gestes posée sur la frontière : la barre paraissait
                        // flotter au-dessus d'un liseré.
                        //
                        // Le `navigationBarsPadding()` de la barre ne peut rien
                        // ici : l'inset vaut zéro tant que la fenêtre ne descend
                        // pas jusque-là. Passer l'application en bord-à-bord
                        // ferait descendre *tout* le contenu sous la barre
                        // d'état, ce qui est une autre revue ; dire au système
                        // quelle couleur employer coûte une ligne et donne le
                        // même résultat à l'œil.
                        //
                        // Le fond de page quand la barre n'est pas là : sur le
                        // lecteur ou l'installation, un gris de barre d'onglets
                        // n'aurait rien à prolonger.
                        @Suppress("DEPRECATION")
                        LaunchedEffect(barreEnBas) {
                            window.navigationBarColor =
                                if (barreEnBas) COULEUR_BARRE_BASSE else COULEUR_FOND
                        }

                        if (barreEnBas) {
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
        // Symétrique du `stop()` de `onPause`. Sans lui, le téléviseur cessait
        // d'écouter au premier passage en arrière-plan : la composition survit à
        // la pause, donc rien ne recréait la socket que la pause avait fermée.
        PairingSession.resume()
        lifecycleScope.launch {
            SyncCoordinator.sync(SyncTrigger.FOREGROUND, System.currentTimeMillis())
        }
        // Le téléviseur appairé est-il allumé ? C'est ce qui décide de
        // l'existence du bouton de télécommande. Ici plutôt que dans la
        // composition, parce que la réponse périme en arrière-plan — on éteint
        // la télé, on rouvre l'application, le bouton doit avoir disparu.
        //
        // Sans cible mémorisée, `refresh` rend faux sans rien émettre sur le
        // réseau : c'est ce qui en fait un appel sans conséquence sur un
        // téléviseur, qui n'en a évidemment aucune.
        lifecycleScope.launch { RemotePresence.refresh() }
    }

    /**
     * Le volume physique, prêté au téléviseur pendant que la télécommande est là.
     *
     * Avant `super`, et non dans `onKeyDown` : les touches de volume n'arrivent
     * jamais jusqu'à lui. `dispatchKeyEvent` est le seul étage de l'application
     * qui les voit avant que la fenêtre ne les traite pour son propre compte, et
     * c'est aussi celui où Cast se branche.
     *
     * Sans écran de télécommande inscrit, [RemoteVolumeKeys.handle] rend faux
     * sans regarder plus loin : ce détournement n'existe donc pas pour le reste
     * de l'application, ni pour le téléviseur, qui exécute pourtant le même
     * `MainActivity`.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        RemoteVolumeKeys.handle(event) || super.dispatchKeyEvent(event)

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
        handleRemoteLink(intent)
        handleCastNotification(intent)
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
    /**
     * Appairage de la télécommande par lien profond.
     *
     * `moovie://remote?h=…&p=…&t=…&n=…` — la page servie par le téléviseur y
     * rebascule quand l'application est installée. On retient la cible ; ouvrir
     * l'écran est ensuite le travail de la composition, qui observe le dépôt.
     *
     * Le jeton ne transite que par ce chemin : il vient du QR affiché à
     * l'écran, donc de quelqu'un qui était devant le téléviseur. C'est
     * précisément ce que l'annonce réseau ne saurait pas prouver.
     */
    /** Passe à vrai quand un lien d'appairage vient d'arriver : la composition ouvre alors l'écran. */
    private val pendingRemote = MutableStateFlow(false)

    private fun handleRemoteLink(intent: Intent?): Boolean {
        // L'analyse est partagée avec le desktop, qui n'a pas de caméra et reçoit
        // le même lien collé à la main. Voir [parseRemoteLink] : la recopier ici
        // l'aurait fait diverger au premier paramètre ajouté.
        val target = parseRemoteLink(intent?.dataString) ?: return false
        lifecycleScope.launch {
            val repo = RemoteTargetRepository()
            val known = repo.target.first()
            repo.remember(target)

            // **Seulement pour un appairage neuf.**
            //
            // L'intent qui a créé la tâche est rejoué à chaque recréation de
            // l'Activity — retour depuis le lanceur après que le système a tué
            // le processus, notamment. Sans cette garde, scanner le QR une seule
            // fois faisait démarrer l'application sur la télécommande, pour
            // toujours : on ouvrait Moo-vie et on tombait sur un pavé
            // directionnel au lieu de l'accueil, ce qui ressemble à une
            // application qui ne s'est pas lancée.
            //
            // On compare le **jeton** plutôt qu'un drapeau dans l'intent :
            // `setIntent` ne met à jour que la copie locale, celle du système
            // reste intacte et revient telle quelle à la recréation suivante.
            // Le dépôt, lui, survit au processus — et c'est exactement la
            // question qu'on pose : « ce lien m'apprend-il quelque chose ? »
            if (known?.token != target.token) pendingRemote.value = true
        }
        return true
    }

    /**
     * Toucher la notification de diffusion ouvre la télécommande.
     *
     * C'est ce qu'on cherche en touchant une diffusion en cours — voir ce que la
     * TV joue, la mettre en pause, la déplacer — et non l'accueil, qui n'a rien à
     * dire sur elle.
     *
     * La garde sur [CastSessionService.live] n'est pas de la prudence : sans
     * elle, on retomberait exactement sur le défaut décrit dans
     * [handleRemoteLink]. Voir la note de ce drapeau.
     */
    private fun handleCastNotification(intent: Intent?) {
        if (intent?.getBooleanExtra(CastSessionService.EXTRA_OPEN_REMOTE, false) != true) return
        if (!CastSessionService.live) return
        pendingRemote.value = true
    }

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

/**
 * Les deux couleurs que la barre système peut prendre.
 *
 * Elles doublent `MOOVIE_BG` et le fond de [MoovieBottomBar] parce que
 * `window.navigationBarColor` attend un entier ARGB et non une `Color` de
 * Compose. Les garder côte à côte est le meilleur moyen de ne pas les laisser
 * diverger : une seule d'entre elles qui bouge et le liseré revient.
 */
private const val COULEUR_BARRE_BASSE = 0xFF121212.toInt()
private const val COULEUR_FOND = 0xFF0A0A0A.toInt()
