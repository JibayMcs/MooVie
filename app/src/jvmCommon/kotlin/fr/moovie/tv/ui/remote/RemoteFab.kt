package fr.moovie.tv.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.remote.RemotePresence
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.remote_title
import fr.moovie.tv.ui.theme.MoovieGradient
import org.jetbrains.compose.resources.stringResource

/**
 * Accès à la télécommande, en bouton flottant au-dessus du contenu.
 *
 * ### Pourquoi pas un onglet
 *
 * Il en a eu un, et il tenait mal : la barre basse partage sa largeur à parts
 * égales, un septième onglet ramène chaque part à 64 dp sur un portrait et les
 * libellés se tronquent. Un bouton flottant ne prend la place de personne, ce
 * qui est la bonne réponse pour une destination **intermittente** — elle
 * n'existe que quand un téléviseur est allumé à côté.
 *
 * ### Une détection, pas une mémoire
 *
 * Il n'apparaît pas parce qu'un appairage a eu lieu un jour, mais parce que le
 * téléviseur **vient de répondre** ([RemotePresence]). C'est toute la
 * différence : le premier critère laissait un bouton derrière un téléviseur
 * débranché, ouvrant un écran dont chaque appui se perdait en silence.
 *
 * Il n'y a donc rien à afficher quand le téléviseur est absent — pas même une
 * version grisée. Un bouton éteint pose la question « pourquoi ? » ; l'absence
 * de bouton ne pose aucune question.
 */
@Composable
fun RemoteFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val present by RemotePresence.found.collectAsState()
    if (!present) return

    Box(
        modifier = modifier
            .padding(20.dp)
            .size(56.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1D1D24), Color(0xFF141419))))
            .border(2.dp, MoovieGradient, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.SettingsRemote,
            contentDescription = stringResource(Res.string.remote_title),
            tint = Color(0xFFCFCFD6),
            modifier = Modifier.size(26.dp),
        )
    }
}
