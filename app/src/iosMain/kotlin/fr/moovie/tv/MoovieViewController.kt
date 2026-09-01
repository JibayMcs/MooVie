package fr.moovie.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.ComposeUIViewController
import fr.moovie.tv.data.download.Download
import fr.moovie.tv.data.net.Connectivity
import fr.moovie.tv.ios.IosCatalogScreen
import fr.moovie.tv.ios.Vm
import fr.moovie.tv.ios.IosDetailsScreen
import fr.moovie.tv.ios.IosDiscoveryScreen
import fr.moovie.tv.ios.IosHistoryScreen
import fr.moovie.tv.ios.IosHomeScreen
import fr.moovie.tv.ios.IosPersonScreen
import fr.moovie.tv.ios.IosPlayerScreen
import fr.moovie.tv.ios.IosSearchScreen
import fr.moovie.tv.ui.adaptive.AdaptiveRoot
import fr.moovie.tv.ui.adaptive.MoovieBottomBar
import fr.moovie.tv.ui.adaptive.UiFlavor
import fr.moovie.tv.ui.adaptive.hidesBottomBar
import fr.moovie.tv.ui.download.DownloadsScreen
import fr.moovie.tv.ui.download.downloadPlayerScreen
import fr.moovie.tv.ui.navigation.NavStack
import fr.moovie.tv.ui.navigation.Screen
import fr.moovie.tv.ui.offline.OfflineScreen
import fr.moovie.tv.ui.offline.OfflineSearchScreen
import fr.moovie.tv.ui.onboarding.OnboardingScreen
import fr.moovie.tv.ui.onboarding.rememberStartScreen
import fr.moovie.tv.ui.player.AvPlayerController
import fr.moovie.tv.ui.player.OrientationEcran
import fr.moovie.tv.ui.player.SurfaceVideo
import fr.moovie.tv.ui.settings.SettingsScreen
import fr.moovie.tv.ui.theme.MooVieTheme
import platform.UIKit.UIViewController

/**
 * Point d'entrée iOS, appelé depuis Swift.
 *
 * Le nom est celui qu'attend `MoovieApp.swift` : Kotlin/Native exporte les
 * fonctions de premier niveau du framework sous le nom du fichier suffixé de
 * `Kt`.
 */
fun MoovieViewController(): UIViewController = ComposeUIViewController { RacineMoovie() }

/**
 * La racine de l'application sur iOS.
 *
 * ## Ce qu'elle est
 *
 * Une [NavStack] — la même classe qu'Android et le desktop — et un `when` sur
 * `nav.current` qui distribue vers les écrans partagés. Les emballages qui
 * branchent chaque ViewModel sont dans `ios/Screens.kt` ; l'interface qu'ils
 * affichent vient de `commonMain`, c'est-à-dire du même Compose qu'Android.
 * Rien de ce que voit l'utilisateur n'est écrit deux fois.
 *
 * ## L'écran de départ n'est pas toujours l'accueil
 *
 * [rememberStartScreen] rend null tant que la réponse n'est pas lue, et on
 * n'affiche rien pendant ce temps : composer un accueil pour le remplacer une
 * lecture DataStore plus tard le ferait clignoter. Sans clé TMDB enregistrée, la
 * racine est [Screen.Onboarding] — pas l'accueil, qui n'aurait rien à montrer.
 *
 * ## Le retour, faute de geste relayé
 *
 * iOS n'a pas de bouton retour matériel, et Compose Multiplatform ne relaie pas
 * encore le balayage depuis le bord jusqu'à la pile. Chaque écran affiche donc
 * le sien — `showBackButton = true` dans les emballages — et la barre de
 * navigation basse tient lieu de sortie pour les destinations de premier niveau.
 * C'est la solution du desktop, où aucune touche ne fait ce travail non plus.
 *
 * ## `UiFlavor.TOUCH`, sans hésitation
 *
 * Pas de détection de forme : un iPhone comme un iPad se pilotent au doigt.
 * `TV` n'existe pas ici — il n'y a pas de télécommande, et le portage a
 * explicitement écarté le rôle de téléviseur sur iOS.
 */
