package fr.moovie.tv.ui.remote

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.cast_launching
import fr.moovie.tv.ui.components.MoovieAsyncImage
import fr.moovie.tv.ui.details.DetailsState
import fr.moovie.tv.ui.details.DetailsViewModel
import fr.moovie.tv.ui.navigation.Screen
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.theme.MoovieShape
import org.jetbrains.compose.resources.stringResource

/**
 * Ce que le téléviseur montre pendant qu'il cherche une source pour un titre
 * envoyé depuis le téléphone.
 *
 * ## Pourquoi cet écran existe
 *
 * La diffusion passait par la fiche, avec la recherche automatique. Ça
 * fonctionnait, et ça avait deux défauts.
 *
 * Le premier, visible : la fiche s'affichait deux à cinq secondes, avec ses
 * saisons, son casting et ses boutons — un écran fait pour être parcouru, posé
 * là où personne ne regarde, pour être remplacé aussitôt.
 *
 * Le second, invisible et plus grave : **une seconde diffusion du même titre ne
 * relançait rien**. La destination était construite avec les mêmes paramètres,
 * donc égale à la précédente au sens des data class, donc pas de recomposition
 * et pas de nouvelle recherche. Recaster ne marchait qu'une fois. C'est
 * l'identifiant de lancement porté par [Screen.CastLaunch] qui le règle, et il
 * n'aurait eu aucun sens sur une fiche.
 *
 * ## Ce qu'on montre, et ce qu'on ne montre pas
 *
 * L'affiche et le titre, parce qu'ils répondent à la seule question qu'on se
 * pose devant un écran qui ne joue pas encore : « c'est bien ça qui arrive ? ».
 * Pas de barre de progression chiffrée — la cascade interroge huit catalogues en
 * parallèle et n'a aucune idée du temps qu'elle prendra ; une jauge qui avance
 * au hasard ment plus qu'elle n'informe. Un défilement indéterminé dit ce qu'il
 * y a à dire : ça travaille.
 */
@Composable
fun CastLaunchScreen(
    launch: Screen.CastLaunch,
    onPlay: (Screen.Player) -> Unit,
    onGiveUp: () -> Unit,
    viewModel: DetailsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolved by viewModel.resolved.collectAsStateWithLifecycle()

    // Clé sur l'identifiant de lancement : c'est lui qui distingue deux
    // diffusions du même épisode, et donc ce qui fait repartir la recherche.
    LaunchedEffect(launch.launchId) {
        viewModel.start(launch.tmdbId, launch.isTv, launch.season, launch.episode)
    }

    // La recherche ne peut démarrer qu'une fois la fiche chargée : elle a besoin
    // du titre tel que les catalogues le connaissent.
    LaunchedEffect(launch.launchId, state) {
        when (state) {
            is DetailsState.Movie -> viewModel.quickPlayMovie()
            is DetailsState.Tv -> {
                viewModel.selectSeason(launch.season)
                viewModel.quickPlayEpisode(launch.season, launch.episode)
            }
            is DetailsState.Error -> onGiveUp()
            else -> Unit
        }
    }

    LaunchedEffect(resolved) {
        val stream = resolved ?: return@LaunchedEffect
        if (stream.url.isNotBlank()) {
            onPlay(
                Screen.Player(
                    streamUrl = stream.url,
                    headers = stream.headers,
                    mediaKey = viewModel.playbackKey,
                    subtitles = stream.subtitleUrls,
                    title = viewModel.playbackTitle,
                    subtitle = viewModel.playbackSubtitle,
                    nextSeason = viewModel.playbackNext?.first ?: 0,
                    nextEpisode = viewModel.playbackNext?.second ?: 0,
                    posterUrl = viewModel.playbackPoster,
                    expectedMinutes = viewModel.playbackMinutes ?: 0,
                    sourceUrl = viewModel.playingLink?.url.orEmpty(),
                    hoster = viewModel.playingLink?.hoster.orEmpty(),
                    language = viewModel.playingLink?.language.orEmpty(),
                    alternatives = viewModel.playbackAlternatives(),
                    // Le point de départ vient du téléphone, pas du magasin de
                    // ce téléviseur — qui n'a aucune raison de connaître un
                    // épisode commencé ailleurs, et n'a parfois pas le droit de
                    // s'en souvenir.
                    startAtMs = launch.positionMs,
                ),
            )
        }
        viewModel.consumeResolved()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF080808)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            val pulse by rememberInfiniteTransition(label = "cast").animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
                label = "castAlpha",
            )
            if (launch.artwork.isNotBlank()) {
                MoovieAsyncImage(
                    model = launch.artwork,
                    contentDescription = launch.title,
                    modifier = Modifier
                        .width(220.dp)
                        .height(330.dp)
                        .clip(MoovieShape)
                        .alpha(pulse),
                )
            }
            Text(
                launch.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            if (launch.subtitle.isNotBlank()) {
                Text(
                    launch.subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFBBBBBB),
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                stringResource(Res.string.cast_launching),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9A9A9A),
            )
            LinearProgressIndicator(
                color = MOOVIE_ACCENT,
                trackColor = Color(0xFF2A2A2A),
                modifier = Modifier.width(220.dp).padding(top = 4.dp),
            )
        }
    }
}
