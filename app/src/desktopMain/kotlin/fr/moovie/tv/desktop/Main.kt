package fr.moovie.tv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import fr.moovie.tv.data.remote.RemoteFocus
import fr.moovie.tv.data.remote.remoteWindow
import fr.moovie.tv.data.pairing.PairingSession
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
import kotlinx.coroutines.delay
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.compose.resources.painterResource
import fr.moovie.tv.data.net.AppDns
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.moovie_icon
import fr.moovie.tv.ui.catalog.CatalogSelection
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.navigation.NavStack
import fr.moovie.tv.ui.navigation.Screen
import fr.moovie.tv.ui.navigation.rememberNavStack
import fr.moovie.tv.ui.onboarding.OnboardingScreen
import fr.moovie.tv.ui.onboarding.rememberStartScreen
import fr.moovie.tv.data.remote.RemoteTargetRepository
import fr.moovie.tv.ui.remote.RemoteFab
import fr.moovie.tv.ui.remote.RemoteScreen
import fr.moovie.tv.ui.adaptive.AdaptiveRoot
import fr.moovie.tv.ui.adaptive.UiFlavor
import fr.moovie.tv.data.download.DownloadQueue
import fr.moovie.tv.ui.download.DownloadsScreen
import fr.moovie.tv.ui.download.downloadPlayerScreen
import fr.moovie.tv.data.download.localStream
import fr.moovie.tv.data.sync.SyncCoordinator
import fr.moovie.tv.data.sync.SyncTrigger
import fr.moovie.tv.ui.profile.ProfileHost
import fr.moovie.tv.ui.theme.MooVieTheme
import fr.moovie.tv.ui.update.UpdateBanner
import fr.moovie.tv.ui.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import fr.moovie.tv.data.net.Connectivity
import fr.moovie.tv.ui.offline.OfflineScreen
import fr.moovie.tv.ui.offline.OfflineSearchScreen
import fr.moovie.tv.ui.offline.playableFor
import fr.moovie.tv.data.download.DownloadRepository
import fr.moovie.tv.data.settings.DesktopLocale
import androidx.compose.runtime.key

/**
 * Point d'entrée desktop : mêmes écrans que la TV (jvmCommon), navigation par
 * état `Screen`, Échap = retour. Le lecteur vidéo (VLCJ) n'est pas encore
 * branché — écran d'attente à la place.
 */
