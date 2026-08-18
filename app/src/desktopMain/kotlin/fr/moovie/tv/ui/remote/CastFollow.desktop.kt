package fr.moovie.tv.ui.remote

import androidx.compose.runtime.Composable

/**
 * Rien à faire sur desktop.
 *
 * La fenêtre est ouverte ou l'application est fermée : il n'y a pas
 * d'entre-deux où une diffusion continuerait sans que personne ne regarde, donc
 * rien à maintenir en vie et rien à mettre dans un volet de notifications que
 * l'utilisateur ne consulte pas pour ça. Même choix que
 * [fr.moovie.tv.data.download.DownloadForeground] côté desktop.
 *
 * La contrepartie est assumée : fermer l'application pendant que la box joue
 * laisse le poste en arrière sur la progression, et c'est le rattrapage au
 * lancement qui la rattrape — exactement ce que faisait le téléphone avant que
 * son service existe.
 */
@Composable
actual fun rememberCastFollow(): () -> Unit = {}
