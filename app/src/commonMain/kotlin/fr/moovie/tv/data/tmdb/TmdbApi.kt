package fr.moovie.tv.data.tmdb

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

/**
 * Endpoints TMDB v3 utilisés par la V1. La clé API est passée par requête
 * (paramétrable dans les réglages) — pas de clé en dur dans le binaire.
 *
 * Était une interface Retrofit annotée ; Retrofit repose sur `java.lang.reflect.Proxy`
 * et ne peut donc pas exister en Kotlin/Native. Le remplacement est une classe
 * ordinaire au-dessus de Ktor, **aux mêmes signatures** : `TmdbRepository` et
 * tout ce qui est au-dessus n'ont pas eu à changer d'une ligne.
 *
 * Le comportement des paramètres nuls est celui de Retrofit : `parameter()`
 * ignore une valeur nulle, elle n'apparaît donc pas dans l'URL. C'est ce dont
 * dépendent les bornes d'année de [discover], dont seule la moitié concerne le
 * média demandé.
 */
class TmdbApi(private val client: HttpClient) {

    suspend fun trending(
        media: String, // "movie" | "tv" | "all"
        apiKey: String,
        language: String,
    ): TmdbPageResult = client.get("${BASE_URL}trending/$media/week") {
        parameter("api_key", apiKey)
        parameter("language", language)
    }.body()

    /** Titres proches d'un titre donné : la rangée « Parce que tu as regardé… ». */
    suspend fun recommendations(
        media: String, // "movie" | "tv"
        id: Int,
        apiKey: String,
        language: String,
    ): TmdbPageResult = client.get("${BASE_URL}$media/$id/recommendations") {
        parameter("api_key", apiKey)
        parameter("language", language)
    }.body()

    suspend fun topRated(
        media: String, // "movie" | "tv"
        apiKey: String,
        language: String,
        page: Int = 1,
    ): TmdbPageResult = client.get("${BASE_URL}$media/top_rated") {
        parameter("api_key", apiKey)
        parameter("language", language)
        parameter("page", page)
    }.body()

    /**
     * `include_adult` est le **seul** filtre que cet endpoint accepte, avec la
     * langue et la page : ni tri, ni année, ni note. Tout le reste se fait donc
     * sur les résultats rapportés (voir `SearchFilters`).
     */
    suspend fun searchMulti(
        query: String,
        apiKey: String,
        language: String,
        page: Int = 1,
        includeAdult: Boolean = false,
    ): TmdbPageResult = client.get("${BASE_URL}search/multi") {
        parameter("query", query)
        parameter("api_key", apiKey)
        parameter("language", language)
        parameter("page", page)
        parameter("include_adult", includeAdult)
    }.body()

    suspend fun genres(
        media: String, // "movie" | "tv"
        apiKey: String,
        language: String,
    ): GenreListResult = client.get("${BASE_URL}genre/$media/list") {
        parameter("api_key", apiKey)
        parameter("language", language)
    }.body()

    /** Catalogue filtré par genre — la page « explorer » de la recherche. */
    suspend fun discover(
        media: String, // "movie" | "tv"
        apiKey: String,
        language: String,
        genreId: Int,
        sortBy: String = "popularity.desc",
        page: Int = 1,
        /**
         * Plancher de note. **Filtré par le service**, contrairement à la
         * recherche texte où il faut rapporter plusieurs pages et trancher
         * soi-même : `discover` sait le faire, et le fait sur tout le catalogue
         * plutôt que sur les soixante premiers résultats.
         */
        minRating: Double? = null,
        /**
         * Bornes d'année. Deux jeux de paramètres parce que TMDB nomme la date
         * différemment selon le média — un film a une sortie, une série une
         * première diffusion. Ceux qui ne concernent pas le média demandé
         * restent nuls, et sont alors omis de l'URL.
         */
        movieFrom: String? = null,
        movieTo: String? = null,
        tvFrom: String? = null,
        tvTo: String? = null,
    ): TmdbPageResult = client.get("${BASE_URL}discover/$media") {
        parameter("api_key", apiKey)
        parameter("language", language)
        parameter("with_genres", genreId)
        parameter("sort_by", sortBy)
        parameter("page", page)
        parameter("vote_average.gte", minRating)
        parameter("primary_release_date.gte", movieFrom)
        parameter("primary_release_date.lte", movieTo)
        parameter("first_air_date.gte", tvFrom)
        parameter("first_air_date.lte", tvTo)
    }.body()