fun main() {
    // **Avant tout le reste** : `Locale.setDefault` décide de ce que les
    // ressources Compose, les formateurs de dates et la requête TMDB rendront.
    // Posée après la première composition, elle laisserait un écran dessiné
    // dans l'ancienne langue. Voir DesktopLocale.
    DesktopLocale.apply()
    // Sonde réseau, comme MooVieApp la pose côté Android : le premier relevé
    // décide de ce que l'accueil affiche. Ici elle interroge le réseau
    // elle-même, faute d'équivalent système en JVM pure — voir Connectivity.
    Connectivity.start()
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
        // (test du pipeline VLCJ sans dépendre des hébergeurs). MOOVIE_TEST_KEY
        // (ex. tv:1396:s1e1) branche en plus la reprise et TheIntroDB : sans clé
        // média, le lecteur n'a aucun titre à interroger.
        val testStream = remember { System.getenv("MOOVIE_TEST_STREAM") }
        val testKey = remember { System.getenv("MOOVIE_TEST_KEY").orEmpty() }
        // Racine résolue avant de bâtir la pile : sans clé TMDB on démarre sur
        // l'écran d'installation, et l'accueil vide n'apparaît pas même le temps
        // d'une image. La pile se reconstruit une fois la réponse connue — rien
        // n'a encore pu s'y empiler.
        val start = rememberStartScreen(
            override = if (testStream.isNullOrBlank()) {
                null
            } else {
                Screen.Player(testStream, mediaKey = testKey)
            },
        )
        val nav = remember(start) { NavStack(start ?: Screen.Home) }
        // Retour *interne* à un écran uniquement (panneau des sources, fiche
        // d'épisode). Null quand l'écran n'a rien à fermer : Échap dépile alors.
        var innerBack: (() -> Unit)? by remember { mutableStateOf(null) }
        val windowState = rememberWindowState(width = 1280.dp, height = 720.dp)
        // Le plein écran est un mode du **lecteur**, pas de la fenêtre : elle y
        // entre avec lui et en sort avec lui.
        //
        // En faire un état de fenêtre laissait l'application en plein écran sans
        // bordure dès qu'on quittait le lecteur : le seul bouton pour en sortir
        // venait de disparaître avec lui, et le plein écran ayant retiré la barre
        // de titre, il ne restait plus rien pour fermer. Tuer le processus était
        // la seule issue.
        //
        // On mémorise l'*intention* plutôt que de rétablir la fenêtre sur chaque
        // sortie : on quitte le lecteur par le retour, par l'échec de lecture,
        // par l'absence de VLC et par l'enchaînement d'épisodes — les câbler une
        // par une, c'est en oublier une, aujourd'hui ou à la prochaine ajoutée.
        // Et comme l'intention survit à l'écran, revenir au lecteur pour
        // l'épisode suivant retrouve le plein écran sans rien redemander.
        var wantsFullscreen by remember { mutableStateOf(false) }

        val inPlayer = nav.current is Screen.Player

        /**
         * Windows ne passe **pas** par le plein écran de Java.
         *
         * `WindowPlacement.Fullscreen` appelle le plein écran *exclusif*, qui
         * reprogramme le mode d'affichage. Mesuré sur une Quadro 2000 (pilote de
         * 2015, sans DirectX 12) : la fenêtre s'agrandit bien et **l'écran
         * devient noir**, seule la souris reste visible — Échap compris, donc
         * sans autre issue que le gestionnaire de tâches.
         *
         * Ailleurs il fonctionne, et rien ne justifie de le retirer à tout le
         * monde. Voir [PleinEcranSansBordure] pour ce qui le remplace.
         */
        val pleinEcranNatif = !PleinEcranSansBordure.disponible

        /**
         * Vrai dans les **deux** régimes.
         *
         * Sans bordure, la fenêtre reste « flottante » pour Compose : son
         * `placement` ne dit rien. Or Échap s'appuie sur cet état pour rendre la
         * main, et c'est la seule issue quand l'image est noire — elle a manqué
         * une fois, et il a fallu tuer le processus.
         */
        val isFullscreen = windowState.placement == WindowPlacement.Fullscreen ||
            (!pleinEcranNatif && inPlayer && wantsFullscreen)

        LaunchedEffect(inPlayer, wantsFullscreen, pleinEcranNatif) {
            // Sur Windows, tout se joue au niveau de la fenêtre native, dans son
            // propre effet — celui-ci n'a rien à y faire.
            if (!pleinEcranNatif) return@LaunchedEffect
            when {
                inPlayer && wantsFullscreen -> windowState.placement = WindowPlacement.Fullscreen
                // Uniquement depuis le plein écran : une fenêtre que
                // l'utilisateur a maximisée lui-même doit le rester.
                windowState.placement == WindowPlacement.Fullscreen ->
                    windowState.placement = WindowPlacement.Floating
            }
        }

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
                        wantsFullscreen = false
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
            // Cible des touches de la télécommande virtuelle. `window` vient du
            // scope de la fenêtre : c'est notre propre fenêtre, donc rien ne
            // part vers une autre application.
            // Le plein écran sans bordure a besoin de la fenêtre native ; elle
            // n'existe que dans ce scope. Le `DisposableEffect` garantit qu'on
            // rend son cadre à la fenêtre même si l'application se ferme en
            // plein écran — sans quoi elle rouvrirait sans barre de titre.
            LaunchedEffect(pleinEcranNatif, inPlayer, wantsFullscreen) {
                if (pleinEcranNatif) return@LaunchedEffect
                if (inPlayer && wantsFullscreen) {
                    if (!PleinEcranSansBordure.entre(window)) {
                        println("[fenêtre] plein écran sans bordure refusé — on reste en fenêtré")
                    }
                } else {
                    PleinEcranSansBordure.sort(window)
                }
            }
            DisposableEffect(window) {
                remoteWindow = window
                onDispose {
                    PleinEcranSansBordure.sort(window)
                    remoteWindow = null
                    PairingSession.stop()
                }
            }

            // Changer de langue relance toute la composition : `Locale.setDefault`
            // ne prévient personne, et sans cette clé l'écran garderait ses
            // chaînes jusqu'au prochain redessin — un réglage qui a l'air de ne
            // pas marcher.
            key(DesktopLocale.generation.value) {
            MooVieTheme {
                RemoteFocus.Register()
                // L'animation se pose au-dessus de l'app, comme sur Android :
                // l'accueil charge derrière et le temps d'animation est du temps
                // de chargement gagné.
                var splashDone by remember { mutableStateOf(false) }
                // Voir MainActivity : `null` tant que le réglage est inconnu.
                val splashEnabled by remember { SettingsRepository().splashAnimation }
                    .collectAsState(initial = null)
                LaunchedEffect(splashEnabled) { if (splashEnabled == false) splashDone = true }
                // Souris et clavier, et une fenêtre librement redimensionnable :
                // les classes de taille sont donc recalculées à chaque
                // redimensionnement, pas seulement à la rotation.
                AdaptiveRoot(flavor = UiFlavor.POINTER, modifier = Modifier.fillMaxSize()) {
                ProfileHost { profileId ->
                // La pile naît hors de l'enveloppe — elle est capturée par la
                // fenêtre, dont le clavier s'en sert. On la ramène donc à sa
                // racine à la main : changer de profil et retomber sur la fiche
                // d'épisode du précédent serait le contraire de ce qu'on demande.
                LaunchedEffect(profileId) { nav.popToRoot() }
                Box(modifier = Modifier.fillMaxSize()) {
                    if (start != null) {
                    DesktopApp(
                        nav = nav,
                        onRegisterBack = { innerBack = it },
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = { wantsFullscreen = !wantsFullscreen },
                    )
                    }
                    if (!splashDone && splashEnabled == true) {
                        DesktopSplash(onFinished = { splashDone = true })
                    }
                }
                }
                }
            }
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
        val updateViewModel = Vm.update
        val updateState by updateViewModel.state.collectAsState()
        // Pendant la lecture, la bannière rétrécirait la vidéo : le lecteur
        // affiche une pastille discrète, et la bannière n'apparaît qu'une fois
        // celle-ci activée.
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

        UpdateBanner(
            state = if (onPlayer && !bannerOnPlayer) UpdateState.None else updateState,
            onInstall = updateViewModel::install,
            onDismiss = {
                bannerOnPlayer = false
                updateViewModel.dismiss()
            },
        )

        // Réseau : c'est lui qui décide de quelle application on se sert.
        val online by Connectivity.online.collectAsState()
        // Le téléviseur que ce poste pilote. `RemoteFab` s'occupe de le sonder
        // périodiquement — c'est lui qui décide si le bouton existe.
        val remoteTarget by remember { RemoteTargetRepository().target }
            .collectAsState(initial = null)
        // Même flux que celui de la barre basse d'Android : l'historique hors
        // ligne a besoin de savoir ce qui est lisible.
        val downloads by remember { DownloadRepository().downloads }
            .collectAsState(initial = emptyList())

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

        // Le contenu, et par-dessus l'accès à la télécommande.
        Box(modifier = Modifier.fillMaxSize()) {
            when (val s = nav.current) {
                // Le téléviseur appairé, ou rien : sans cible la destination est
                // inatteignable (le bouton qui y mène n'existe pas), mais la
                // composition doit rester totale.
                //
                // Elle rendait `Unit` au motif qu'« une télécommande n'a pas de sens
                // sur un poste de travail ». C'était vrai du poste **piloté**, pas
                // du poste qui pilote : on regarde une fiche sur son ordinateur
                // comme sur son téléphone, et l'envoyer au salon n'a rien de moins
                // naturel. Voir `remoteOffered`.
                Screen.Remote -> remoteTarget?.let {
                    // La chrome se décide dans l'écran, depuis `LocalUiFlavor` :
                    // clavier, pavé cliquable et bouton de sortie viennent avec
                    // le pointeur, sans que l'appelant ait à le dire.
                    RemoteScreen(target = it, onBack = { nav.pop() })
                }
                // Un poste de travail n'est **pas** une cible de diffusion, et c'est
                // volontaire : `pairingOffered` réserve ce rôle au téléviseur, seul
                // appareil où saisir se fait à la télécommande. Rien ne peut donc
                // lui envoyer de titre, mais la destination est partagée et la
                // branche doit exister.
                is Screen.CastLaunch -> Unit
                // Hors ligne, l'accueil cède la place à la bibliothèque locale :
                // voir OfflineScreen.
                Screen.Home -> if (!online) OfflineScreen(
                    onPlay = { d -> downloadPlayerScreen(d)?.let(nav::push) },
                    onOpenSettings = { nav.push(Screen.Settings) },
                ) else DesktopHomeScreen(
                    onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                    onResume = { e ->
                        // Voir MainActivity : le rail ouvre la fiche, il ne lance
                        // plus la lecture.
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
                    onOpenSettings = { nav.push(Screen.Settings) },
                    // Remplace au lieu d'empiler : une fois installé, revenir sur
                    // l'écran d'installation n'aurait plus rien à proposer.
                    onReady = { nav.replace(Screen.Home) },
                )
                Screen.Downloads -> DownloadsScreen(
                    onPlay = { d -> downloadPlayerScreen(d)?.let(nav::push) },
                    onBack = { nav.pop() },
                    showBackButton = true,
                )

                Screen.Settings -> DesktopSettingsScreen(
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
                is Screen.Person -> DesktopPersonScreen(
                    params = s,
                    onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                    onBack = { nav.pop() },
                )
                is Screen.Catalog -> DesktopCatalogScreen(
                    select = s.select,
                    onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                    onBack = { nav.pop() },
                )
                // Hors ligne, une vignette d'historique lit le fichier au lieu
                // d'ouvrir une fiche qui ne chargerait pas : voir playableFor.
                Screen.History -> DesktopHistoryScreen(
                    onOpenTitle = { id, isTv ->
                        if (online) {
                            nav.push(Screen.Details(id, isTv))
                        } else {
                            downloads.playableFor(id, isTv)
                                ?.let { d -> downloadPlayerScreen(d)?.let(nav::push) }
                        }
                    },
                    onBack = { nav.pop() },
                )
                // Hors ligne, chercher veut dire chercher dans ce qu'on possède :
                // voir OfflineSearchScreen.
                Screen.Search -> if (!online) OfflineSearchScreen(
                    onPlay = { d -> downloadPlayerScreen(d)?.let(nav::push) },
                ) else DesktopSearchScreen(
                    onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                    onBack = { nav.pop() },
                )
                // Hors ligne, la découverte n'a rien à découvrir : elle est bâtie
                // sur TMDB de bout en bout. On renvoie à la bibliothèque locale
                // plutôt que d'afficher une page vide.
                Screen.Discovery -> if (!online) OfflineScreen(
                    onPlay = { d -> downloadPlayerScreen(d)?.let(nav::push) },
                    onOpenSettings = { nav.push(Screen.Settings) },
                ) else DesktopDiscoveryScreen(
                    onOpenTitle = { id, isTv -> nav.push(Screen.Details(id, isTv)) },
                    onBack = { nav.pop() },
                )
                is Screen.Details -> DesktopDetailsScreen(
                    params = s,
                    onOpenRemote = { nav.push(Screen.Remote) },
                    onOpenPerson = { id, name -> nav.push(Screen.Person(id, name)) },
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
                        sourceUrl = s.sourceUrl,
                        hoster = s.hoster,
                        language = s.language,
                        alternatives = s.alternatives,
                        subtitles = s.subtitles,
                        title = s.title,
                        subtitle = s.subtitle,
                        nextSeason = s.nextSeason,
                        nextEpisode = s.nextEpisode,
                        updateVersion = (updateState as? UpdateState.Available)?.version,
                        onUpdateSelected = { bannerOnPlayer = true },
                        posterUrl = s.posterUrl,
                        expectedMinutes = s.expectedMinutes,
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
                        // Prépare les sources de l'épisode suivant pendant que
                        // celui-ci joue : le ViewModel de fiche vit à l'échelle
                        // de la fenêtre, il connaît donc encore la série.
                        onPrefetchNext = { Vm.details.prefetchEpisodeSources(s.nextSeason, s.nextEpisode) },
                        onNextEpisode = { season, episode ->
                            // `previous`, et non `current` : `current` **est** le
                            // lecteur, le transtypage échouait donc toujours et
                            // l'enchaînement retombait sur l'accueil. Passer le
                            // générique renvoyait ainsi à la maison au lieu de
                            // lancer l'épisode suivant.
                            val details = nav.previous as? Screen.Details
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

            // Accès à la télécommande, flottant au-dessus du contenu.
            //
            // Il n'apparaît que si le téléviseur vient de répondre — c'est
            // `RemoteFab` qui en décide, et qui le sonde périodiquement. Sans
            // lui, la télécommande ne serait joignable qu'au retour d'un envoi :
            // on diffuserait un titre, on fermerait l'écran, et il faudrait
            // rediffuser pour pouvoir mettre en pause.
            //
            // Jamais par-dessus le lecteur ni sur l'écran qu'il ouvre.
            if (nav.current !is Screen.Player && nav.current !is Screen.Remote) {
                RemoteFab(
                    onClick = { nav.push(Screen.Remote) },
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}

