package fr.moovie.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.moovie.tv.ui.theme.MoovieShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Réglage à choix unique, façon « select » : un bouton affiche la valeur
 * courante et ouvre une liste modale. Préféré à une rangée de boutons dès que
 * les options se comptent en poignée — à la télécommande, traverser sept
 * boutons pour atteindre le dernier est pénible.
 *
 * Le premier élément prend le focus à l'ouverture, et Retour ferme la liste.
 */
@Composable
fun <T> MoovieSelect(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    MoovieButton(onClick = { open = true }, modifier = modifier) {
        Text(label(selected))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
    }

    if (open) {
        val firstFocus = remember { FocusRequester() }
        Dialog(onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier
                    .clip(MoovieShape)
                    .background(Color(0xF5161616))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                options.forEachIndexed { index, option ->
                    val isSelected = option == selected
                    MoovieButton(
                        onClick = {
                            open = false
                            onSelect(option)
                        },
                        selected = isSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                    ) {
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(label(option))
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    }
}
