package fr.moovie.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.moovie.tv.data.net.Connectivity
import fr.moovie.tv.ui.adaptive.AdaptiveRoot
import fr.moovie.tv.ui.adaptive.UiFlavor
import fr.moovie.tv.ui.home.HomeScreenContent
import fr.moovie.tv.ui.home.HomeViewModel
import fr.moovie.tv.ui.navigation.Screen
import fr.moovie.tv.ui.navigation.rememberNavStack
import fr.moovie.tv.ui.theme.MooVieTheme
import platform.UIKit.UIViewController

/**
 * Point d'entrée iOS, appelé depuis Swift.
 *
 * Le nom est celui qu'attend `ContentView.swift` : Kotlin/Native exporte les
 * fonctions de premier niveau du framework sous le nom du fichier suffixé de
 * `Kt`.
 */
fun MoovieViewController(): UIViewController = ComposeUIViewController { RacineMoovie() }

/**
 * La racine de l'application sur iOS.
 *
 * ## Ce qu'elle est, et ce qu'elle n'est pas encore
 *
 * Elle branche le vrai accueil — `HomeViewModel` et `HomeScreenContent`, les
 * mêmes que sur Android et desktop — sur le thème et la mise en page
 * adaptative partagés. L'écran d'attente qui tenait cette place depuis le
 * premier commit du portage a disparu : ce qui s'affiche maintenant est
 * l'application.
 *
 * En revanche la navigation s'arrête là. `NavStack` est bien créé et le retour
 * matériel n'existe pas sur iOS — c'est le geste de balayage qui en tient lieu,
 * et Compose Multiplatform ne le relaie pas encore. Les écrans de destination
 * (fiche, recherche, catalogue, réglages) attendent leur câblage, et les
 * rappels ci-dessous sont donc muets plutôt que faux : mieux vaut un bouton
 * sans effet qu'un bouton qui ouvre un écran à moitié branché.
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
            val modele: HomeViewModel = viewModel { HomeViewModel() }

            val etat by modele.state.collectAsState()
            val reprises by modele.resume.collectAsState()
            val vus by modele.watched.collectAsState()
            val aVoir by modele.watchlist.collectAsState()

            // `remember` sur la pile : elle est créée ici et servira aux écrans
            // de destination dès qu'ils seront branchés. La garder maintenant
            // évite de restructurer la racine à ce moment-là.
            remember(nav) { nav }

            HomeScreenContent(
                state = etat,
                resume = reprises,
                watched = vus,
                watchlist = aVoir,
                onOpenTitle = { _, _ -> },
                onResume = {},
                onOpenSettings = {},
                onOpenSearch = {},
                onOpenHistory = {},
                onOpenCatalog = {},
                onOpenCatalogGenre = {},
                onRemoveResume = modele::removeResume,
                onMarkResumeWatched = modele::markResumeWatched,
                onRemoveFromWatchlist = modele::removeFromWatchlist,
                onAddToWatchlist = modele::addToWatchlist,
                // Nul et non vide : le contrat de ce paramètre est « null s'il
                // n'y a pas de téléviseur à portée », et il n'y en aura jamais
                // sur iOS — la diffusion Cast a été écartée du portage. Le
                // bouton ne s'affiche donc pas, au lieu de s'afficher sans
                // effet.
                onSendResumeToTv = null,
                onOpenRemote = null,
            )
        }
    }
}
