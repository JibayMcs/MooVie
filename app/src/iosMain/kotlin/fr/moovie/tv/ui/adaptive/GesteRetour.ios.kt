package fr.moovie.tv.ui.adaptive

import androidx.compose.runtime.Composable

/** Rien à faire : voir l'attendu commun. iOS n'a pas de geste de retour global. */
@Composable
actual fun GesteRetour(actif: Boolean, onRetour: () -> Unit) = Unit
