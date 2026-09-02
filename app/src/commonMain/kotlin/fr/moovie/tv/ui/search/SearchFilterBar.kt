package fr.moovie.tv.ui.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import fr.moovie.tv.ui.components.BarreDefilante
import fr.moovie.tv.resources.search_filter_sort
import fr.moovie.tv.resources.search_filter_media
import fr.moovie.tv.resources.search_filter_rating
import fr.moovie.tv.resources.search_filter_period
import fr.moovie.tv.ui.components.MoovieSelect
import fr.moovie.tv.ui.components.MoovieButton
import org.jetbrains.compose.resources.stringResource
import fr.moovie.tv.ui.theme.MOOVIE_WARN

/**
 * Barre de tri et de filtres de la recherche.
 *
 * ## Des menus, et non des boutons qui cyclent
 *
 * Chaque critère a longtemps été un bouton qui passait à la valeur suivante :
 * un seul appui, la valeur courante pour libellé, rien à ouvrir. C'était vrai,
 * et c'était insuffisant. Un bouton qui cycle **ne montre jamais ses options** :
 * on apprend qu'il existe une note minimale de 7 en tombant dessus, après avoir
 * appuyé trois fois sur un bouton qui disait « Toute note ». On ne sait pas
 * combien de valeurs il reste, ni comment revenir à celle qu'on vient de
 * dépasser autrement qu'en faisant le tour. Et rien ne dit à quoi sert un
 * bouton nommé « Popularité » tant qu'on ne l'a pas essayé.
 *
 * Un menu répond aux trois : la flèche annonce qu'il y a un choix, la liste le
 * montre en entier, et son titre nomme le critère. Le coût — un geste de plus —
 * se paie une fois, là où le cycle se paie à chaque valeur dépassée.
 *
 * Restent en bascule les deux critères **binaires**, ordre et contenu adulte :
 * ouvrir une liste de deux entrées pour choisir celle qui n'est pas affichée
 * serait une cérémonie pour rien.
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
    /**
     * Faux sur le catalogue : le type est **déjà** choisi là-bas, en entrant
     * dans un genre de la liste « Films » ou de la liste « Séries ». Un bouton
     * qui permettrait de le contredire ne pourrait que produire une grille vide.
     */
    showMedia: Boolean = true,
    /**
     * Faux sur le catalogue : la pertinence n'a de sens que face à un texte à
     * comparer. Sans requête, `discover` retombe sur la popularité — le bouton
     * annoncerait donc un tri que le service n'applique pas.
     */
    allowRelevance: Boolean = true,
) {
    // Quatre à six filtres : ils tiennent sur un téléviseur et débordent en
    // portrait, où le dernier sortait de l'écran sans que rien ne le dise. La
    // barre porte désormais ses propres repères — voir [BarreDefilante], qui
    // gère aussi la marge de page dans le défilement (le rembourrage extérieur
    // rognait le bouton agrandi au focus).
    BarreDefilante(modifier = modifier, marge = hPad) {
        // Le critère est nommé dans le libellé, pas seulement dans le titre du
        // menu : « Popularité » seul ne dit pas qu'il s'agit d'un tri, et c'est
        // la première question qu'on se pose devant la barre.
        MoovieSelect(
            title = stringResource(Res.string.search_filter_sort),
            options = SortBy.entries.filter { allowRelevance || it != SortBy.RELEVANCE },
            selected = filters.sortBy,
            label = { sortLabel(it) },
            libelleBouton = { "${stringResource(Res.string.search_filter_sort)} · ${sortLabel(it)}" },
            onSelect = { onChange(filters.copy(sortBy = it)) },
            actif = filters.sortBy != SearchFilters.DEFAULT.sortBy,
        )

        // Binaire : une bascule, pas une liste de deux entrées.
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

        if (showMedia) {
            MoovieSelect(
                title = stringResource(Res.string.search_filter_media),
                options = MediaFilter.entries,
                selected = filters.media,
                label = { mediaLabel(it) },
                onSelect = { onChange(filters.copy(media = it)) },
                actif = filters.media != SearchFilters.DEFAULT.media,
            )
        }

        MoovieSelect(
            title = stringResource(Res.string.search_filter_rating),
            options = NOTES,
            selected = NOTES.lastOrNull { it <= filters.minRating } ?: 0.0,
            label = { ratingLabel(it) },
            onSelect = { onChange(filters.copy(minRating = it)) },
            actif = filters.minRating > 0.0,
        )

        MoovieSelect(
            title = stringResource(Res.string.search_filter_period),
            options = EPOQUES,
            selected = filters.decade(),
            label = { decadeOptionLabel(it) },
            onSelect = { onChange(filters.withDecade(it)) },
            actif = filters.minYear != null || filters.maxYear != null,
        )

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
                    color = MOOVIE_WARN,
                )
            }
        }
    }
}

@Composable
private fun mediaLabel(media: MediaFilter): String = stringResource(
    when (media) {
        MediaFilter.ALL -> Res.string.search_media_all
        MediaFilter.MOVIE -> Res.string.search_media_movies
        MediaFilter.TV -> Res.string.search_media_shows
    },
)

/** Paliers de note : entiers, et assez espacés pour tenir dans une liste courte. */
private val NOTES = listOf(0.0, 6.0, 7.0, 8.0)

@Composable
private fun ratingLabel(note: Double): String = if (note <= 0.0) {
    stringResource(Res.string.search_rating_any)
} else {
    // Sans décimale : les paliers sont entiers, et « 7,0 » laisserait croire
    // qu'on peut demander 7,5.
    stringResource(Res.string.search_rating_min, note.toInt().toString())
}


@Composable
private fun decadeOptionLabel(decade: Int?): String = when (decade) {
    null -> stringResource(Res.string.search_decade_any)
    BEFORE -> stringResource(Res.string.search_decade_before, DECADES.last())
    else -> stringResource(Res.string.search_decade, decade)
}

/** L'époque courante, sous la forme d'une des entrées de [EPOQUES]. */
private fun SearchFilters.decade(): Int? = when {
    minYear != null -> minYear
    maxYear != null -> BEFORE
    else -> null
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

/** Décennies proposées, de la plus récente à la plus ancienne. */
private val DECADES = listOf(2020, 2010, 2000, 1990, 1980)

/** Repère du palier « avant la plus ancienne décennie ». */
private const val BEFORE = -1

/** Époques proposées : « toute époque », les décennies, puis « avant 1980 ». */
private val EPOQUES: List<Int?> = listOf(null) + DECADES + listOf(BEFORE)

private fun SearchFilters.withDecade(decade: Int?): SearchFilters = when (decade) {
    null -> copy(minYear = null, maxYear = null)
    BEFORE -> copy(minYear = null, maxYear = DECADES.last() - 1)
    else -> copy(minYear = decade, maxYear = decade + 9)
}
