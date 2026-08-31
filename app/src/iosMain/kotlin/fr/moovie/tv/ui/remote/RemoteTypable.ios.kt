package fr.moovie.tv.ui.remote

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Sans effet : iOS n'annonce aucun champ, faute de télécommande à qui l'annoncer.
 *
 * Le portage a écarté le rôle de cible Cast comme celui de téléviseur piloté —
 * ce sont des rôles de salon, et `data.remote` s'adosse de toute façon à des
 * sockets d'écoute que Kotlin/Native n'a pas ici. Le modificateur existe pour
 * que les écrans partagés se compilent sans condition ; il rend la chaîne telle
 * quelle. Voir le KDoc de l'`expect`.
 */
@Composable
actual fun Modifier.remoteTypable(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    secret: Boolean,
): Modifier = this
