package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire.
 *
 * [CoverageProbeTest] répond à « le titre est-il regardable ? » et s'arrête au
 * premier lien jouable. Utile, mais aveugle à la question posée ici : **que
 * reste-t-il quand ce premier lien meurt ?** Un épisode couvert par un unique
 * lien uqload est couvert *aujourd'hui* ; le jour où uqload change son
 * obfuscation, il tombe — et c'est exactement le symptôme d'origine, « d'un
 * épisode à l'autre il n'y a souvent aucune VF ».
 *
 * On mesure donc la **redondance** : tous les liens VF sont résolus et testés,
 * pas seulement le premier, puis comptés par hébergeur et par catalogue. Un
 * épisode n'est jugé redondant que s'il garde un lien jouable après la perte
 * d'un hébergeur *et* après la perte d'un catalogue — deux pannes distinctes,
 * l'une chez l'extracteur, l'autre chez le fournisseur.
 *
 * Le panier balaie plusieurs épisodes par série, dont des saisons tardives : la
 * couverture d'un S1E1 est systématiquement meilleure que celle du reste, c'est
 * le biais qui rend les mesures sur pilote trompeuses.
 */
class SeriesRedundancyProbeTest {

    private data class S(val strate: String, val id: Int, val nom: String, val eps: List<Pair<Int, Int>>)

    private val series = listOf(
        S("anime", 1429, "L'Attaque des Titans", listOf(1 to 1, 1 to 12, 3 to 5)),
        S("anime", 37854, "One Piece", listOf(1 to 1, 1 to 40)),
        S("populaire", 1416, "Grey's Anatomy", listOf(1 to 1, 5 to 8, 17 to 3)),
        S("populaire", 94997, "House of the Dragon", listOf(1 to 1, 2 to 4)),
        S("populaire", 60625, "Rick et Morty", listOf(1 to 1, 6 to 2)),
        S("populaire", 5920, "Mentalist", listOf(1 to 1, 4 to 9)),
        S("recent", 273240, "Off Campus", listOf(1 to 1, 1 to 4)),
        S("francais", 62688, "Le Bureau des légendes", listOf(1 to 1, 3 to 2)),
    )

    @Test
    fun probeSeriesRedundancy() = runBlocking {
        if (System.getProperty("moovie.probe") == null) return@runBlocking

        val rows = mutableListOf<Row>()
        println("\n%-38s %-22s %-26s %s".format("épisode", "liens VF (catalogue)", "jouables (hébergeur)", "verdict"))
        println("─".repeat(120))

        for (s in series) {
            for ((season, episode) in s.eps) {
                rows += measure(s, season, episode)
            }
        }

        summary(rows)
    }

    private data class Row(
        val strate: String,
        val serie: String,
        val episode: String,
        val playableHosters: Set<String>,
        val playableProviders: Set<String>,
        val playableCount: Int,
    ) {
        /** Couvert = au moins un lien jouable. */
        val covered get() = playableCount > 0

        /**
         * Redondant = survit à la perte de n'importe quel hébergeur *et* de
         * n'importe quel catalogue. Deux hébergeurs chez un seul catalogue ne
         * suffisent pas : si le catalogue tombe, les deux partent avec lui.
         */
        val redundant get() = playableHosters.size >= 2 && playableProviders.size >= 2
    }

