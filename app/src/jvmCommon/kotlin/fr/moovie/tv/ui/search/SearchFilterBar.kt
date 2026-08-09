package fr.moovie.tv.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.search.MediaFilter
import fr.moovie.tv.data.search.SearchFilters
import fr.moovie.tv.data.search.SortBy
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.search_adult_hidden
import fr.moovie.tv.resources.search_adult_shown
import fr.moovie.tv.resources.search_decade
import fr.moovie.tv.resources.search_decade_any
import fr.moovie.tv.resources.search_decade_before
import fr.moovie.tv.resources.search_filters_reset
import fr.moovie.tv.resources.search_media_all
import fr.moovie.tv.resources.search_media_movies
import fr.moovie.tv.resources.search_media_shows
import fr.moovie.tv.resources.search_rating_any
import fr.moovie.tv.resources.search_rating_min
import fr.moovie.tv.resources.search_sort_asc
import fr.moovie.tv.resources.search_sort_desc
import fr.moovie.tv.resources.search_sort_popularity
import fr.moovie.tv.resources.search_sort_rating
import fr.moovie.tv.resources.search_sort_relevance
import fr.moovie.tv.resources.search_sort_title
import fr.moovie.tv.resources.search_sort_year
import fr.moovie.tv.ui.components.MoovieButton
import org.jetbrains.compose.resources.stringResource

/**
 * Barre de tri et de filtres de la recherche.
 *
 * **Des boutons qui font défiler leurs valeurs, pas des menus.** Un menu
 * déroulant demande d'ouvrir, de descendre dans une liste et de valider : trois
 * gestes à la télécommande, et un survol de plus au doigt. Ici chaque critère
 * est un bouton qui passe à la valeur suivante, donc un seul appui, et la
 * valeur courante est son libellé — rien à ouvrir pour savoir où l'on en est.
 * C'est aussi ce qui la rend franchissable d'un coup de flèche vers les
 * résultats, au lieu de piéger le focus dans une liste.
 *
 * Le sens de tri disparaît sur la pertinence : elle n'a pas d'inverse qui veuille
 * dire quelque chose, et un bouton qui ne fait rien est pire qu'un bouton absent.
 */
