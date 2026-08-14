package fr.moovie.tv.data.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Endpoints TMDB v3 utilisés par la V1. La clé API est passée par requête
 * (paramétrable dans les réglages) — pas de clé en dur dans le binaire.
 */
interface TmdbApi {
    @GET("trending/{media}/week")
    suspend fun trending(
        @Path("media") media: String, // "movie" | "tv" | "all"
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
    ): TmdbPageResult

    /** Titres proches d'un titre donné : la rangée « Parce que tu as regardé… ». */
    @GET("{media}/{id}/recommendations")
    suspend fun recommendations(
        @Path("media") media: String, // "movie" | "tv"
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
    ): TmdbPageResult

    @GET("{media}/top_rated")
    suspend fun topRated(
        @Path("media") media: String, // "movie" | "tv"
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("page") page: Int = 1,
    ): TmdbPageResult

    /**
     * `include_adult` est le **seul** filtre que cet endpoint accepte, avec la
     * langue et la page : ni tri, ni année, ni note. Tout le reste se fait donc
     * sur les résultats rapportés (voir `SearchFilters`).
     */
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbPageResult

    @GET("genre/{media}/list")
    suspend fun genres(
        @Path("media") media: String, // "movie" | "tv"
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
    ): GenreListResult

    /** Catalogue filtré par genre — la page « explorer » de la recherche. */
    @GET("discover/{media}")
    suspend fun discover(
        @Path("media") media: String, // "movie" | "tv"
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        @Query("with_genres") genreId: Int,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        /**
         * Plancher de note. **Filtré par le service**, contrairement à la
         * recherche texte où il faut rapporter plusieurs pages et trancher
         * soi-même : `discover` sait le faire, et le fait sur tout le catalogue
         * plutôt que sur les soixante premiers résultats.
         */
        @Query("vote_average.gte") minRating: Double? = null,
        /**
         * Bornes d'année. Deux jeux de paramètres parce que TMDB nomme la date
         * différemment selon le média — un film a une sortie, une série une
         * première diffusion. Ceux qui ne concernent pas le média demandé
         * restent nuls, et Retrofit les omet de l'URL.
         */
        @Query("primary_release_date.gte") movieFrom: String? = null,
        @Query("primary_release_date.lte") movieTo: String? = null,
        @Query("first_air_date.gte") tvFrom: String? = null,
        @Query("first_air_date.lte") tvTo: String? = null,
    ): TmdbPageResult

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
    @GET("discover/{media}")
    suspend fun discoverMood(
        @Path("media") media: String, // "movie" | "tv"
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        /** Genres joints par `|` (OU). Vide = tout le catalogue. */
        @Query("with_genres") genres: String? = null,
        @Query("sort_by") sortBy: String = "vote_average.desc",
        @Query("page") page: Int = 1,
        @Query("vote_average.gte") minRating: Double? = null,
        @Query("vote_count.gte") minVotes: Int? = null,
        @Query("vote_count.lte") maxVotes: Int? = null,
        @Query("with_runtime.lte") maxRuntime: Int? = null,
        @Query("with_runtime.gte") minRuntime: Int? = null,
        @Query("primary_release_date.lte") movieBefore: String? = null,
        @Query("first_air_date.lte") tvBefore: String? = null,
        @Query("include_adult") includeAdult: Boolean = false,
    ): TmdbPageResult

    /**
     * Une collection TMDB : la saga d'un film, ses épisodes cinéma dans l'ordre.
     *
     * Aucune donnée à entretenir de notre côté — `belongs_to_collection` arrive
     * déjà dans la fiche d'un film, il ne restait qu'à le lire.
     */
    @GET("collection/{id}")
    suspend fun collection(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
    ): CollectionDetails

    /**
     * `append_to_response` porte maintenant `videos` : les bandes-annonces
     * arrivent **dans la réponse de la fiche**, sans requête supplémentaire.
     * TMDB les filtre alors sur `language`, d'où le repli de [videos] quand la
     * langue de l'utilisateur n'en déclare aucune — cas courant, la plupart des
     * titres n'ont qu'une bande-annonce en anglais.
     */
    @GET("movie/{id}")
    suspend fun movieDetails(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        // `release_dates` porte la classification par pays (-12, -16) : elle
        // n'existe nulle part ailleurs dans la fiche.
        @Query("append_to_response") append: String = "credits,videos,release_dates",
    ): MovieDetails

    @GET("tv/{id}")
    suspend fun tvDetails(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
        /** `content_ratings` est l'équivalent séries de `release_dates`. */
        @Query("append_to_response") append: String = "credits,videos,content_ratings",
    ): TvDetails

    /** Vidéos d'un titre dans une langue donnée. Sert de repli, voir ci-dessus. */
    @GET("{media}/{id}/videos")
    suspend fun videos(
        @Path("media") media: String, // "movie" | "tv"
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
    ): VideoList

    /**
     * Filmographie d'une personne, films **et** séries en une requête.
     *
     * `combined_credits` plutôt que `movie_credits` + `tv_credits` : un acteur
     * partage rarement sa carrière entre les deux de façon nette, et deux appels
     * auraient obligé à fusionner et retrier deux listes pour un résultat
     * identique. Les entrées portent déjà `media_type`, ce dont [TmdbItem] sait
     * déduire film ou série.
     */
    @GET("person/{id}/combined_credits")
    suspend fun personCredits(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
    ): PersonCredits

    @GET("tv/{id}/season/{season}")
    suspend fun season(
        @Path("id") id: Int,
        @Path("season") season: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String,
    ): SeasonDetails

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
    }
}