    private suspend fun measure(s: S, season: Int, episode: Int): Row {
        val media = MediaRef.Episode(s.id, s.nom, null, season, episode)

        val perProvider = coroutineScope {
            ProviderRegistry.all.map { p ->
                async {
                    p.name to runCatching { p.sourcesFor(media) }
                        .getOrDefault(emptyList())
                        .filter { it.language == "VF" }
                }
            }.awaitAll()
        }

        // Un même embed peut être listé par deux catalogues : on le dédoublonne,
        // sinon la redondance est comptée deux fois pour un seul lien réel.
        val links = mutableMapOf<String, Pair<EmbedLink, String>>()
        for ((provider, list) in perProvider) {
            for (l in list) links.putIfAbsent(l.url, l to provider)
        }

        val slots = Semaphore(4)
        val playable = coroutineScope {
            links.values.map { (link, provider) ->
                async {
                    slots.withPermit {
                        val stream = runCatching { ExtractorRegistry.resolve(link) }.getOrNull()
                            ?: return@withPermit null
                        if (runCatching { isStreamPlayable(stream) }.getOrDefault(false)) {
                            link.hoster to provider
                        } else {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

        val row = Row(
            strate = s.strate,
            serie = s.nom,
            episode = "S${season}E$episode",
            playableHosters = playable.map { it.first }.toSet(),
            playableProviders = playable.map { it.second }.toSet(),
            playableCount = playable.size,
        )

        println(
            "%-38s %-22s %-26s %s".format(
                "${s.nom.take(26)} S${season}E$episode",
                perProvider.filter { it.second.isNotEmpty() }
                    .joinToString(" ") { "${it.first}:${it.second.size}" }.ifEmpty { "—" },
                playable.groupingBy { it.first }.eachCount()
                    .entries.joinToString(" ") { "${it.key}:${it.value}" }.ifEmpty { "—" },
                when {
                    row.redundant -> "✅ redondant"
                    row.covered -> "⚠️ unique (${row.playableHosters.joinToString()} / ${row.playableProviders.joinToString()})"
                    else -> "⛔ aucun"
                },
            ),
        )
        return row
    }

    private fun summary(rows: List<Row>) {
        println("\n════════ SYNTHÈSE SÉRIES ════════")
        val covered = rows.count { it.covered }
        val redundant = rows.count { it.redundant }
        println("épisodes testés     : ${rows.size}")
        println("couverts (≥1 lien)  : $covered / ${rows.size}")
        println("redondants          : $redundant / ${rows.size}")
        println("mono-lien (fragile) : ${covered - redundant} / ${rows.size}")

        println("\npar strate :")
        rows.groupBy { it.strate }.toSortedMap().forEach { (strate, v) ->
            println(
                "   %-14s couverts %d/%d   redondants %d/%d".format(
                    strate, v.count { it.covered }, v.size, v.count { it.redundant }, v.size,
                ),
            )
        }

        println("\nS1E1 vs saisons ultérieures :")
        rows.groupBy { it.episode == "S1E1" }.forEach { (pilote, v) ->
            println(
                "   %-14s couverts %d/%d   redondants %d/%d".format(
                    if (pilote) "S1E1" else "autres", v.count { it.covered }, v.size,
                    v.count { it.redundant }, v.size,
                ),
            )
        }

        println("\nhébergeurs qui rendent un lien jouable :")
        rows.flatMap { it.playableHosters }.groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
            .forEach { (h, n) -> println("   %-16s %d épisodes".format(h, n)) }

        println("\ncatalogues qui rendent un lien jouable :")
        rows.flatMap { it.playableProviders }.groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
            .forEach { (p, n) -> println("   %-16s %d épisodes".format(p, n)) }

        // Les deux chiffres qui décident : si une brique disparaissait, combien
        // d'épisodes deviendraient invisibles ? Les deux axes sont indépendants —
        // un catalogue meurt quand son domaine tombe, un hébergeur quand il change
        // son obfuscation, et rien ne dit que c'est le même jour.
        println("\nsi un catalogue tombait :")
        rows.flatMap { it.playableProviders }.toSet().sorted().forEach { p ->
            val perdus = rows.count { it.covered && it.playableProviders == setOf(p) }
            println("   sans %-12s → %d épisodes perdus sur $covered".format(p, perdus))
        }

        println("\nsi un hébergeur tombait :")
        rows.flatMap { it.playableHosters }.toSet().sorted().forEach { h ->
            val perdus = rows.count { it.covered && it.playableHosters == setOf(h) }
            println("   sans %-12s → %d épisodes perdus sur $covered".format(h, perdus))
        }
    }
}
