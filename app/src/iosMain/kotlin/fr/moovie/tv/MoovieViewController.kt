package fr.moovie.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.moovie.tv.data.net.Connectivity
import fr.moovie.tv.ui.adaptive.AdaptiveRoot
import fr.moovie.tv.ui.adaptive.UiFlavor
import fr.moovie.tv.ui.home.HomeScreenContent
import fr.moovie.tv.ui.home.HomeViewModel
import fr.moovie.tv.ui.navigation.Screen
import fr.moovie.tv.ui.navigation.rememberNavStack
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
 * ## Une pile, et non plus un seul écran
 *
 * Jusqu'ici cette fonction affichait l'accueil et rien d'autre : les rappels
 * d'ouverture étaient des lambdas vides, si bien qu'« Ouvrir les réglages » ne
 * faisait rien — et comme l'accueil s'ouvre sur cet écran-là tant qu'aucune clé
 * TMDB n'est enregistrée, l'application était en pratique inutilisable.
 *
 * Elle tient maintenant une [fr.moovie.tv.ui.navigation.NavStack], la même
 * classe que MainActivity et le desktop, et dispatche sur `nav.current` comme
 * eux. Les écrans qu'elle atteint sont **les écrans partagés** : c'est le même
 * Compose que sur Android, pas une réécriture — d'où la même interface et le
 * même style, sans effort pour les tenir alignés.
 *
 * ## Ce qui n'est pas encore branché, et pourquoi
 *
 * Recherche, fiche, historique, découverte, téléchargements et lecteur vivent
 * encore dans `jvmCommon`, où le portage ne les a pas encore fait passer. Leurs
 * rappels restent muets plutôt que faux — mieux vaut un bouton sans effet qu'un
 * bouton qui ouvre un écran à moitié branché. Ils arriveront par la même voie
 * que les réglages : le contenu partagé remonte en `commonMain`, un emballage
 * iOS le branche, une branche s'ajoute ici.
 *
 * Le catalogue et la filmographie font exception : leur contenu **est** déjà
 * commun. Ils attendent quand même, et pour une raison de navigation, pas de
 * portage — ni l'un ni l'autre n'affiche de bouton retour, parce que sur Android
 * c'est la touche matérielle qui les ferme. Les ouvrir ici enfermerait
 * l'utilisateur dedans. Ils arriveront avec [fr.moovie.tv.ui.adaptive.MoovieBottomBar],
 * qui est la sortie qu'Android leur donne en mode tactile, et qui suppose que
 * tous ses onglets mènent quelque part.
 *
 * ## Le retour
 *
 * Il n'y a pas de bouton retour matériel sur iOS ; c'est le balayage depuis le
 * bord qui en tient lieu, et Compose Multiplatform ne le relaie pas jusqu'à la
 * pile. Chaque écran porte donc son propre `onBack`, qui dépile — la même
 * convention que le desktop, où la touche Échap ne couvre pas tout non plus.
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
    // partir sur un état inconnu ferait clignoter l'écran au lancement.
    LaunchedEffect(Unit) { Connectivity.start() }

    MooVieTheme {
        AdaptiveRoot(flavor = UiFlavor.TOUCH) {
            val nav = rememberNavStack(Screen.Home)

            when (nav.current) {
                Screen.Settings -> SettingsScreen(onBack = { nav.pop() })

                // Tout le reste retombe sur l'accueil : c'est la seule autre
                // destination atteignable pour l'instant, et rien ne pousse les
                // autres. Voir le KDoc ci-dessus.
                else -> AccueilMoovie(onOpenSettings = { nav.push(Screen.Settings) })
            }
        }
    }
}

/**
 * L'accueil partagé, branché sur son ViewModel.
 *
 * Extrait de [RacineMoovie] parce que deux branches du `when` l'affichent, et
 * qu'un `HomeViewModel` construit dans chacune rechargerait les rangées à
 * chaque passage.
 */
@Composable
private fun AccueilMoovie(onOpenSettings: () -> Unit) {
    val modele: HomeViewModel = viewModel { HomeViewModel() }
    val etat by modele.state.collectAsState()
    val reprises by modele.resume.collectAsState()
    val vus by modele.watched.collectAsState()
    val aVoir by modele.watchlist.collectAsState()

    HomeScreenContent(
        state = etat,
        resume = reprises,
        watched = vus,
        watchlist = aVoir,
        onOpenTitle = { _, _ -> },
        onResume = {},
        onOpenSettings = onOpenSettings,
        onOpenSearch = {},
        onOpenHistory = {},
        onOpenCatalog = {},
        onOpenCatalogGenre = {},
        onRemoveResume = modele::removeResume,
        onMarkResumeWatched = modele::markResumeWatched,
        onRemoveFromWatchlist = modele::removeFromWatchlist,
        onAddToWatchlist = modele::addToWatchlist,
        // Nul et non vide : le contrat de ce paramètre est « null s'il n'y a pas
        // de téléviseur à portée », et il n'y en aura jamais sur iOS — la
        // diffusion Cast a été écartée du portage. Le bouton ne s'affiche donc
        // pas, au lieu de s'afficher sans effet.
        onSendResumeToTv = null,
        onOpenRemote = null,
    )
}
