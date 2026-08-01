package fr.moovie.tv.data.sources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Étiquettes de langue de l'API frembed — fonction pure, aucun réseau.
 *
 * C'est ce qui décide si un lien alimente la cascade VF ou la cascade VOSTFR.
 * Une erreur ici ne casse rien visiblement : elle sert simplement la mauvaise
 * langue à l'utilisateur, ce qui est précisément le défaut qu'on cherche à
 * corriger depuis le début.
 */
class FrembedLanguageTest {

    @Test
    fun `les étiquettes de l'API sont alignées sur celles de la cascade`() {
        assertEquals("VF", FrembedProvider.languageOf("vf"))
        assertEquals("VOSTFR", FrembedProvider.languageOf("vostfr"))
        assertEquals("VO", FrembedProvider.languageOf("vo"))
    }

    @Test
    fun `la casse ne change rien`() {
        assertEquals("VF", FrembedProvider.languageOf("VF"))
        assertEquals("VOSTFR", FrembedProvider.languageOf("VoStFr"))
    }

    @Test
    fun `les variantes annoncées par l'API publique sont reconnues`() {
        // L'API publique v1 étiquette « TrueFrench » là où l'API interne dit « vf ».
        assertEquals("VF", FrembedProvider.languageOf("TrueFrench"))
        assertEquals("VF", FrembedProvider.languageOf("french"))
    }

    @Test
    fun `une étiquette inconnue ne devient pas du VF par défaut`() {
        // Mieux vaut un lien sans langue — que la cascade tentera en dernier
        // recours — qu'un lien annoncé VF qui ne l'est pas.
        assertNull(FrembedProvider.languageOf("vkr"))
        assertNull(FrembedProvider.languageOf(""))
        assertNull(FrembedProvider.languageOf(null))
    }
}
