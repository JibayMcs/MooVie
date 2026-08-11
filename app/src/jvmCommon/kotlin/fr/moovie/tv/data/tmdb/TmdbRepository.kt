package fr.moovie.tv.data.tmdb

import fr.moovie.tv.data.search.SearchFilters
import fr.moovie.tv.data.search.applyFilters
import fr.moovie.tv.data.store.moovieCacheDir
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Taille max du cache disque des réponses TMDB. */
private const val TMDB_CACHE_BYTES = 20L * 1024 * 1024

/**
 * Durée pendant laquelle une réponse TMDB est resservie sans appel réseau.
 * Le catalogue bouge lentement (tendances hebdomadaires, fiches quasi figées) :
 * quelques heures suffisent pour rendre instantané le retour sur une fiche.
 */
private const val TMDB_TTL_SECONDS = 6 * 60 * 60

/** Âge max toléré quand le réseau est indisponible (mieux qu'un écran d'erreur). */
private const val TMDB_STALE_SECONDS = 7 * 24 * 60 * 60

/**
 * Client HTTP partagé par toutes les instances du repository.
 *
 * Il **doit** être unique : un `Cache` OkHttp verrouille son répertoire, et deux
 * instances concurrentes sur le même dossier le corrompent. Or ce repository est
 * construit à la demande un peu partout (accueil, recherche, fiche).
 *
 * Il n'utilise **pas** le DNS-over-HTTPS d'AppDns : TMDB n'est pas bloqué par les
 * FAI, seuls les domaines des sources le sont.
 */
private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .cache(Cache(moovieCacheDir("tmdb-http"), TMDB_CACHE_BYTES))
        // TMDB renvoie des en-têtes de cache très courts : on impose notre durée.
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).newBuilder()
                .header("Cache-Control", "public, max-age=$TMDB_TTL_SECONDS")
                .removeHeader("Pragma")
                .build()
        }
        // Repli hors ligne : si la requête réseau échoue, on ressert la réponse
        // en cache même périmée plutôt que de casser l'écran.
        .addInterceptor { chain ->
            try {
                chain.proceed(chain.request())
            } catch (io: IOException) {
                val stale = chain.request().newBuilder()
                    .cacheControl(
                        CacheControl.Builder()
                            .onlyIfCached()
                            .maxStale(TMDB_STALE_SECONDS, TimeUnit.SECONDS)
                            .build(),
                    )
                    .build()
                chain.proceed(stale)
            }
        }
        .build()
}

/**
 * Accès TMDB. La clé API et la langue viennent des réglages (fournies par
 * l'appelant), pour rester configurables et hors du binaire.
 */
