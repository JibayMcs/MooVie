package fr.moovie.tv.data.search

import fr.moovie.tv.data.tmdb.TmdbItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Le tri et les filtres appliqués localement, c'est-à-dire ce que voit qui a
 * tapé une requête : `search/multi` ne sait ni trier ni filtrer, la liste
 * arrive dans l'ordre de pertinence de TMDB et tout se joue ici.
 */
class SearchFiltersTest {

    private fun movie(
        id: Int,
        title: String,
        year: String? = null,
        rating: Double = 0.0,
        popularity: Double = 0.0,
        votes: Int = 500,
    ) = TmdbItem(
        id = id,
        title = title,
        mediaType = "movie",
        releaseDate = year?.let { "$it-01-01" },
        voteAverage = rating,
        popularity = popularity,
        voteCount = votes,
    )

    private fun show(id: Int, name: String, year: String? = null, rating: Double = 0.0) = TmdbItem(
        id = id,
        name = name,
        mediaType = "tv",
        firstAirDate = year?.let { "$it-01-01" },
        voteAverage = rating,
        voteCount = 500,
    )

    private val catalogue = listOf(
        movie(1, "Alpha", "2001", rating = 5.0, popularity = 10.0),
        movie(2, "Bravo", "2020", rating = 8.5, popularity = 1.0),
        show(3, "Charlie", "2010", rating = 7.0),
        movie(4, "Delta", null, rating = 0.0, popularity = 50.0),
    )

    @Test
    fun `la pertinence ne réordonne rien`() {
        val sorted = catalogue.applyFilters(SearchFilters())

        // L'ordre de TMDB est le seul qui sache qu'on a tapé quelque chose :
        // le recalculer localement, c'est le perdre.
        assertEquals(listOf(1, 2, 3, 4), sorted.map { it.id })
    }

    @Test
    fun `le tri par note descend par défaut`() {
        val sorted = catalogue.applyFilters(SearchFilters(sortBy = SortBy.RATING))

        assertEquals(listOf(2, 3, 1), sorted.take(3).map { it.id })
    }

    @Test
    fun `le sens ascendant inverse le classement`() {
        val sorted = catalogue.applyFilters(SearchFilters(sortBy = SortBy.RATING, ascending = true))

        assertEquals(listOf(1, 3, 2), sorted.take(3).map { it.id })
    }

    /**
     * Un titre sans note ni date n'est ni le meilleur ni le pire, ni le plus
     * ancien ni le plus récent. Le voir ouvrir un classement ascendant se lit
     * comme un défaut, alors qu'il n'a simplement rien à comparer.
     */
    @Test
    fun `les valeurs absentes finissent la liste dans les deux sens`() {
        listOf(SortBy.RATING, SortBy.YEAR).forEach { sort ->
            listOf(true, false).forEach { asc ->
                val sorted = catalogue.applyFilters(SearchFilters(sortBy = sort, ascending = asc))
                assertEquals(4, sorted.last().id, "tri=$sort ascendant=$asc")
            }
        }
    }

    @Test
    fun `le tri par titre ignore la casse`() {
        val items = listOf(movie(1, "zulu"), movie(2, "Alpha"), movie(3, "mike"))

        val sorted = items.applyFilters(SearchFilters(sortBy = SortBy.TITLE, ascending = true))

        assertEquals(listOf(2, 3, 1), sorted.map { it.id })
    }

    @Test
    fun `le filtre de type ne garde que ce qui est demandé`() {
        val films = catalogue.applyFilters(SearchFilters(media = MediaFilter.MOVIE))
        val series = catalogue.applyFilters(SearchFilters(media = MediaFilter.TV))

        assertTrue(films.none { it.isTv })
        assertEquals(listOf(3), series.map { it.id })
    }

    @Test
    fun `la note minimale écarte aussi ce qui n'a pas été noté`() {
        val kept = catalogue.applyFilters(SearchFilters(minRating = 6.0))

        assertEquals(listOf(2, 3), kept.map { it.id }.sorted())
    }

    /**
     * Une borne d'année ne doit pas faire disparaître les sorties annoncées,
     * que TMDB rend souvent sans date.
     */
    @Test
    fun `un titre sans date traverse les bornes d'année`() {
        val kept = catalogue.applyFilters(SearchFilters(minYear = 2005, maxYear = 2015))

        assertEquals(listOf(3, 4), kept.map { it.id }.sorted())
    }

    @Test
    fun `les filtres par défaut ne sont pas actifs`() {
        assertTrue(!SearchFilters().isActive)
        assertEquals(0, SearchFilters().activeCount)
        assertEquals(2, SearchFilters(minRating = 7.0, media = MediaFilter.TV).activeCount)
    }

    /**
     * `discover` ne connaît pas la pertinence : sans requête, il n'y a rien
     * dont être pertinent. Il retombe alors sur son propre défaut.
     */
    @Test
    fun `la pertinence n'a pas de tri discover`() {
        assertEquals(null, SearchFilters().discoverSort())
        assertEquals("vote_average.desc", SearchFilters(sortBy = SortBy.RATING).discoverSort())
        assertEquals(
            "primary_release_date.asc",
            SearchFilters(sortBy = SortBy.YEAR, ascending = true).discoverSort(),
        )
    }

    /**
     * Le cas qui a motivé le plancher : cherchant « matrix », un documentaire
     * noté 10 sur trois voix passait devant le film de 1999. On ne l'écarte
     * pas — on vient de le chercher — mais il ne mène plus le classement.
     */
    @Test
    fun `une note portée par trop peu de votes ne mène pas le classement`() {
        val items = listOf(
            movie(1, "Obscur", "2015", rating = 10.0, votes = 3),
            movie(2, "Matrix", "1999", rating = 8.2, votes = 25_000),
        )

        val sorted = items.applyFilters(SearchFilters(sortBy = SortBy.RATING))

        assertEquals(listOf(2, 1), sorted.map { it.id })
    }
}
