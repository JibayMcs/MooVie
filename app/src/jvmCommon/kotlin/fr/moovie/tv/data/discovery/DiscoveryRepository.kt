package fr.moovie.tv.data.discovery

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import fr.moovie.tv.data.store.STORE_DISCOVERY
import fr.moovie.tv.data.store.preferencesStore
import fr.moovie.tv.data.store.profileStoreName
import fr.moovie.tv.data.sync.MoovieClock
import fr.moovie.tv.data.tmdb.TmdbItem
import fr.moovie.tv.data.tmdb.TmdbRepository
import fr.moovie.tv.data.watch.HistoryEntry
import fr.moovie.tv.data.watch.WatchProgressRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.random.Random

/**
 * Ce que la page Découverte a écarté, et ce qu'elle a appris de TMDB.
 *
 * Deux choses très différentes dans le même magasin, pour une raison simple :
 * ce sont les deux seules données que la découverte possède en propre.
 *
 * - **les sagas masquées**, qui n'ont pas de clé de visionnage et ne peuvent
 *   donc pas être marquées « vues » dans le suivi de lecture ;
 * - **la saga d'un film**, une fois qu'on l'a demandée à TMDB. C'est un fait
 *   sur le catalogue, pas sur la personne, mais le relire coûte une requête par
 *   film d'historique à chaque ouverture de la page. Zéro signifie « demandé,
 *   et ce film n'appartient à aucune saga » — sans cette valeur on
 *   redemanderait éternellement les films qui n'en ont pas, c'est-à-dire la
 *   plupart.
 */
class DiscoveryPrefs {

    private val store = preferencesStore(profileStoreName(STORE_DISCOVERY))
    private val hidden = stringSetPreferencesKey("hidden")

    val hiddenKeys: Flow<Set<String>> = store.data.map { it[hidden] ?: emptySet() }

    suspend fun hide(key: String) {
        store.edit { prefs -> prefs[hidden] = (prefs[hidden] ?: emptySet()) + key }
    }

    /** La saga connue d'un film : id de collection, ou 0 s'il n'en a pas. */
    suspend fun cachedCollection(movieId: Int): Int? =
        store.data.first()[intPreferencesKey("col:$movieId")]

    suspend fun cacheCollection(movieId: Int, collectionId: Int) {
        store.edit { it[intPreferencesKey("col:$movieId")] = collectionId }
    }
}

/**
 * Construit les groupes de la page Découverte.
 *
 * ### Le principe
 *
 * Chaque groupe est calculé **indépendamment** et enveloppé dans un
 * `runCatching` : un endpoint qui tombe, une clé qui expire ou une saga
 * introuvable font disparaître un groupe, jamais la page. C'est la même règle
 * que la cascade des sources — sauf qu'ici, contrairement aux catalogues, un
 * groupe vide se voit immédiatement puisque la page en compte quatre.
 *
 * ### Ce qui n'est volontairement pas fait
 *
 * On ne vérifie **pas** qu'une source existe pour les titres proposés. Trente
 * résolutions, c'est plusieurs minutes de réseau pour afficher des croix
 * décourageantes ; la fiche décide, comme partout ailleurs dans l'application.
 *
 * En revanche on ne pousse jamais vers le récent : les recommandations tirent
 * naturellement vers des titres plus anciens, et ce sont eux qui ont des
 * sources en VF. Une rangée de nouveautés serait une belle vitrine sur du vide.
 */
