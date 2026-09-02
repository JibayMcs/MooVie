package fr.moovie.tv.ui.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.tmdb.TmdbItem
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.search_loading
import fr.moovie.tv.resources.watchlist_added
import fr.moovie.tv.ui.adaptive.useBottomNav
import fr.moovie.tv.ui.components.MoovieAsyncImage
import fr.moovie.tv.ui.components.MoovieCard
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieMarqueeText
import fr.moovie.tv.ui.components.SkeletonGrid
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import fr.moovie.tv.ui.theme.MOOVIE_BG
import fr.moovie.tv.ui.theme.MOOVIE_SCRIM
import fr.moovie.tv.ui.theme.MOOVIE_SURFACE_HIGH
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM
import fr.moovie.tv.ui.theme.margePage
import fr.moovie.tv.ui.components.MooviePageHeader
import fr.moovie.tv.ui.theme.ESPACE_SECTION
import fr.moovie.tv.ui.components.MooviePosterCard

/** Colonnes sur un écran large (TV 960 dp, desktop) — comme la recherche. */
private const val COLUMNS = 6

/** Colonnes en portrait : trois affiches d'environ 140 dp. */
private const val COMPACT_COLUMNS = 3

/**
 * Filmographie d'une personne : ce qu'elle a joué, du plus récent au plus ancien.
 *
 * Ouverte depuis le casting d'une fiche. C'est le chaînon qui manquait au
 * parcours : on reconnaissait un visage au casting sans aucun moyen de savoir
 * où on l'avait vu — il fallait sortir de l'app pour le chercher.
 *
 * Une grille d'affiches, comme le catalogue et la recherche : c'est le même
 * geste — parcourir des titres et en ouvrir un — et il n'y avait aucune raison
 * de lui inventer une présentation à part.
 */
@Composable
fun PersonScreenContent(
    /** Connu de la fiche d'origine : affiché avant même la réponse de TMDB. */
    name: String,
    state: PersonState,
    watched: Set<String>,
    watchlistKeys: Set<String>,
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onBack: () -> Unit = {},
    // Desktop uniquement : sur TV la télécommande a sa touche Retour, et un
    // bouton à l'écran ne ferait que voler le focus à la première affiche.
    showBackButton: Boolean = false,
) {
    val firstCard = remember { FocusRequester() }
    // La grille arrive après la première composition (appel TMDB) : on retente
    // tant qu'elle n'est pas posée, sinon la demande de focus tombe dans le vide
    // et le premier appui du D-pad est perdu.
    LaunchedEffect(state) {
        if (state !is PersonState.Ready) return@LaunchedEffect
        repeat(10) {
            if (runCatching { firstCard.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    val hPad = margePage()

    Box(modifier = Modifier.fillMaxSize().background(MOOVIE_BG)) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = if (useBottomNav) 16.dp else 32.dp)) {
            MooviePageHeader(
                titre = name,
                onBack = onBack.takeIf { showBackButton },
            )
            Spacer(Modifier.height(ESPACE_SECTION))

            when (state) {
                // Grille fantôme au même nombre de colonnes que la vraie : la
                // page ne se réorganise pas quand les affiches arrivent.
                PersonState.Loading -> SkeletonGrid(
                    columns = if (useBottomNav) COMPACT_COLUMNS else COLUMNS,
                    modifier = Modifier.padding(horizontal = hPad),
                )

                is PersonState.Empty -> Text(
                    state.reason,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MOOVIE_TEXT_DIM,
                    modifier = Modifier.padding(horizontal = hPad),
                )

                is PersonState.Ready -> LazyVerticalGrid(
                    columns = GridCells.Fixed(if (useBottomNav) COMPACT_COLUMNS else COLUMNS),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    // Marges intérieures : la grille clippe à ses bords, et les
                    // cartes agrandies au focus ont besoin de cette réserve.
                    contentPadding = PaddingValues(horizontal = hPad, vertical = 12.dp),
                ) {
                    items(state.credits, key = { "${it.id}_${it.isTv}" }) { item ->
                        val key = if (item.isTv) "tv:${item.id}" else "movie:${item.id}"
                        MooviePosterCard(
                            posterUrl = item.posterUrl(),
                            titre = item.displayTitle,
                            note = item.voteAverage,
                            annee = item.year,
                            isWatched = key in watched,
                            inWatchlist = key in watchlistKeys,
                            // Une filmographie est pleine de titres qui se
                            // ressemblent — remakes, suites, séries et films du
                            // même nom. L'année est ce qui les sépare, et au
                            // doigt elle n'apparaîtrait jamais : il n'y a pas de
                            // focus pour la révéler.
                            metaToujours = useBottomNav,
                            onClick = { onOpenTitle(item.id, item.isTv) },
                            modifier = if (item == state.credits.firstOrNull()) {
                                Modifier.focusRequester(firstCard)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
    }
}