@Composable
private fun RacineMoovie() {
    // La sonde de connectivité doit tourner avant le premier chargement :
    // l'accueil bascule sur la bibliothèque hors ligne si elle dit non, et
    // partir d'un état inconnu ferait clignoter l'écran au lancement.
    LaunchedEffect(Unit) { Connectivity.start() }

    MooVieTheme {
        AdaptiveRoot(flavor = UiFlavor.TOUCH) {
            // **Le fond, puis le retrait des encoches, dans cet ordre.**
            //
            // `MooVieTheme` ne peint rien : `MaterialTheme` déclare une palette,
            // il ne dessine pas de fond, et sans `Surface` parent la vue reste
            // blanche. MainActivity et le desktop ouvrent tous deux sur le même
            // fond peint.
            //
            // Le retrait vient après, parce que `MoovieApp.swift` passe
            // `ignoresSafeArea(.all)` : Compose reçoit toute la dalle, y compris
            // sous la Dynamic Island. Peindre d'abord rend l'encoche sombre ;
            // retirer d'abord y laisserait une bande blanche.
            //
            // `safeDrawing` plutôt que la seule barre d'état : en paysage
            // l'encoche mange un bord latéral, et l'indicateur d'accueil borde
            // le bas. Le modificateur **consomme** ce qu'il applique, donc le
            // `navigationBarsPadding()` de [MoovieBottomBar] n'ajoute rien
            // par-dessus.
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
                val depart = rememberStartScreen()
                // Null = la réponse n'est pas encore lue. Voir le KDoc.
                if (depart != null) {
                    val nav = remember(depart) { NavStack(depart) }
                    val online by Connectivity.online.collectAsState()

                    // **Une vidéo occupe-t-elle tout l'écran ?**
                    //
                    // Deux cas, et un seul endroit pour les reconnaître : le
                    // lecteur, et la fiche dont la bande-annonce est passée au
                    // premier plan. Trois décisions en découlent — l'orientation,
                    // la barre de navigation, le retrait des encoches — et les
                    // faire dépendre d'une même expression est ce qui garantit
                    // qu'elles ne se contrediront pas.
                    //
                    // L'état de la bande-annonce se lit sur le ViewModel de la
                    // fiche, qui vit à l'échelle de l'application : c'est la
                    // seule façon pour la racine de savoir ce qui se passe dans
                    // un écran qu'elle ne fait qu'afficher.
                    val bandeAnnonce by Vm.details.trailerExpanded.collectAsState()
                    val videoPleinEcran = nav.current is Screen.Player ||
                        (nav.current is Screen.Details && bandeAnnonce)

                    // Posé plutôt que compté. La version d'avant appairait une
                    // demande à l'entrée et un relâchement à la sortie, et
                    // l'application restait en paysage après la bande-annonce :
                    // un relâchement manquait. Recalculer la réponse entière à
                    // chaque changement supprime la classe entière de ce défaut
                    // — il n'y a plus de sortie à ne pas oublier.
                    LaunchedEffect(videoPleinEcran) {
                        OrientationEcran.definir(videoPleinEcran)
                    }

                    // Hors ligne, on ne reste pas sur un écran qui ne peut rien
                    // afficher — sauf en lecture, qui joue un fichier local.
                    LaunchedEffect(online) {
                        if (!online && nav.current !is Screen.Player) nav.popToRoot()
                    }

                    // **Une vidéo plein écran garde toute la dalle.**
                    //
                    // Partout ailleurs on retire les encoches, sinon le haut des
                    // pages passe sous la Dynamic Island. Une image de film, elle,
                    // doit aller jusqu'aux bords : c'est ce pour quoi
                    // `MoovieApp.swift` demande `ignoresSafeArea(.all)`, et la
                    // rogner reviendrait à afficher un film en médaillon. Les
                    // commandes du lecteur portent déjà leurs propres marges.
                    Column(
                        modifier = if (videoPleinEcran) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                        },
                    ) {
                        // Le contenu prend la place restante, la barre occupe le
                        // bas. Empilé plutôt que superposé : la barre ne doit pas
                        // recouvrir la dernière ligne d'une liste.
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            EcranCourant(nav = nav, online = online)
                        }

                        // Aux mêmes conditions que sur Android — jamais sur le
                        // lecteur ni sur l'installation initiale, que
                        // `hidesBottomBar` désigne toutes deux — plus une qui
                        // n'existe que sur iOS : la bande-annonce au premier
                        // plan. Elle recouvre l'écran sans changer de
                        // destination, si bien que la pile est toujours sur la
                        // fiche et que `hidesBottomBar` ne peut pas la
                        // reconnaître ; la barre restait donc posée en travers
                        // de la vidéo.
                        if (!hidesBottomBar(nav.current) && !videoPleinEcran) {
                            MoovieBottomBar(
                                current = nav.current,
                                onSelect = { nav.switchTop(it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * L'écran que désigne le sommet de la pile.
 *
 * Extrait de [RacineMoovie] pour que le `when` se lise d'un bloc, sans les
 * quatre niveaux d'indentation de la mise en page.
 */
@Composable
private fun EcranCourant(nav: NavStack, online: Boolean) {
    /** Ouvrir une fiche : le même geste depuis six écrans. */
    val ouvrirTitre: (Int, Boolean) -> Unit = { id, isTv -> nav.push(Screen.Details(id, isTv)) }

    when (val ecran = nav.current) {
        // Sans réseau, l'accueil et la recherche basculent sur ce qui est
        // téléchargé : la même substitution que sur Android, et c'est
        // `OfflineScreen` qui porte l'explication.
        Screen.Home -> if (!online) {
            OfflineScreen(
                onPlay = { d -> lancerTelechargement(nav, d) },
                onOpenSettings = { nav.push(Screen.Settings) },
            )
        } else {
            IosHomeScreen(
                onOpenTitle = ouvrirTitre,
                onResume = { entree ->
                    // Reprise : la fiche ouvre directement le panneau des
                    // sources, sur l'épisode où l'on en était.
                    nav.push(
                        Screen.Details(
                            tmdbId = entree.tmdbId,
                            isTv = entree.isTv,
                            autoSources = true,
                            resumeSeason = entree.season,
                            resumeEpisode = entree.episode,
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
        }

        Screen.Onboarding -> OnboardingScreen(
            onOpenSettings = { nav.push(Screen.Settings) },
            // Remplace au lieu d'empiler : une fois installé, revenir sur
            // l'écran d'installation n'aurait plus rien à proposer.
            onReady = { nav.replace(Screen.Home) },
            // Nul : l'appairage porte un serveur HTTP local, réservé aux cibles
            // JVM, et n'a de sens que face à un téléviseur. Le choix « depuis
            // mon téléphone » disparaît.
            pairingDialog = null,
        )

        Screen.Settings -> SettingsScreen(
            onBack = { nav.pop() },
            onPlayDownload = { d -> lancerTelechargement(nav, d) },
        )

        Screen.Search -> if (!online) {
            OfflineSearchScreen(onPlay = { d -> lancerTelechargement(nav, d) })
        } else {
            IosSearchScreen(
                onOpenTitle = ouvrirTitre,
                onOpenDiscovery = { nav.push(Screen.Discovery) },
                onBack = { nav.pop() },
            )
        }

        Screen.Discovery -> if (!online) {
            OfflineScreen(
                onPlay = { d -> lancerTelechargement(nav, d) },
                onOpenSettings = { nav.push(Screen.Settings) },
            )
        } else {
            IosDiscoveryScreen(onOpenTitle = ouvrirTitre, onBack = { nav.pop() })
        }

        Screen.History -> IosHistoryScreen(onOpenTitle = ouvrirTitre, onBack = { nav.pop() })

        Screen.Downloads -> DownloadsScreen(
            onPlay = { d -> lancerTelechargement(nav, d) },
            onBack = { nav.pop() },
            showBackButton = true,
        )

        is Screen.Catalog -> IosCatalogScreen(
            onOpenTitle = ouvrirTitre,
            select = ecran.select,
            onBack = { nav.pop() },
        )

        is Screen.Person -> IosPersonScreen(
            params = ecran,
            onOpenTitle = ouvrirTitre,
            onBack = { nav.pop() },
        )

        is Screen.Details -> IosDetailsScreen(
            params = ecran,
            onPlay = { lecteur ->
                // Neutralise l'auto-lecture sur l'entrée de la fiche avant
                // d'empiler le lecteur : sinon en revenir relancerait la
                // lecture, qui repousserait le lecteur — boucle sans issue.
                // Le garde-fou `autoConsumed` de l'écran ne suffit pas : la
                // fiche quitte la composition pendant que le lecteur est au
                // sommet, et son `remember` repart à faux au retour.
                if (ecran.autoSources) nav.replace(ecran.copy(autoSources = false))
                nav.push(lecteur)
            },
            onOpenPerson = { id, nom -> nav.push(Screen.Person(id, nom)) },
            onBack = { nav.pop() },
        )

        is Screen.Player -> LecteurIos(nav = nav, params = ecran)

        // Les destinations de la pile Cast — télécommande, écran de diffusion et
        // son lancement. Elles n'existent pas sur iOS, le portage a écarté ce
        // rôle, et rien ne les empile. On revient à l'accueil plutôt que
        // d'afficher un écran vide si l'une d'elles arrivait par une voie qu'on
        // n'a pas prévue.
        else -> LaunchedEffect(ecran) { nav.popToRoot() }
    }
}

/**
 * Le lecteur, avec le contrôleur AVPlayer qu'il faut lui construire.
 *
 * ## Pourquoi le contrôleur naît ici et pas dans l'écran
 *
 * [AvPlayerController] ouvre le flux dès sa construction et retient une session
 * audio. Sa durée de vie doit donc être exactement celle de l'entrée de
 * navigation : `remember(streamUrl)` le recrée quand la source change — c'est ce
 * qui fait qu'un enchaînement d'épisodes rouvre bien un nouveau média — et
 * `DisposableEffect` le libère en quittant. Le laisser vivre plus longtemps
 * laisserait un AVPlayer jouer derrière l'écran suivant, son compris.
 */
@Composable
private fun LecteurIos(nav: NavStack, params: Screen.Player) {
    val controleur = remember(params.streamUrl) {
        AvPlayerController(params.streamUrl, params.headers)
    }
    DisposableEffect(controleur) { onDispose { controleur.liberer() } }

    // L'orientation n'est pas décidée ici : `RacineMoovie` la calcule pour les
    // deux écrans qui la réclament — celui-ci et la bande-annonce au premier
    // plan — depuis une seule expression. Un effet de plus à cet endroit
    // ajouterait un second avis sur la même question.

    IosPlayerScreen(
        streamUrl = params.streamUrl,
        headers = params.headers,
        mediaKey = params.mediaKey,
        title = params.title,
        subtitle = params.subtitle,
        nextSeason = params.nextSeason,
        nextEpisode = params.nextEpisode,
        posterUrl = params.posterUrl,
        startAtMs = params.startAtMs,
        expectedMinutes = params.expectedMinutes,
        controller = controleur,
        surface = { modifier -> SurfaceVideo(controleur, modifier) },
        onBack = { retourDepuisLecteur(nav) },
        // Enchaîner passe par la fiche, qui sait résoudre une source : le lecteur
        // ne connaît qu'une URL, pas l'hébergeur d'où viendra la suivante.
        //
        // **On dépile le lecteur, puis on réécrit la fiche du dessous.**
        //
        // Le seul `replace` d'avant écrasait l'entrée du lecteur, pas celle de la
        // fiche : chaque épisode laissait donc une fiche de plus dans la pile, et
        // quatre épisodes d'affilée demandaient quatre retours sur des pages
        // identiques. L'intention était déjà d'éviter cela — elle n'était tenue
        // qu'à moitié, `replace` économisant la seconde entrée par épisode, pas la
        // première. Dépiler d'abord tient la pile à `[…, fiche, lecteur]` quel que
        // soit le nombre d'épisodes enchaînés, et un seul retour ramène à la série.
        //
        // Le dépilement est sûr ici : `fiche` non nul prouve qu'il y a une entrée
        // sous le lecteur, donc que la pile en compte au moins deux.
        onNextEpisode = { saison, episode ->
            val fiche = nav.previous as? Screen.Details
            if (fiche == null) {
                retourDepuisLecteur(nav)
            } else {
                nav.pop()
                nav.replace(
                    fiche.copy(
                        isTv = true,
                        autoSources = true,
                        resumeSeason = saison,
                        resumeEpisode = episode,
                    ),
                )
            }
        },
        // Le flux a cassé : on rend la main à la fiche, qui reprend sa cascade
        // sur l'hébergeur suivant.
        onPlaybackFailed = { retourDepuisLecteur(nav) },
    )
}

/**
 * Quitter le lecteur, c'est revenir à la fiche dont le flux est parti.
 *
 * `popUpTo` et non `pop` : il désigne la fiche par ce qu'elle est, sans supposer
 * qu'elle occupe l'entrée immédiatement sous le lecteur. C'est ce qui distingue
 * les deux cas de figure sans avoir à les compter — le retour depuis un
 * enchaînement d'épisodes tombe sur la fiche, tandis qu'une lecture ouverte sans
 * fiche du tout, celle d'un téléchargement lancé depuis les réglages, n'en
 * trouve aucune et revient à la racine. Même raisonnement que sur Android.
 */
private fun retourDepuisLecteur(nav: NavStack) {
    if (!nav.popUpTo { it is Screen.Details }) nav.popToRoot()
}

/**
 * Joue un fichier déjà téléchargé, sans passer par sa fiche ni par TMDB.
 *
 * La conversion est celle de `downloadPlayerScreen`, en commun : elle résout le
 * flux local du téléchargement et rend null quand il n'y en a pas — une entrée
 * en file d'attente, ou dont les fichiers ont été effacés. Rien ne s'ouvre alors,
 * ce qui vaut mieux qu'un lecteur sur une URL vide.
 */
private fun lancerTelechargement(nav: NavStack, telechargement: Download) {
    downloadPlayerScreen(telechargement)?.let(nav::push)
}
