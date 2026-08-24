package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * SwiftFlow, de la requête d'API jusqu'à la jouabilité — **par notre code**.
 *
 * ```
 * ./gradlew :app:desktopTest --tests '*SwiftFlowHealthProbeTest' -Dmoovie.probe=1
 * ```
 *
 * ## Pourquoi une sonde de plus
 *
 * Le contrôle rapide dit « 9/9 jouables » et ne nomme jamais swiftflow dans la
 * colonne du gagnant : la cascade trouve toujours quelqu'un d'autre. Ce chiffre
 * global masque donc exactement ce qu'on cherche — un provider qui rend des
 * liens que personne ne sait résoudre reste invisible tant qu'un autre prend le
 * relais.
 *
 * Celle-ci suit **un seul provider** et imprime, pour chaque lien, quel
 * extracteur l'a revendiqué et quels en-têtes il a posés. C'est ce qui distingue
 * les trois pannes possibles :
 *
 * - l'API ne rend plus rien → le provider est muet ;
 * - elle rend des liens que `canHandle` ne reconnaît plus → un autre extracteur
 *   les ramasse, sans les en-têtes qu'il fallait ;
 * - tout est reconnu mais le CDN refuse → la sonde de jouabilité le dit.
 */
class SwiftFlowHealthProbeTest {

    private val panier = listOf(
        "Fight Club" to MediaRef.Movie(550, "Fight Club", "1999"),
        "Intouchables" to MediaRef.Movie(77338, "Intouchables", "2011"),
        "Matrix" to MediaRef.Movie(603, "Matrix", "1999"),
        "Mentalist S1E1" to MediaRef.Episode(5920, "Mentalist", null, 1, 1),
    )

    @Test
    fun probeSwiftFlow() = runBlocking {
        if (System.getProperty("moovie.probe") != "1") {
            println("[sonde swiftflow] ignorée (relancer avec -Dmoovie.probe=1)")
            return@runBlocking
        }

        val provider = ProviderRegistry.all.firstOrNull { it.name == "swiftflow" }
        if (provider == null) {
            println("[sonde] ⚠️ provider swiftflow absent du registre")
            return@runBlocking
        }

        var liens = 0
        var revendiques = 0
        var resolus = 0
        var jouables = 0

        for ((nom, media) in panier) {
            println("\n──── $nom ────")
            val trouves = runCatching { provider.sourcesFor(media) }.getOrDefault(emptyList())
            if (trouves.isEmpty()) {
                println("  ⚠️ aucun lien rendu par l'API")
                continue
            }
            for (lien in trouves.take(4)) {
                liens++
                val hote = runCatching { java.net.URI(lien.url).host }.getOrNull() ?: "?"
                val extracteur = ExtractorRegistry.extractorFor(lien.url)
                if (extracteur != null) revendiques++
                println("  hôte      : $hote  (${lien.language ?: "?"})")
                println("  revendiqué: ${extracteur?.hoster ?: "❌ PERSONNE — repli par reniflage"}")

                val flux = runCatching { ExtractorRegistry.resolve(lien) }.getOrNull()
                if (flux == null) {
                    println("  résolu    : ❌ non")
                    continue
                }
                resolus++
                println("  en-têtes  : ${flux.headers.keys.sorted()}")
                val joue = runCatching { isStreamPlayable(flux) }.getOrDefault(false)
                if (joue) jouables++
                println("  jouable   : ${if (joue) "✅" else "⛔"}")
            }
        }

        println("\nLIENS $liens   REVENDIQUES $revendiques   RESOLUS $resolus   JOUABLES $jouables")
        println(
            when {
                liens == 0 -> "➜ l'API ne rend plus rien : le provider est muet."
                revendiques < liens ->
                    "➜ des liens ne sont revendiqués par personne : canHandle ne suit plus " +
                        "les domaines du CDN."
                jouables < resolus -> "➜ résolus mais refusés : le CDN a changé ses exigences."
                else -> "➜ SwiftFlow est sain."
            },
        )
    }
}
