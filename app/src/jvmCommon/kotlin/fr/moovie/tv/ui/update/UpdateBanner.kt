package fr.moovie.tv.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import fr.moovie.tv.ui.theme.MoovieGradient
import androidx.compose.ui.unit.dp
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.update_available
import fr.moovie.tv.resources.update_downloading
import fr.moovie.tv.resources.update_install
import fr.moovie.tv.resources.update_later
import fr.moovie.tv.resources.update_retry
import fr.moovie.tv.ui.components.MoovieButton
import org.jetbrains.compose.resources.stringResource

/**
 * Bannière de mise à jour, tout en haut de l'écran sur toutes les pages.
 * Dégradé aux couleurs du branding ; « Installer » télécharge puis ouvre
 * l'installateur système, « Plus tard » masque jusqu'au prochain démarrage.
 */
@Composable
fun UpdateBanner(
    state: UpdateState,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state !is UpdateState.None,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Le dégradé de l'app, pas une seconde version approchante :
                // ce bandeau avait ses propres teintes, proches sans être les
                // mêmes — deux identités pour un seul produit.
                .background(MoovieGradient)
                .padding(horizontal = 32.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state) {
                is UpdateState.Available -> {
                    Text(
                        stringResource(Res.string.update_available, state.version),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    MoovieButton(onClick = onInstall) {
                        Icon(
                            Icons.Default.SystemUpdateAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(Res.string.update_install))
                    }
                    MoovieButton(onClick = onDismiss) { Text(stringResource(Res.string.update_later)) }
                }
                is UpdateState.Downloading -> {
                    Text(
                        stringResource(Res.string.update_downloading, state.version, (state.progress * 100).toInt()),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    LinearProgressIndicator(
                        progress = { state.progress },
                        color = Color.White,
                        trackColor = Color(0x66000000),
                        modifier = Modifier.weight(1f),
                    )
                }
                is UpdateState.Error -> {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    MoovieButton(onClick = onInstall) { Text(stringResource(Res.string.update_retry)) }
                    MoovieButton(onClick = onDismiss) { Text(stringResource(Res.string.update_later)) }
                }
                UpdateState.None -> Unit
            }
        }
    }
}
