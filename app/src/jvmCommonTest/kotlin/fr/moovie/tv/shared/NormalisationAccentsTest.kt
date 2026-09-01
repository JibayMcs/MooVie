package fr.moovie.tv.shared

import java.text.Normalizer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `FstreamProvider.normalizeTitle` retirait les diacritiques avec `\p{Mn}`, une
 * classe de catégorie Unicode que la JVM comprend mais que le moteur
 * d'expressions régulières de Kotlin/Native ne garantit pas. Elle a été
 * remplacée par un filtrage sur des plages de code explicites.
 *
 * Le risque d'un tel remplacement est de ne pas retirer exactement les mêmes
 * caractères — et une classe muette n'aurait rien signalé, elle aurait
 * simplement laissé les accents en place et fait rater des correspondances de
 * titre. Ce test compare donc les deux méthodes sur des titres réellement
 * accentués.
 */
class NormalisationAccentsTest {

    /** Les plages retenues dans `normalizeTitle`. */
    private fun sansMarquesParPlages(s: String): String =
        enNfd(s).filterNot { c ->
            c.code in 0x0300..0x036F || c.code in 0x1AB0..0x1AFF ||
                c.code in 0x1DC0..0x1DFF || c.code in 0x20D0..0x20FF ||
                c.code in 0xFE20..0xFE2F
        }

    /** La méthode d'origine, propre à la JVM. */
    private fun sansMarquesParCategorie(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "")

    private val titres = listOf(
        "Amélie",
        "Le Fabuleux Destin d'Amélie Poulain",
        "Les Misérables",
        "À bout de souffle",
        "Le Cinquième Élément",
        "La Cité de la peur",
        "Astérix & Obélix",
        "Rrrrrrr!!!",
        "L'Auberge espagnole",
        "Être et avoir",
        "Ça",
        "Coup de tête",
        "Naruto Shippūden",
        "Pokémon",
        "Spider-Man: Across the Spider-Verse",
    )

    @Test
    fun `le filtrage par plages retire les memes caracteres que la categorie Mn`() {
        for (titre in titres) {
            assertEquals(
                sansMarquesParCategorie(titre),
                sansMarquesParPlages(titre),
                "titre=$titre",
            )
        }
    }

    @Test
    fun `enNfd decompose bien les caracteres accentues`() {
        // « é » précomposé (U+00E9) doit devenir « e » + accent combinant (U+0301).
        assertEquals(2, enNfd("é").length)
        assertEquals('e', enNfd("é")[0])
        assertEquals(0x0301, enNfd("é")[1].code)
    }
}
