package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.test.Test

/**
 * Contrôle de santé des sources — neuf titres, deux minutes.
 *
 * ### Ce qu'elle n'est pas
 *
 * Ce n'est **pas** une mesure de couverture. [CoverageProbeTest] fait ça, sur un
 * panier stratifié de 38 titres tiré de TMDB, et c'est elle qu'il faut lire pour
 * savoir ce que l'application couvre vraiment. Celle-ci répond à une autre
 * question, qu'on se pose bien plus souvent : **est-ce que tout marche encore ?**
 *
 * ### Le défaut qu'elle guette
 *
 * Un catalogue mort est silencieux. La cascade est conçue pour qu'il ne bloque
 * pas les autres, et le prix est qu'une liste vide est indistinguable de « ce
 * titre n'y est pas ». anime-sama est passé aux liens absolus, le provider est
 * devenu muet **sur tous les animés**, et ça a survécu à une release : aucun
 * test n'a échoué, rien n'a été journalisé.
 *
 * ### Le panier, et pourquoi ces titres-là
 *
 * Trois films, trois séries, trois animés, **choisis pour être indiscutables** :
 * des œuvres anciennes, largement diffusées, doublées depuis des années. Un
 * titre récent ferait un mauvais canari — l'absence de VF y est une nouvelle
 * ordinaire, pas un symptôme. Ici, un zéro veut dire quelque chose.
 *
 * ### La sortie
 *
 * Chaque relevé imprime des lignes `PROVIDER <nom> <titres>` et un
 * `COUVERTURE n/9`, que `tools/check-sources.sh` compare d'un relevé à l'autre.
 * C'est la comparaison qui porte le signal : `animesama 9` puis `animesama 0`
 * est lisible, `animesama 0` seul ne l'est pas.
 */
class QuickCoverageProbeTest {

    private data class Titre(val genre: String, val nom: String, val media: MediaRef)

    private val panier = listOf(
        // Films : trois classiques doublés depuis toujours.
        Titre("film", "Fight Club", MediaRef.Movie(550, "Fight Club", "1999")),
        Titre("film", "Intouchables", MediaRef.Movie(77338, "Intouchables", "2011")),
        Titre("film", "Matrix", MediaRef.Movie(603, "Matrix", "1999")),
        // Séries : des feuilletons au long cours, dont le S1E1 existe partout.
        Titre("série", "Grey's Anatomy", MediaRef.Episode(1416, "Grey's Anatomy", null, 1, 1)),
        Titre("série", "Mentalist", MediaRef.Episode(5920, "Mentalist", null, 1, 1)),
        Titre(
            "série",
            "New York Unité Spéciale",
            MediaRef.Episode(2734, "New York Unité Spéciale", null, 1, 1),
        ),
        // Animés : les trois piliers du catalogue francophone.
        Titre("animé", "One Piece", MediaRef.Episode(37854, "One Piece", null, 1, 1)),
        Titre("animé", "Naruto", MediaRef.Episode(46260, "Naruto", null, 1, 1)),
        Titre("animé", "Demon Slayer", MediaRef.Episode(85937, "Demon Slayer", null, 1, 1)),
    )

    @Test
    fun probeQuickCoverage() = runBlocking {
        if (System.getProperty("moovie.probe") != "1") {
            println("[sonde rapide] ignorée (relancer avec -Dmoovie.probe=1)")
            return@runBlocking
        }

        // Mesuré en parallèle mais **imprimé après** : neuf résolutions à la
        // suite prennent plusieurs minutes, et des `println` entrelacés
        // rendraient le relevé illisible — or il est fait pour être comparé.
        val gate = Semaphore(3)
        val lignes = panier
            .map { titre -> async { gate.withPermit { mesure(titre) } } }
            .awaitAll()

        println("\n════════ CONTRÔLE RAPIDE DES SOURCES ════════")
        println("%-10s %-28s %-30s %s".format("genre", "titre", "providers avec du VF", "jouable"))
        lignes.forEach { r ->
            println(
                "%-10s %-28s %-30s %s".format(
                    r.genre,
                    r.titre.take(26),
                    r.providers.joinToString(" ").ifEmpty { "—" },
                    r.hoster?.let { "✅ $it" } ?: "⛔ aucun",
                ),
            )
        }

        val ok = lignes.count { it.hoster != null }
        println("\nCOUVERTURE $ok/${lignes.size}")

        // Une ligne par provider **connu**, y compris à zéro : c'est le zéro qui
        // est le signal, et il ne peut apparaître que si la ligne existe.
        val parProvider = lignes.flatMap { it.providers }.groupingBy { it }.eachCount()
        ProviderRegistry.all.map { it.name }.sorted().forEach { nom ->
            println("PROVIDER $nom ${parProvider[nom] ?: 0}")
        }

        val parHebergeur = lignes.mapNotNull { it.hoster }.groupingBy { it }.eachCount()
        parHebergeur.toList().sortedByDescending { it.second }
            .forEach { (h, n) -> println("HEBERGEUR $h $n") }

        // Seule la **couverture** part d'ici. Le compte par catalogue était
        // faux comme mesure de santé : cette sonde ne retient que la VF, donc
        // vidapi — qui ne produit que de la VO, par construction — affichait
        // zéro depuis toujours et passait pour mort. Voir ProviderHealthProbeTest.
        if (ProbeReport.demande) {
            ProbeReport.ecris(
                "coverage",
                buildString {
                    append("""{"covered":$ok,"total":${lignes.size},"titles":[""")
                    lignes.forEachIndexed { i, l ->
                        if (i > 0) append(',')
                        append("""{"title":${ProbeReport.texte(l.titre)},""")
                        append(""""kind":${ProbeReport.texte(l.genre)},""")
                        append(""""playable":${l.hoster != null},""")
                        append(""""hoster":${l.hoster?.let { ProbeReport.texte(it) } ?: "null"}}""")
                    }
                    append("]}")
                },
            )
        }
    }

    private data class Releve(
        val genre: String,
        val titre: String,
        val providers: List<String>,
        val hoster: String?,
    )

    /**
     * Un titre : quels catalogues en ont du VF, et le premier lien qui joue.
     *
     * On s'arrête au premier jouable, comme le fait la cascade — c'est ce qui
     * décide si le titre est regardable, et non le nombre de liens trouvés.
     */
    private suspend fun mesure(titre: Titre): Releve {
        val parProvider = ProviderRegistry.all.map { p ->
            p.name to runCatching { p.sourcesFor(titre.media) }.getOrDefault(emptyList())
                .filter { it.language == "VF" }
        }
        val avecVf = parProvider.filter { it.second.isNotEmpty() }

        val liens: List<EmbedLink> = avecVf.flatMap { it.second }.distinctBy { it.url }
        var hoster: String? = null
        for (lien in liens) {
            val flux = runCatching { ExtractorRegistry.resolve(lien) }.getOrNull() ?: continue
            if (isStreamPlayable(flux)) {
                hoster = lien.hoster
                break
            }
        }
        return Releve(titre.genre, titre.nom, avecVf.map { it.first }, hoster)
    }
}