class DiscoveryRepository(
    private val tmdb: TmdbRepository,
    private val watch: WatchProgressRepository = WatchProgressRepository(),
    private val prefs: DiscoveryPrefs = DiscoveryPrefs(),
) {

    /**
     * Construit la page.
     *
     * [tirage] est le nombre de fois qu'on a demandé à **rebattre les cartes**.
     * Il ne change rien aux recettes, seulement à ce qu'elles servent : les
     * groupes bâtis sur TMDB demandent une autre page, ceux bâtis sur
     * l'historique rebattent leur ordre. Sans lui, « Redistribuer » recalculait
     * scrupuleusement le même résultat — techniquement juste, et parfaitement
     * inutile pour qui cherche à découvrir.
     */
    suspend fun build(apiKey: String, mood: MoodAnswers, tirage: Int = 0): DiscoveryState {
        if (apiKey.isBlank()) return DiscoveryState.NeedsKey

        val history = watch.history.first()
        val seen = watch.watched.first()
        val enCours = watch.continueWatching.first().map { it.key }.toSet()
        val liste = watch.watchlist.first().map { it.key }.toSet()
        val masques = prefs.hiddenKeys.first()

        // Un titre déjà vu, en cours, mis de côté ou écarté n'a rien à faire
        // dans une page de découverte. `seen` porte des clés d'épisode
        // (`tv:1416:s1e1`) : on en dérive la clé de titre, sans quoi une série
        // dont on a vu vingt épisodes resterait proposée.
        val exclus = buildSet {
            addAll(enCours)
            addAll(liste)
            addAll(masques)
            seen.forEach { key ->
                add(key)
                add(key.split(":").take(2).joinToString(":"))
            }
        }

        if (history.isEmpty() && !mood.isComplete) return DiscoveryState.ColdStart

        val groupes = coroutineScope {
            listOf(
                async { runCatching { humeur(apiKey, mood, exclus, tirage) }.getOrNull() },
                async { runCatching { recoupement(apiKey, history, exclus, tirage) }.getOrNull() },
                async { runCatching { sagas(apiKey, history, seen, masques, tirage) }.getOrNull() },
                async { runCatching { revoir(history, masques, tirage) }.getOrNull() },
                async { runCatching { pepites(apiKey, history, mood, exclus, tirage) }.getOrNull() },
            ).awaitAll()
        }.filterNotNull().filter { it.cards.isNotEmpty() }

        return if (groupes.isEmpty()) DiscoveryState.ColdStart
        else DiscoveryState.Ready(groupes)
    }

    fun hiddenKeys(): Flow<Set<String>> = prefs.hiddenKeys

    suspend fun hide(key: String) = prefs.hide(key)

    // ── Les recettes ─────────────────────────────────────────────────────────

    /** Ce que le questionnaire a demandé. Absent tant qu'il n'a pas été répondu. */
    private suspend fun humeur(
        apiKey: String,
        mood: MoodAnswers,
        exclus: Set<String>,
        tirage: Int,
    ): DiscoveryGroup? {
        if (!mood.isComplete) return null
        val items = pageTiree(tirage) { page ->
            tmdb.discoverMood(
                apiKey = apiKey,
                isTv = mood.wantsTv,
                genres = mood.genres,
                // Trié par note, pas par popularité : c'est ce qui sépare une
                // découverte d'un carrousel. Le plancher de votes évite les
                // 10/10 sur trois votants, qui ne sont pas une note mais du bruit.
                sortBy = "vote_average.desc",
                page = page,
                minRating = 6.2,
                minVotes = 300,
                maxRuntime = mood.maxRuntime,
                minRuntime = mood.minRuntime,
            )
        }
        return groupe(DiscoveryKind.HUMEUR, items, exclus)
    }

    /**
     * Ce que plusieurs titres terminés désignent en commun.
     *
     * Le score est le **nombre de graines** qui recommandent un titre. Un film
     * désigné par trois de vos films n'est pas de même nature qu'un film
     * désigné par un seul, et c'est la seule différence entre cette page et la
     * rangée « parce que vous avez regardé X » de l'accueil, qui ne part que du
     * dernier titre vu.
     */
    private suspend fun recoupement(
        apiKey: String,
        history: List<HistoryEntry>,
        exclus: Set<String>,
        tirage: Int,
    ): DiscoveryGroup? {
        val graines = history.distinctBy { it.titleKey }.take(GRAINES)
        if (graines.isEmpty()) return null

        val listes = coroutineScope {
            graines.map { g ->
                async {
                    runCatching { tmdb.recommendations(apiKey, g.isTv, g.tmdbId) }
                        .getOrDefault(emptyList())
                }
            }.awaitAll()
        }

        // Compté par titre, pas par occurrence : une graine qui recommande deux
        // fois le même film ne vaut pas deux graines.
        val score = mutableMapOf<Pair<Int, Boolean>, Int>()
        val parId = mutableMapOf<Pair<Int, Boolean>, TmdbItem>()
        listes.forEach { liste ->
            liste.distinctBy { it.id to it.isTv }.forEach { item ->
                val id = item.id to item.isTv
                score[id] = (score[id] ?: 0) + 1
                parId.putIfAbsent(id, item)
            }
        }

        /*
         * Le classement reste le recoupement, mais **les ex aequo sont
         * rebattus**.
         *
         * Avec six graines, l'immense majorité des candidats est à un seul
         * recoupement : ce paquet-là est énorme, et c'est lui qu'on voit en
         * premier. Le trier par note donnait toujours la même tête de liste, si
         * bien que « Redistribuer » ne redistribuait rien. Brasser à l'intérieur
         * d'un même score change ce qui se présente sans jamais faire passer un
         * titre désigné une fois devant un titre désigné trois fois.
         */
        val des = Random(tirage)
        val classes = score.entries
            .groupBy { it.value }
            .toSortedMap(compareByDescending { it })
            .values
            .flatMap { paquet -> paquet.shuffled(des) }
            .mapNotNull { parId[it.key] }

        return groupe(
            kind = DiscoveryKind.RECOUPEMENT,
            items = classes,
            exclus = exclus,
            seeds = graines.take(3).map { it.title }.filter { it.isNotBlank() },
        )
    }

    /**
     * Vu il y a longtemps, jamais rouvert.
     *
     * Le seuil est volontairement bas ([JOURS_REVOIR] jours) : l'historique
     * daté n'existe que depuis peu dans l'application, un seuil de deux ans
     * rendrait le groupe vide pour tout le monde pendant deux ans. Il
     * disparaît, comme les autres, quand il n'a rien à dire.
     */
    private suspend fun revoir(
        history: List<HistoryEntry>,
        masques: Set<String>,
        tirage: Int,
    ): DiscoveryGroup? {
        val limite = MoovieClock.now() - JOURS_REVOIR * 24L * 3600_000L
        val cartes = history
            .filter { it.watchedAt in 1 until limite }
            .distinctBy { it.titleKey }
            .filter { it.titleKey !in masques }
            .map {
                DiscoveryCard.Title(
                    tmdbId = it.tmdbId,
                    isTv = it.isTv,
                    title = it.title,
                    posterUrl = it.imageUrl,
                )
            }
        // Brassé plutôt que trié par date : l'ordre chronologique donnait
        // toujours les mêmes trois titres en tête, et ce groupe n'a aucune
        // raison d'être ordonné — tout y a été vu il y a longtemps.
        return cartes.takeIf { it.size >= 2 }
            ?.let { DiscoveryGroup(DiscoveryKind.REVOIR, it.shuffled(Random(tirage))) }
    }

    /**
     * Vos sagas commencées, et le film qui vient ensuite.
     *
     * Aucune donnée éditoriale : `belongs_to_collection` arrive déjà dans la
     * fiche d'un film, et `/collection/{id}` donne le reste. On ne prétend pas
     * connaître l'ordre chronologique de fiction — il n'existe nulle part dans
     * l'API, et l'inventer serait pire que de s'en tenir aux dates de sortie.
     */
    private suspend fun sagas(
        apiKey: String,
        history: List<HistoryEntry>,
        seen: Set<String>,
        masques: Set<String>,
        tirage: Int,
    ): DiscoveryGroup? {
        val films = history.filterNot { it.isTv }.distinctBy { it.tmdbId }.take(FILMS_SAGA)
        if (films.isEmpty()) return null

        val ids = coroutineScope {
            films.map { f ->
                async {
                    prefs.cachedCollection(f.tmdbId) ?: runCatching {
                        tmdb.movieDetails(apiKey, f.tmdbId).collection?.id ?: 0
                    }.getOrDefault(0).also { prefs.cacheCollection(f.tmdbId, it) }
                }
            }.awaitAll()
        }.filter { it > 0 }.distinct()

        if (ids.isEmpty()) return null

        val aujourdhui = LocalDate.now().toString()
        val cartes = coroutineScope {
            ids.map { id ->
                async { runCatching { tmdb.collection(apiKey, id) }.getOrNull() }
            }.awaitAll()
        }.filterNotNull().mapNotNull { col ->
            // Un film pas encore sorti n'est pas « le prochain » : il ne se
            // regarde pas, et le proposer transforme une saga en promesse.
            val sortis = col.inOrder.filter { (it.releaseDate ?: "") <= aujourdhui }
            if (sortis.isEmpty()) return@mapNotNull null
            val vus = sortis.count { "movie:${it.id}" in seen }
            val suivant = sortis.firstOrNull { "movie:${it.id}" !in seen }
            // Ni les sagas jamais commencées (elles n'ont rien de personnel),
            // ni les sagas finies (il n'y a plus rien à proposer).
            if (vus == 0 || suivant == null) return@mapNotNull null
            DiscoveryCard.Saga(
                collectionId = col.id,
                name = col.name,
                poster = col.posterUrl() ?: suivant.posterUrl(),
                total = sortis.size,
                seen = vus,
                next = suivant,
            )
        }.filter { it.key !in masques }

        return cartes.takeIf { it.isNotEmpty() }?.let {
            DiscoveryGroup(DiscoveryKind.SAGAS, it)
        }
    }

    /**
     * Note haute, peu de votes.
     *
     * La bande [VOTES_MIN]–[VOTES_MAX] est le seul réglage qui décide si la
     * page ressemble à une découverte ou à un carrousel. Au-dessus, on ne
     * propose que ce que tout le monde a déjà vu ; en dessous, on propose des
     * moyennes qui ne veulent rien dire.
     */
    private suspend fun pepites(
        apiKey: String,
        history: List<HistoryEntry>,
        mood: MoodAnswers,
        exclus: Set<String>,
        tirage: Int,
    ): DiscoveryGroup? {
        // Les genres de l'historique sont des **noms** (relevés à l'ouverture
        // d'une fiche), et `discover` veut des identifiants : la table de
        // correspondance vient de TMDB, dans la langue courante.
        val genres = if (mood.genres.isNotEmpty()) {
            mood.genres
        } else {
            val table = runCatching { tmdb.genres(apiKey, isTv = false) }.getOrDefault(emptyList())
                .associate { it.name.lowercase() to it.id }
            history.flatMap { it.genres }
                .groupingBy { it.lowercase() }.eachCount()
                .entries.sortedByDescending { it.value }
                .mapNotNull { table[it.key] }
                .take(3)
        }

        val items = pageTiree(tirage) { page ->
            tmdb.discoverMood(
                apiKey = apiKey,
                isTv = false,
                genres = genres,
                sortBy = "vote_average.desc",
                page = page,
                minRating = 6.8,
                minVotes = VOTES_MIN,
                maxVotes = VOTES_MAX,
            )
        }
        return groupe(DiscoveryKind.PEPITES, items, exclus)
    }

    /**
     * Une page de découverte, choisie par le tirage, avec repli sur la première.
     *
     * Les pages profondes ne sont pas garanties : nos filtres sont serrés (la
     * bande de votes des pépites, surtout), et rien ne dit que TMDB ait trois
     * pages à offrir. Une page maigre ferait disparaître le groupe — alors que
     * l'utilisateur vient précisément de demander à en voir plus. On revient
     * donc à la première dès que la moisson est trop courte.
     */
    private suspend fun pageTiree(
        tirage: Int,
        bloc: suspend (page: Int) -> List<TmdbItem>,
    ): List<TmdbItem> {
        val page = 1 + (tirage % PAGES)
        if (page == 1) return bloc(1)
        val tirees = runCatching { bloc(page) }.getOrDefault(emptyList())
        return if (tirees.size >= MOISSON_MINI) tirees else bloc(1)
    }

    private fun groupe(
        kind: DiscoveryKind,
        items: List<TmdbItem>,
        exclus: Set<String>,
        seeds: List<String> = emptyList(),
    ): DiscoveryGroup? {
        val cartes = items
            .filter { it.posterPath != null }
            .map { DiscoveryCard.Title.of(it) }
            .filter { it.key !in exclus }
            .distinctBy { it.key }
        return cartes.takeIf { it.isNotEmpty() }?.let {
            DiscoveryGroup(kind, it, seeds)
        }
    }

    private companion object {
        /**
         * Titres d'historique interrogés pour le recoupement.
         *
         * Six requêtes en parallèle : assez pour que le recoupement veuille
         * dire quelque chose, assez peu pour que la page s'ouvre.
         */
        const val GRAINES = 6

        /** Films d'historique dont on cherche la saga. Voir le cache dans [DiscoveryPrefs]. */
        const val FILMS_SAGA = 12

        /**
         * Pages TMDB parcourues en boucle par « Redistribuer ».
         *
         * Trois : de quoi ne pas revoir la même main deux fois de suite, sans
         * s'enfoncer dans une queue de catalogue où les filtres ne rendent plus
         * grand-chose.
         */
        const val PAGES = 3

        /** En deçà, la page tirée est jugée trop maigre et on revient à la première. */
        const val MOISSON_MINI = 5

        const val JOURS_REVOIR = 90
        const val VOTES_MIN = 300
        const val VOTES_MAX = 5_000
    }
}
