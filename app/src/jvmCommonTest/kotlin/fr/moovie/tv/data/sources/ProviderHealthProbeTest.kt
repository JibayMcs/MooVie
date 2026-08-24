package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.test.Test

/**
 * Santé **par catalogue**, toutes langues confondues.
 *
 * ```
 * ./gradlew :app:desktopTest --tests '*ProviderHealthProbeTest' -Dmoovie.probe=1
 * ```
 *
 * ## Le faux mort qu'elle corrige
 *
 * [QuickCoverageProbeTest] ne compte que les liens **VF** : c'est le bon filtre
 * pour la question qu'elle pose — une application francophone est-elle encore
 * regardable — et c'est exactement ce qui la rend aveugle ici.
 *
 * `vidapi` ne produit que de la version originale, par construction : il a été
 * ajouté pour ça, parce qu'aucun autre catalogue ne sert de VO. Il affichait
 * donc `PROVIDER vidapi 0` depuis toujours, et le tableau de bord l'annonçait
 * mort. Il ne l'était pas : il était **invisible à la mesure**.
 *
 * C'est le même piège que celui des catalogues muets, retourné : là-bas une
 * liste vide passait pour un succès, ici un catalogue sain passe pour une
 * panne. Dans les deux cas la faute est à l'instrument, et un moniteur qui crie
 * au loup ne se lit plus au bout d'une semaine.
 *
 * ## Ce qu'elle mesure
 *
 * Le nombre de liens rendus, **par langue**, sans aucun filtre. Un catalogue est
 * vivant s'il rend quoi que ce soit ; ce qu'il rend et dans quelle langue est
 * une seconde question, que la répartition permet de poser.
 */
class ProviderHealthProbeTest {

    private val panier = listOf(
        MediaRef.Movie(550, "Fight Club", "1999"),
        MediaRef.Movie(77338, "Intouchables", "2011"),
        MediaRef.Movie(603, "Matrix", "1999"),
        MediaRef.Episode(1416, "Grey's Anatomy", null, 1, 1),
        MediaRef.Episode(5920, "Mentalist", null, 1, 1),
    )

    @Test
    fun probeProviders() = runBlocking {
        if (System.getProperty("moovie.probe") != "1") {
            println("[sonde catalogues] ignorée (relancer avec -Dmoovie.probe=1)")
            return@runBlocking
        }

        val porte = Semaphore(6)
        val lignes = ProviderRegistry.all.map { provider ->
            async {
                porte.withPermit {
                    var liens = 0
                    var titres = 0
                    val langues = sortedMapOf<String, Int>()
                    for (media in panier) {
                        val r = runCatching { provider.sourcesFor(media) }.getOrDefault(emptyList())
                        if (r.isNotEmpty()) titres++
                        liens += r.size
                        r.forEach { l ->
                            val cle = l.language?.takeIf { it.isNotBlank() } ?: "?"
                            langues[cle] = (langues[cle] ?: 0) + 1
                        }
                    }
                    Ligne(provider.name, titres, liens, langues)
                }
            }
        }.awaitAll()

        println("\n════════ SANTE PAR CATALOGUE ════════")
        println("%-14s %7s %7s  %s".format("catalogue", "titres", "liens", "langues"))
        lignes.sortedBy { it.nom }.forEach { l ->
            println(
                "%-14s %7d %7d  %s".format(
                    l.nom, l.titres, l.liens,
                    l.langues.entries.joinToString(" ") { "${it.key}:${it.value}" }
                        .ifEmpty { "⛔ AUCUN LIEN" },
                ),
            )
        }
        val vivants = lignes.count { it.liens > 0 }
        println("\nCATALOGUES VIVANTS $vivants/${lignes.size}")
        lignes.filter { it.liens == 0 }.forEach { println("MUET ${it.nom}") }

        if (ProbeReport.demande) {
            ProbeReport.ecris(
                "providers",
                buildString {
                    append("""{"providers":[""")
                    lignes.sortedBy { it.nom }.forEachIndexed { i, l ->
                        if (i > 0) append(',')
                        append("""{"name":${ProbeReport.texte(l.nom)},"titles":${l.titres},""")
                        append(""""links":${l.liens},"alive":${l.liens > 0},"languages":{""")
                        l.langues.entries.forEachIndexed { j, (k, v) ->
                            if (j > 0) append(',')
                            append("""${ProbeReport.texte(k)}:$v""")
                        }
                        append("}}")
                    }
                    append("""],"alive":$vivants,"total":${lignes.size}}""")
                },
            )
        }
    }

    private data class Ligne(
        val nom: String,
        val titres: Int,
        val liens: Int,
        val langues: Map<String, Int>,
    )
}