class TmdbRepository(
    private val language: String = "fr-FR",
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val api: TmdbApi = Retrofit.Builder()
        .baseUrl(TmdbApi.BASE_URL)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(TmdbApi::class.java)

    suspend fun trendingMovies(apiKey: String): List<TmdbItem> =
        api.trending("movie", apiKey, language).results

    suspend fun trendingTv(apiKey: String): List<TmdbItem> =
        api.trending("tv", apiKey, language).results

    suspend fun topRatedMovies(apiKey: String): List<TmdbItem> =
        api.topRated("movie", apiKey, language).results

    /**
     * Recherche texte, filtrée et triée localement.
     *
     * [pages] existe parce que trier la seule première page ne trie pas grand
     * chose : « les mieux notés » deviendrait « les mieux notés parmi les vingt
     * premiers résultats de pertinence », ce qui n'est pas ce qu'on demande.
     * On rapporte donc plusieurs pages avant de classer, en s'arrêtant dès que
     * TMDB n'en a plus. Une page de moins qu'annoncé n'est pas une erreur : le
     * service coupe au-delà de 500, et une requête large les atteint.
     */
    suspend fun search(
        apiKey: String,
        query: String,
        filters: SearchFilters = SearchFilters.DEFAULT,
        pages: Int = 1,
    ): List<TmdbItem> {
        val collected = mutableListOf<TmdbItem>()
        var totalPages = 1
        for (page in 1..pages.coerceAtLeast(1)) {
            if (page > totalPages) break
            val result = runCatching {
                api.searchMulti(query, apiKey, language, page, filters.includeAdult)
            }.getOrNull() ?: break
            totalPages = result.totalPages
            collected += result.results
        }
        return collected
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            // TMDB rend parfois le même titre sur deux pages quand le
            // classement bouge entre deux requêtes. Sans ça, la grille affiche
            // deux fois la même affiche.
            .distinctBy { "${it.mediaType}:${it.id}" }
            .applyFilters(filters)
    }

    /**
     * Titres recommandés à partir d'un titre déjà vu.
     *
     * TMDB range parfois des entrées sans affiche : elles feraient un trou gris
     * dans la rangée, on les écarte ici plutôt que dans l'UI.
     */
    suspend fun recommendations(apiKey: String, isTv: Boolean, id: Int): List<TmdbItem> =
        api.recommendations(if (isTv) "tv" else "movie", id, apiKey, language)
            .results
            .filter { it.posterUrl() != null }

    /**
     * Vérifie qu'une clé est acceptée par TMDB.
     *
     * On interroge la liste des genres : c'est la plus petite réponse du service,
     * et une clé refusée y répond 401 comme partout ailleurs.
     *
     * **Refusée et injoignable sont deux verdicts distincts**, et les confondre
     * ferait accuser la clé à chaque coupure réseau — sur un téléviseur qui vient
     * d'être installé, c'est le scénario le plus probable des deux. Seul un 401
     * met en cause la clé.
     */
    suspend fun validateKey(apiKey: String): KeyCheck = runCatching {
        api.genres("movie", apiKey, language)
    }.fold(
        onSuccess = { KeyCheck.VALID },
        onFailure = { error ->
            if (error is HttpException && error.code() == 401) KeyCheck.REJECTED
            else KeyCheck.UNREACHABLE
        },
    )

    /** Genres du catalogue, dans la langue de l'app (listes distinctes film / série). */
    suspend fun genres(apiKey: String, isTv: Boolean): List<Genre> =
        api.genres(if (isTv) "tv" else "movie", apiKey, language).genres

    /** Titres d'un genre, les plus populaires d'abord. */
    /**
     * Parcours d'un genre, tri et filtres compris.
     *
     * Tout part **au service**, et c'est la différence de fond avec la recherche
     * par texte : là-bas TMDB n'accepte aucun critère, il faut donc rapporter
     * plusieurs pages et trier soi-même — d'où l'avertissement « le tri porte
     * sur les N premiers résultats ». Ici le tri et les filtres portent sur le
     * catalogue entier, et la pagination reste juste.
     */
    suspend fun discover(
        apiKey: String,
        isTv: Boolean,
        genreId: Int,
        page: Int = 1,
        filters: SearchFilters = SearchFilters.DEFAULT,
    ): List<TmdbItem> {
        // Bornes en dates complètes : TMDB compare des dates, pas des années.
        val from = filters.minYear?.let { "$it-01-01" }
        val to = filters.maxYear?.let { "$it-12-31" }
        return api.discover(
            media = if (isTv) "tv" else "movie",
            apiKey = apiKey,
            language = language,
            genreId = genreId,
            // La pertinence n'existe pas sans texte à comparer : `discover`
            // retombe alors sur son propre défaut, la popularité décroissante.
            sortBy = filters.discoverSort() ?: "popularity.desc",
            page = page,
            minRating = filters.minRating.takeIf { it > 0.0 },
            movieFrom = from.takeUnless { isTv },
            movieTo = to.takeUnless { isTv },
            tvFrom = from.takeIf { isTv },
            tvTo = to.takeIf { isTv },
        ).results
    }

    /**
     * Ce qu'une personne a joué, du plus récent au plus ancien.
     *
     * Trois nettoyages, tous nécessaires sur des réponses réelles :
     *
     * - **dédoublonnage** sur (id, type) : un acteur crédité dans plusieurs
     *   épisodes d'une série y apparaît une fois par épisode ;
     * - **sans affiche, écarté** : la grille est faite d'affiches, une entrée
     *   vide n'y est qu'un trou qu'on ne sait pas nommer ;
     * - **tri par date décroissante**, les sans-date en fin. TMDB rend l'ordre
     *   de sa base, qui n'a aucun sens à l'écran ; ce qu'on cherche en ouvrant
     *   une filmographie, c'est « qu'a-t-il fait récemment ».
     */
    suspend fun personCredits(apiKey: String, personId: Int): List<TmdbItem> =
        api.personCredits(personId, apiKey, language).cast
            .filter { it.posterPath != null }
            .distinctBy { it.id to it.isTv }
            .sortedByDescending { it.year ?: "" }

    suspend fun movieDetails(apiKey: String, id: Int): MovieDetails =
        api.movieDetails(id, apiKey, language)

    suspend fun tvDetails(apiKey: String, id: Int): TvDetails =
        api.tvDetails(id, apiKey, language)

    suspend fun season(apiKey: String, id: Int, season: Int): SeasonDetails =
        api.season(id, season, apiKey, language)

    /**
     * Meilleure bande-annonce d'un titre, langue de l'utilisateur d'abord.
     *
     * [appended] est la liste déjà arrivée avec la fiche (`append_to_response`),
     * donc gratuite. TMDB l'a filtrée sur `language` : sur un titre qui n'a pas
     * de bande-annonce française elle est **vide**, ce qui n'est pas la même
     * chose que « ce titre n'a pas de bande-annonce ». D'où la seconde requête,
     * en anglais, déclenchée seulement dans ce cas — la plupart des titres
     * doublés n'en auront jamais besoin.
     *
     * Rend null quand il n'y a rien de jouable : l'appelant n'affiche alors pas
     * le bouton, plutôt que d'en afficher un qui échoue.
     */
    suspend fun bestTrailer(
        apiKey: String,
        id: Int,
        isTv: Boolean,
        appended: List<TmdbVideo>,
    ): TmdbVideo? {
        appended.bestTrailer(language)?.let { return it }
        return runCatching {
            api.videos(if (isTv) "tv" else "movie", id, apiKey, FALLBACK_LANGUAGE).results
        }.getOrNull()?.bestTrailer(language)
    }

    private companion object {
        /**
         * Anglais : c'est la langue dans laquelle TMDB a une bande-annonce quand
         * il n'en a qu'une, parce que c'est celle que les studios déposent.
         */
        const val FALLBACK_LANGUAGE = "en-US"
    }
}

/**
 * Verdict d'une vérification de clé TMDB.
 *
 * Trois états et non deux : « pas valide » recouvrirait à la fois une clé fausse
 * et un réseau absent, deux situations qui n'appellent pas la même action de la
 * part de qui vient d'installer l'application.
 */
enum class KeyCheck {
    /** Le service a répondu : la clé fonctionne. */
    VALID,

    /** 401 : le service refuse cette clé. */
    REJECTED,

    /** Aucune réponse exploitable — réseau, DNS, panne. La clé n'est pas en cause. */
    UNREACHABLE,
}
