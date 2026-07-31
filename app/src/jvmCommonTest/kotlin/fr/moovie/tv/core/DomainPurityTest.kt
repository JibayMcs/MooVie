package fr.moovie.tv.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Garde-fou de la frontière hexagonale : le domaine (`fr.moovie.tv.core`) ne
 * doit dépendre d'aucune plateforme ni d'aucune bibliothèque d'infrastructure.
 *
 * **Pourquoi un test et non le compilateur.** On pourrait croire que placer le
 * domaine dans `commonMain` suffit à l'interdire. C'est vrai d'un projet KMP qui
 * vise au moins une cible non-JVM : la compilation des métadonnées communes
 * échoue alors sur un import JVM. Ici les deux cibles (Android et desktop) sont
 * des JVM, `compileCommonMainKotlinMetadata` est **SKIPPED**, et `commonMain`
 * est compilé avec le classpath complet de chaque cible — un `import okhttp3.…`
 * dans le domaine passe donc sans broncher. Vérifié, pas supposé.
 *
 * Ce test est la vraie barrière. Le jour où un module Gradle dédié isolera le
 * domaine, il deviendra redondant et pourra disparaître.
 */
class DomainPurityTest {

    private val forbidden = listOf(
        "okhttp3", "okio", "org.jsoup", "retrofit2",
        "android.", "androidx.", "java.", "javax.",
        "kotlinx.coroutines.Dispatchers",
    )

    @Test
    fun `le domaine n'importe aucune infrastructure`() {
        val root = domainRoot()
        val sources = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

        assertTrue(sources.isNotEmpty(), "aucune source trouvée sous $root — chemin à corriger")

        val offenses = sources.flatMap { file ->
            file.readLines()
                .filter { it.startsWith("import ") }
                .filter { line -> forbidden.any { line.removePrefix("import ").startsWith(it) } }
                .map { "${file.name}: $it" }
        }

        assertTrue(
            offenses.isEmpty(),
            "Le domaine doit rester pur — déplacez ce code dans un adaptateur :\n" +
                offenses.joinToString("\n"),
        )
    }

    /** Remonte l'arborescence jusqu'à trouver les sources du domaine. */
    private fun domainRoot(): File {
        val relative = "src/commonMain/kotlin/fr/moovie/tv/core"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            File(dir, relative).takeIf { it.isDirectory }?.let { return it }
            File(dir, "app/$relative").takeIf { it.isDirectory }?.let { return it }
            dir = dir.parentFile
        }
        error("sources du domaine introuvables depuis ${File(".").absolutePath}")
    }
}
