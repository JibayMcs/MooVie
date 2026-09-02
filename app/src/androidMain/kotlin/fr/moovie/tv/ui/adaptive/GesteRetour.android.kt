package fr.moovie.tv.ui.adaptive

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * Le gestionnaire d'Android, tel quel. Posé dans la composition de l'écran, il
 * est plus profond que celui de `MainActivity` — donc consulté avant lui, ce
 * qui est exactement l'ordre voulu : on referme le détail, et c'est seulement
 * une fois qu'il n'y a plus rien à refermer que la pile se dépile.
 */
@Composable
actual fun GesteRetour(actif: Boolean, onRetour: () -> Unit) {
    BackHandler(enabled = actif, onBack = onRetour)
}
