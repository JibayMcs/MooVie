package fr.moovie.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.ui.theme.ESPACE
import fr.moovie.tv.ui.theme.ESPACE_SERRE
import fr.moovie.tv.ui.theme.MOOVIE_TEXT
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM
import fr.moovie.tv.ui.theme.margePage
import org.jetbrains.compose.resources.stringResource

/**
 * L'en-tête d'une page secondaire : retour, titre, et ce qui l'accompagne.
 *
 * ## Ce qu'il remplace
 *
 * Cinq écrans écrivaient le même bloc — une `Row`, un bouton retour
 * conditionnel, un `Text` — et chacun l'écrivait un peu autrement. Le titre
 * était en `headlineSmall` et rose sur une filmographie, en `headlineMedium` et
 * blanc sur l'historique ; l'espace en dessous valait seize points ici,
 * vingt-quatre là. Rien de tout cela n'avait été décidé : c'était l'ordre dans
 * lequel les pages avaient été écrites.
 *
 * Une page secondaire commence donc toujours pareil, ce qui a une conséquence
 * qu'on ne voit qu'en naviguant : le titre ne bouge plus d'un pixel quand on
 * passe de l'historique aux téléchargements. Un en-tête qui saute d'un écran à
 * l'autre donne l'impression que l'application recharge.
 *
 * @param sousTitre l'information qui qualifie la page — un décompte, un
 *   filtre actif. Sous le titre plutôt qu'à côté : à côté, elle se lit comme la
 *   suite du titre.
 * @param actions posées à droite, sur la même ligne que le titre. C'est la
 *   place qu'on cherche pour agir sur toute la page — vider un historique,
 *   changer un tri.
 */
@Composable
fun MooviePageHeader(
    titre: String,
    modifier: Modifier = Modifier,
    sousTitre: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = margePage()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ESPACE),
    ) {
        if (onBack != null) {
            MoovieIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.common_back),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ESPACE_SERRE),
        ) {
            Text(
                titre,
                // `headlineMedium`, jamais autre chose : c'est le rang « titre
                // de page », et il ne dépend pas de la page.
                style = MaterialTheme.typography.headlineMedium,
                color = MOOVIE_TEXT,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sousTitre != null) {
                Text(
                    sousTitre,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MOOVIE_TEXT_DIM,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (actions != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ESPACE_SERRE),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        } else {
            // Rien à droite, mais la colonne du titre garde son poids : sans
            // cette boîte vide, un titre court se centrerait au lieu de rester
            // calé à gauche comme sur les pages qui ont des actions.
            Box(modifier = Modifier)
        }
    }
}
