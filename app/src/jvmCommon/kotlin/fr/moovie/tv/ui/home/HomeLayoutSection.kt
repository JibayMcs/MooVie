package fr.moovie.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.home.HomeLayoutEntry
import fr.moovie.tv.data.home.HomeRowKind
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.layout_help
import fr.moovie.tv.resources.layout_hidden
import fr.moovie.tv.resources.layout_hide
import fr.moovie.tv.resources.layout_move_down
import fr.moovie.tv.resources.layout_move_up
import fr.moovie.tv.resources.layout_reset
import fr.moovie.tv.resources.layout_show
import fr.moovie.tv.resources.pin_remove
import fr.moovie.tv.ui.adaptive.useBottomNav
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.theme.MoovieShape
import org.jetbrains.compose.resources.stringResource

/**
 * Réorganisation de l'accueil : déplacer, masquer, retirer.
 *
 * Des flèches plutôt qu'un glisser-déposer. Le même écran doit se piloter à la
 * télécommande, où il n'existe aucun geste de traîné, et sur un téléphone, où un
 * glisser-déposer dans une liste défilante se bat avec le défilement. Deux
 * boutons font le même travail sur les trois plateformes.
 *
 * Masquer et retirer ne sont pas la même action, et l'écran ne propose que celle
 * qui a un sens : une rangée intégrée se **masque** (elle doit pouvoir revenir,
 * et rien d'autre ne la ferait revenir), un genre épinglé se **retire** (il se
 * réépingle depuis le catalogue, où il n'a jamais cessé d'exister).
 */
@Composable
fun HomeLayoutSection(viewModel: HomeLayoutViewModel = remember { HomeLayoutViewModel() }) {
    val layout by viewModel.layout.collectAsState()

    Text(
        stringResource(Res.string.layout_help),
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF9A9A9A),
    )

    layout.forEachIndexed { index, entry ->
        LayoutRow(
            index = index,
            entry = entry,
            isLast = index == layout.lastIndex,
            onMoveUp = { viewModel.moveUp(entry.id) },
            onMoveDown = { viewModel.moveDown(entry.id) },
            onToggleVisible = { viewModel.setVisible(entry.id, !entry.visible) },
            onRemove = entry.genre?.let { { viewModel.unpin(it.isTv, it.genreId) } },
        )
    }

    MoovieButton(onClick = viewModel::reset) { Text(stringResource(Res.string.layout_reset)) }
}

@Composable
private fun LayoutRow(
    index: Int,
    entry: HomeLayoutEntry,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleVisible: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val label = homeRowLabel(entry)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MoovieShape)
            .background(if (index % 2 == 0) Color(0xFF161616) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${index + 1}. $label",
                style = MaterialTheme.typography.titleMedium,
                // Grisé quand la rangée est masquée : sur un téléphone, la
                // mention « Masquée » passe à la ligne suivante et se lit après
                // coup — la couleur, elle, se voit du premier coup d'œil.
                color = if (entry.visible) Color.White else Color(0xFF777777),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!entry.visible) {
                Text(
                    stringResource(Res.string.layout_hidden),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF777777),
                )
            }
        }
        // Sur téléphone les quatre icônes tiennent encore : 4 × 48 dp sur 448,
        // le libellé garde plus de la moitié de la ligne. Rien à replier ici.
        Row(horizontalArrangement = Arrangement.spacedBy(if (useBottomNav) 4.dp else 8.dp)) {
            if (index > 0) {
                MoovieIconButton(
                    onClick = onMoveUp,
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(Res.string.layout_move_up),
                )
            }
            if (!isLast) {
                MoovieIconButton(
                    onClick = onMoveDown,
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(Res.string.layout_move_down),
                )
            }
            MoovieIconButton(
                onClick = onToggleVisible,
                icon = if (entry.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = stringResource(
                    if (entry.visible) Res.string.layout_hide else Res.string.layout_show,
                ),
                selected = entry.visible,
            )
            // Seuls les genres épinglés : masquer une rangée intégrée est
            // réversible, la supprimer ne le serait pas.
            if (entry.kind == HomeRowKind.GENRE && onRemove != null) {
                MoovieIconButton(
                    onClick = onRemove,
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.pin_remove),
                )
            }
        }
    }
}