@Composable
fun SearchFilterBar(
    filters: SearchFilters,
    onChange: (SearchFilters) -> Unit,
    hPad: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Marges dans le contenu et non autour : la barre défile, et une marge
        // extérieure rognerait le bouton agrandi au focus (voir Gotchas).
    ) {
        Row(
            modifier = Modifier.padding(PaddingValues(horizontal = hPad)),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoovieButton(
                onClick = { onChange(filters.copy(sortBy = filters.sortBy.next())) },
                selected = filters.sortBy != SearchFilters.DEFAULT.sortBy,
            ) {
                Text(sortLabel(filters.sortBy), style = MaterialTheme.typography.labelMedium)
            }

            if (filters.sortBy != SortBy.RELEVANCE) {
                MoovieButton(
                    onClick = { onChange(filters.copy(ascending = !filters.ascending)) },
                ) {
                    Text(
                        stringResource(
                            if (filters.ascending) Res.string.search_sort_asc
                            else Res.string.search_sort_desc,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            MoovieButton(
                onClick = { onChange(filters.copy(media = filters.media.next())) },
                selected = filters.media != SearchFilters.DEFAULT.media,
            ) {
                Text(
                    stringResource(
                        when (filters.media) {
                            MediaFilter.ALL -> Res.string.search_media_all
                            MediaFilter.MOVIE -> Res.string.search_media_movies
                            MediaFilter.TV -> Res.string.search_media_shows
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            MoovieButton(
                onClick = { onChange(filters.copy(minRating = nextRating(filters.minRating))) },
                selected = filters.minRating > 0.0,
            ) {
                Text(
                    if (filters.minRating <= 0.0) {
                        stringResource(Res.string.search_rating_any)
                    } else {
                        // Sans décimale : les paliers sont entiers, et « 7,0 »
                        // laisserait croire qu'on peut demander 7,5.
                        stringResource(Res.string.search_rating_min, filters.minRating.toInt().toString())
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            MoovieButton(
                onClick = { onChange(filters.withDecade(nextDecade(filters))) },
                selected = filters.minYear != null || filters.maxYear != null,
            ) {
                Text(decadeLabel(filters), style = MaterialTheme.typography.labelMedium)
            }

            MoovieButton(
                onClick = { onChange(filters.copy(includeAdult = !filters.includeAdult)) },
                selected = filters.includeAdult,
            ) {
                Text(
                    stringResource(
                        if (filters.includeAdult) Res.string.search_adult_shown
                        else Res.string.search_adult_hidden,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            // Seulement quand il y a quelque chose à effacer : autrement il
            // occupe une place et un cran de focus pour rien.
            if (filters.isActive) {
                MoovieButton(onClick = { onChange(SearchFilters.DEFAULT) }) {
                    Text(
                        stringResource(Res.string.search_filters_reset),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFE0B057),
                    )
                }
            }
        }
    }
}

@Composable
private fun sortLabel(sort: SortBy) = stringResource(
    when (sort) {
        SortBy.RELEVANCE -> Res.string.search_sort_relevance
        SortBy.POPULARITY -> Res.string.search_sort_popularity
        SortBy.RATING -> Res.string.search_sort_rating
        SortBy.YEAR -> Res.string.search_sort_year
        SortBy.TITLE -> Res.string.search_sort_title
    },
)

@Composable
private fun decadeLabel(filters: SearchFilters): String = when {
    filters.minYear == null && filters.maxYear == null ->
        stringResource(Res.string.search_decade_any)
    filters.minYear == null && filters.maxYear != null ->
        stringResource(Res.string.search_decade_before, filters.maxYear!! + 1)
    else -> stringResource(Res.string.search_decade, filters.minYear!!)
}

/** Décennies proposées, de la plus récente à la plus ancienne. */
private val DECADES = listOf(2020, 2010, 2000, 1990, 1980)

/**
 * Décennie suivante du cycle, `null` pour « toute époque ».
 *
 * La dernière entrée devient « avant 1980 » plutôt qu'une décennie de plus :
 * en dessous, le catalogue est trop clairsemé pour que décennie par décennie
 * ait un sens, et le cycle deviendrait interminable à la télécommande.
 */
private fun nextDecade(filters: SearchFilters): Int? {
    val current = filters.minYear
    // « Avant 1980 » : pas de borne basse, une borne haute. C'est la fin du
    // cycle, on repart sur « toute époque ».
    if (current == null && filters.maxYear != null) return null
    if (current == null) return DECADES.first()
    val index = DECADES.indexOf(current)
    return if (index < 0 || index == DECADES.lastIndex) BEFORE else DECADES[index + 1]
}

/** Repère du palier « avant la plus ancienne décennie ». */
private const val BEFORE = -1

private fun SearchFilters.withDecade(decade: Int?): SearchFilters = when (decade) {
    null -> copy(minYear = null, maxYear = null)
    BEFORE -> copy(minYear = null, maxYear = DECADES.last() - 1)
    else -> copy(minYear = decade, maxYear = decade + 9)
}

/** Paliers de note : entiers, et assez espacés pour se parcourir en trois appuis. */
private fun nextRating(current: Double): Double = when {
    current < 6.0 -> 6.0
    current < 7.0 -> 7.0
    current < 8.0 -> 8.0
    else -> 0.0
}

private fun SortBy.next(): SortBy = SortBy.entries[(ordinal + 1) % SortBy.entries.size]

private fun MediaFilter.next(): MediaFilter =
    MediaFilter.entries[(ordinal + 1) % MediaFilter.entries.size]
