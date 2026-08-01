package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire.
 *
 * Mesure la **couverture** : sur un panier de titres, combien en ont au moins un
 * lien VF réellement jouable ? C'est la seule métrique qui corresponde au
 * problème d'origine — « d'un épisode à l'autre, il n'y a souvent aucune VF ».
 * Compter les liens d'un blockbuster flatte les chiffres ; ce qui compte, c'est
 * le nombre de titres à zéro.
 *
 * Le panier est **stratifié et tiré de TMDB** (populaires, récents, anciens,
 * confidentiels, français), et non choisi de mémoire : sans ça on ne mesure que
 * son propre biais vers les gros titres. Restreint aux œuvres **déjà sorties**
 * et, pour les strates confidentielles, aux langues d'origine EN/FR — un feuilleton
 * hongrois sans VF ne dit rien de nos providers.
 *
 * Elle rapporte aussi **qui** a fourni le lien : c'est ce qui révèle une
 * mono-dépendance, invisible dans un simple taux de réussite.
 */
class CoverageProbeTest {

    private data class M(val strate: String, val id: Int, val titre: String, val annee: String)
    private data class E(val strate: String, val id: Int, val nom: String)

    private val films = listOf(
        M("populaire", 1339713, "Obsession", "2026"),
        M("populaire", 634649, "Spider-Man : No Way Home", "2021"),
        M("populaire", 687163, "Projet Dernière Chance", "2026"),
        M("populaire", 557, "Spider-Man", "2002"),
        M("populaire", 315635, "Spider-Man : Homecoming", "2017"),
        M("populaire", 83533, "Avatar : De feu et de cendres", "2025"),
        M("recent", 1368337, "L'Odyssée", "2026"),
        M("recent", 1081003, "Supergirl", "2026"),
        M("recent", 454639, "Les Maîtres de l'univers", "2026"),
        M("recent", 1108427, "Vaiana, la légende du bout du monde", "2026"),
        M("ancien", 278, "Les Évadés", "1994"),
        M("ancien", 550, "Fight Club", "1999"),
        M("ancien", 862, "Toy Story", "1995"),
        M("ancien", 238, "Le Parrain", "1972"),
        M("ancien", 11, "La Guerre des étoiles", "1977"),
        M("ancien", 603, "Matrix", "1999"),
        M("confidentiel", 1284465, "On l'appelait Robin des Bois", "2026"),
        M("confidentiel", 1318621, "Descendants : La Malédiction du Pays des Merveilles", "2026"),
        M("confidentiel", 47612, "Au Bonheur des Dames", "1930"),
        M("confidentiel", 976912, "Graphic Designs", "2023"),
        M("francais", 77338, "Intouchables", "2011"),
        M("francais", 101, "Léon", "1994"),
        M("francais", 18, "Le Cinquième Élément", "1997"),
        M("francais", 1246049, "Dracula", "2025"),
        M("francais", 1196470, "Survivre", "2024"),
    )

    private val series = listOf(
        E("populaire", 5920, "Mentalist"),
        E("populaire", 94997, "House of the Dragon"),
        E("populaire", 2734, "New York Unité Spéciale"),
        E("populaire", 79744, "The Rookie : Le Flic de Los Angeles"),
        E("populaire", 60625, "Rick et Morty"),
        E("populaire", 1416, "Grey's Anatomy"),
        E("recent", 278624, "Lucky"),
        E("recent", 273240, "Off Campus"),
        E("recent", 287238, "Furious"),
        E("recent", 277439, "Cape Fear - Les Nerfs à vif"),
        E("confidentiel", 22980, "Watch What Happens Live with Andy Cohen"),
        E("confidentiel", 91555, "All Elite Wrestling: Dynamite"),
        E("confidentiel", 11366, "Big Brother"),
    )

    @Test
    fun probeCoverage() = runBlocking {
        if (System.getProperty("moovie.probe") == null) return@runBlocking

        val rows = mutableListOf<Row>()

        println("\n════════ FILMS ════════")
        header()
        for (f in films) {
            rows += measure(f.strate, "film", f.titre, MediaRef.Movie(f.id, f.titre, f.annee))
        }

        println("\n════════ SÉRIES (S1E1) ════════")
        header()
        for (s in series) {
            rows += measure(s.strate, "série", s.nom, MediaRef.Episode(s.id, s.nom, null, 1, 1))
        }

        summary(rows)
    }

    private data class Row(
        val strate: String,
        val type: String,
        val providers: List<String>,
        val hoster: String?,
    )

    private fun header() =
        println("%-46s %-26s %s".format("titre", "providers avec du VF", "1er lien jouable"))

    private suspend fun measure(strate: String, type: String, titre: String, media: MediaRef): Row {
        val perProvider = ProviderRegistry.all.map { p ->
            p.name to runCatching { p.sourcesFor(media) }.getOrDefault(emptyList())
                .filter { it.language == "VF" }
        }
        val withVf = perProvider.filter { it.second.isNotEmpty() }

        // On s'arrête au premier jouable : c'est ce que fait la cascade, et c'est
        // ce qui décide si le titre est regardable ou non.
        val links: List<EmbedLink> = withVf.flatMap { it.second }.distinctBy { it.url }
        var hoster: String? = null
        for (link in links) {
            val stream = ExtractorRegistry.resolve(link) ?: continue
            if (isStreamPlayable(stream)) { hoster = link.hoster; break }
        }

        println(
            "%-46s %-26s %s".format(
                titre.take(44),
                withVf.joinToString(" ") { "${it.first}:${it.second.size}" }.ifEmpty { "—" },
                hoster?.let { "✅ $it" } ?: "⛔ aucun",
            ),
        )
        return Row(strate, type, withVf.map { it.first }, hoster)
    }

    private fun summary(rows: List<Row>) {
        println("\n════════ SYNTHÈSE ════════")
        println("%-16s %-8s %-12s %s".format("strate", "type", "couverture", "détail"))
        rows.groupBy { it.strate to it.type }.toSortedMap(compareBy({ it.first }, { it.second }))
            .forEach { (k, v) ->
                val ok = v.count { it.hoster != null }
                println("%-16s %-8s %-12s %s".format(k.first, k.second, "$ok / ${v.size}", ""))
            }

        val ok = rows.count { it.hoster != null }
        println("\n→ couverture globale : $ok / ${rows.size}")

        println("\nqui fournit le lien retenu :")
        rows.mapNotNull { it.hoster }.groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
            .forEach { (h, n) -> println("   %-16s %d".format(h, n)) }

        println("\nproviders qui rendent du VF (toutes occurrences) :")
        rows.flatMap { it.providers }.groupingBy { it }.eachCount()
            .toList().sortedByDescending { it.second }
            .forEach { (p, n) -> println("   %-16s %d titres".format(p, n)) }
    }
}