    /**
     * Découverte à plusieurs genres, pour la page Découverte.
     *
     * Distinct de [discover], qui sert le catalogue et n'accepte qu'un genre.
     * Deux différences décident du résultat :
     *
     * - **le tube et non la virgule.** Chez TMDB, `28,53` veut dire « action
     *   ET thriller » — une intersection qui ne rend presque rien dès trois
     *   genres, et qui oblige à enchaîner des replis jusqu'à servir autre chose
     *   que ce qui était demandé. `28|53` veut dire « ou », et c'est ce qu'on
     *   entend quand on coche plusieurs humeurs.
     * - **une bande de votes, pas un plancher.** [maxVotes] est le paramètre
     *   qui sépare une découverte d'un carrousel : au-delà de quelques milliers
     *   de votes, on ne propose plus que ce que tout le monde a déjà vu.
     */
    suspend fun discoverMood(
        media: String, // "movie" | "tv"
        apiKey: String,
        language: String,
        /** Genres joints par `|` (OU). Vide = tout le catalogue. */
        genres: String? = null,
        /** Genres écartés, joints par `,`. Ici la virgule est bien un ET-NON. */
        sansGenres: String? = null,
        sortBy: String = "vote_average.desc",
        page: Int = 1,
        minRating: Double? = null,
        minVotes: Int? = null,
        maxVotes: Int? = null,
        maxRuntime: Int? = null,
        minRuntime: Int? = null,
        movieBefore: String? = null,
        tvBefore: String? = null,
        includeAdult: Boolean = false,
    ): TmdbPageResult = client.get("${BASE_URL}discover/$media") {
        parameter("api_key", apiKey)
        parameter("language", language)
        parameter("with_genres", genres)
        parameter("without_genres", sansGenres)
        parameter("sort_by", sortBy)
        parameter("page", page)
        parameter("vote_average.gte", minRating)
        parameter("vote_count.gte", minVotes)
        parameter("vote_count.lte", maxVotes)
        parameter("with_runtime.lte", maxRuntime)
        parameter("with_runtime.gte", minRuntime)
        parameter("primary_release_date.lte", movieBefore)
        parameter("first_air_date.lte", tvBefore)
        parameter("include_adult", includeAdult)
    }.body()

    /**
     * Une collection TMDB : la saga d'un film, ses épisodes cinéma dans l'ordre.
     *
     * Aucune donnée à entretenir de notre côté — `belongs_to_collection` arrive
     * déjà dans la fiche d'un film, il ne restait qu'à le lire.
     */
    suspend fun collection(
        id: Int,
        apiKey: String,
        language: String,
    ): CollectionDetails = client.get("${BASE_URL}collection/$id") {
        parameter("api_key", apiKey)
        parameter("language", language)
    }.body()

    /**
     * `append_to_response` porte maintenant `videos` : les bandes-annonces
     * arrivent **dans la réponse de la fiche**, sans requête supplémentaire.
     * TMDB les filtre alors sur `language`, d'où le repli de [videos] quand la
     * langue de l'utilisateur n'en déclare aucune — cas courant, la plupart des
     * titres n'ont qu'une bande-annonce en anglais.
     */
    suspend fun movieDetails(
        id: Int,
        apiKey: String,
        language: String,
        // `release_dates` porte la classification par pays (-12, -16) : elle
        // n'existe nulle part ailleurs dans la fiche.
        append: String = "credits,videos,release_dates",
    ): MovieDetails = client.get("${BASE_URL}movie/$id") {
        parameter("api_key", apiKey)
        parameter("language", language)
        parameter("append_to_response", append)
    }.body()

    suspend fun tvDetails(
        id: Int,
        apiKey: String,
        language: String,
        /** `content_ratings` est l'équivalent séries de `release_dates`. */
        append: String = "credits,videos,content_ratings",
    ): TvDetails = client.get("${BASE_URL}tv/$id") {
        parameter("api_key", apiKey)
        parameter("language", language)
        parameter("append_to_response", append)
    }.body()

    /** Vidéos d'un titre dans une langue donnée. Sert de repli, voir ci-dessus. */
    suspend fun videos(
        media: String, // "movie" | "tv"
        id: Int,
        apiKey: String,
        language: String,
    ): VideoList = client.get("${BASE_URL}$media/$id/videos") {
        parameter("api_key", apiKey)
        parameter("language", language)
    }.body()

    /**
     * Filmographie d'une personne, films **et** séries en une requête.
     *
     * `combined_credits` plutôt que `movie_credits` + `tv_credits` : un acteur
     * partage rarement sa carrière entre les deux de façon nette, et deux appels
     * auraient obligé à fusionner et retrier deux listes pour un résultat
     * identique. Les entrées portent déjà `media_type`, ce dont [TmdbItem] sait
     * déduire film ou série.
     */
    suspend fun personCredits(
        id: Int,
        apiKey: String,
        language: String,
    ): PersonCredits = client.get("${BASE_URL}person/$id/combined_credits") {
        parameter("api_key", apiKey)
        parameter("language", language)
    }.body()

    suspend fun season(
        id: Int,
        season: Int,
        apiKey: String,
        language: String,
    ): SeasonDetails = client.get("${BASE_URL}tv/$id/season/$season") {
        parameter("api_key", apiKey)
        parameter("language", language)
    }.body()

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
    }
}
