package fr.moovie.tv.data.search

import fr.moovie.tv.data.tmdb.TmdbItem

/**
 * Critère de tri d'une liste de résultats.
 *
 * [RELEVANCE] n'a pas d'équivalent local : c'est l'ordre que TMDB rend, et le
 * seul qui sache qu'on a tapé « matrix ». On ne le recalcule donc jamais, on se
 * contente de ne pas y toucher — d'où sa présence ici, comme choix explicite
 * plutôt que comme absence de tri.
 */
enum class SortBy { RELEVANCE, POPULARITY, RATING, YEAR, TITLE }

/** Ce qu'on veut voir : les deux, ou l'un des deux. */
enum class MediaFilter { ALL, MOVIE, TV }

/**
 * Filtres et tri de la page de recherche, conservés d'une session à l'autre.
 *
 * ### Pourquoi deux chemins derrière un seul modèle
 *
 * TMDB sépare nettement les deux usages, et aucun des deux ne fait le travail
 * de l'autre :
 *
 * - `search/multi` prend un texte et rend des résultats classés par
 *   pertinence. Il n'accepte **que** `query`, `include_adult`, `language` et
 *   `page` : ni tri, ni année, ni note.
 * - `discover` accepte tri, plages d'années, note minimale et genres, mais
 *   **aucun texte libre**.
 *
 * Le même jeu de filtres sert donc les deux : avec une requête il s'applique
 * aux résultats rapportés, sans requête il part dans l'URL de `discover` et le
 * service fait le travail. `includeAdult` est le seul qui parte au service dans
 * les deux cas.
 */
data class SearchFilters(
    val sortBy: SortBy = SortBy.RELEVANCE,
    /** Faux = décroissant, qui est l'attente par défaut sur une note ou une date. */
    val ascending: Boolean = false,
    val media: MediaFilter = MediaFilter.ALL,
    /** Note minimale sur 10, 0 = pas de plancher. */
    val minRating: Double = 0.0,
    /** Bornes d'année, nulles quand elles ne sont pas posées. */
    val minYear: Int? = null,
    val maxYear: Int? = null,
    val includeAdult: Boolean = false,
) {
    /** Vrai dès qu'un critère s'écarte du réglage d'origine. */
    val isActive: Boolean
        get() = this != DEFAULT

    /** Nombre de critères posés, pour la pastille du bouton de filtres. */
    val activeCount: Int
        get() = listOf(
            sortBy != DEFAULT.sortBy || ascending != DEFAULT.ascending,
            media != DEFAULT.media,
            minRating != DEFAULT.minRating,
            minYear != null || maxYear != null,
            includeAdult != DEFAULT.includeAdult,
        ).count { it }

    /**
     * Valeur de `sort_by` pour `discover`, ou null quand le tri est la
     * pertinence — que `discover` ne connaît pas. Il retombe alors sur la
     * popularité décroissante, son propre défaut et le classement le plus
     * proche de « ce qui compte » sans requête pour en juger.
     */
    fun discoverSort(): String? = when (sortBy) {
        SortBy.RELEVANCE -> null
        SortBy.POPULARITY -> "popularity"
        SortBy.RATING -> "vote_average"
        SortBy.YEAR -> "primary_release_date"
        SortBy.TITLE -> "title"
    }?.plus(if (ascending) ".asc" else ".desc")

    companion object {
        val DEFAULT = SearchFilters()
    }
}

/**
 * Applique les filtres à une liste déjà rapportée.
 *
 * Utilisé sur le chemin **texte**, où le service ne sait rien faire de tout
 * ceci. Fonction pure et séparée : c'est la partie qui se teste, et elle décide
 * de ce que l'utilisateur voit.
 */
fun List<TmdbItem>.applyFilters(filters: SearchFilters): List<TmdbItem> {
    val kept = filter { item ->
        val mediaOk = when (filters.media) {
            MediaFilter.ALL -> true
            MediaFilter.MOVIE -> !item.isTv
            MediaFilter.TV -> item.isTv
        }
        // Une note de 0 veut dire « personne n'a voté », pas « mauvais ». Poser
        // un plancher écarte donc aussi les titres sans note, ce qui est bien
        // ce qu'on demande en exigeant une note minimale.
        val ratingOk = item.voteAverage >= filters.minRating
        val year = item.year?.toIntOrNull()
        // Un titre sans date passe les bornes : l'écarter ferait disparaître les
        // sorties annoncées dès qu'on pose une année de début.
        val yearOk = year == null ||
            (filters.minYear?.let { year >= it } ?: true) &&
            (filters.maxYear?.let { year <= it } ?: true)
        mediaOk && ratingOk && yearOk
    }
    return kept.sortedWith(filters.comparator())
}

/**
 * Ordre demandé.
 *
 * Les valeurs absentes vont **toujours en dernier**, quel que soit le sens :
 * un film sans année n'est ni le plus ancien ni le plus récent, et le voir
 * ouvrir un classement par date se lit comme un bug.
 */
private fun SearchFilters.comparator(): Comparator<TmdbItem> {
    if (sortBy == SortBy.RELEVANCE) return Comparator { _, _ -> 0 }
    val base: Comparator<TmdbItem> = when (sortBy) {
        SortBy.POPULARITY, SortBy.RELEVANCE -> compareBy { it.popularity }
        SortBy.RATING -> compareBy { it.voteAverage }
        SortBy.YEAR -> compareBy { it.year?.toIntOrNull() ?: Int.MIN_VALUE }
        SortBy.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }
    }
    val directed = if (ascending) base else base.reversed()
    return compareBy<TmdbItem> { if (missingFor(it)) 1 else 0 }.then(directed)
}

/**
 * La valeur triée manque-t-elle sur ce titre ?
 *
 * Une note l'est aussi quand **trop peu de gens ont voté**. Sans ce plancher,
 * chercher « matrix » et trier par note remontait un documentaire confidentiel
 * noté 10 sur trois voix devant le film de 1999 : mathématiquement exact,
 * inutilisable à l'écran. `discover` a `vote_count.gte` pour la même raison ;
 * ici on ne retire rien, on range simplement ces titres après les autres —
 * écarter un résultat qu'on vient de chercher serait pire.
 */
private fun SearchFilters.missingFor(item: TmdbItem): Boolean = when (sortBy) {
    SortBy.YEAR -> item.year?.toIntOrNull() == null
    SortBy.RATING -> item.voteAverage <= 0.0 || item.voteCount < MIN_VOTES
    else -> false
}

/**
 * Votes nécessaires pour qu'une moyenne soit tenue pour une note.
 *
 * Cinquante : au-dessus, une poignée d'avis extrêmes ne déplace plus la
 * moyenne d'un point ; en dessous, c'est le cas courant sur TMDB pour les
 * titres confidentiels, qui sont légion dans une recherche par mot-clé.
 */
private const val MIN_VOTES = 50
