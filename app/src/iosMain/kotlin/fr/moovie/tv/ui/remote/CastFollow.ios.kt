package fr.moovie.tv.ui.remote

import androidx.compose.runtime.Composable

/**
 * Rien à faire sur iOS, faute de diffusion Cast à suivre.
 *
 * Ce geste sert à demander l'autorisation de poser une notification qui
 * survivrait à la mise en arrière-plan pendant qu'une diffusion continue. Or la
 * diffusion elle-même n'existe pas encore ici : le protocole Chromecast se
 * parle en TLS brut sur socket, ce que le portage n'a pas fait. Demander une
 * autorisation pour une fonction absente serait mensonger.
 */
@Composable
actual fun rememberCastFollow(): () -> Unit = {}
