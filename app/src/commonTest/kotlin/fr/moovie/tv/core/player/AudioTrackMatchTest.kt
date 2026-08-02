package fr.moovie.tv.core.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioTrackMatchTest {

    @Test
    fun `libelle identique`() {
        assertEquals("French", matchAudioTrack("French", listOf("English", "French")))
    }

    @Test
    fun `casse et accents ignores`() {
        assertEquals("Français", matchAudioTrack("francais", listOf("English", "Français")))
        assertEquals("FRENCH", matchAudioTrack("french", listOf("FRENCH", "English")))
    }

    /** Un épisode annonce « French », le suivant « French AC3 5.1 ». */
    @Test
    fun `suffixe technique tolere`() {
        assertEquals("French AC3 5.1", matchAudioTrack("French", listOf("English", "French AC3 5.1")))
        assertEquals("French", matchAudioTrack("French AC3 5.1", listOf("English", "French")))
    }

    /** Code court d'un côté, nom complet de l'autre. */
    @Test
    fun `code langue et nom complet se rejoignent`() {
        assertEquals("Français", matchAudioTrack("fr", listOf("Français", "English")))
        assertEquals("fr", matchAudioTrack("Français", listOf("en", "fr")))
    }

    @Test
    fun `la ponctuation ne gene pas`() {
        assertEquals("Français (VF)", matchAudioTrack("Francais VF", listOf("English", "Français (VF)")))
    }

    /**
     * Rien de comparable : mieux vaut ne pas toucher que d'imposer une piste
     * fausse — l'utilisateur se retrouverait en VO sans l'avoir demandé.
     */
    @Test
    fun `aucune correspondance rend null`() {
        assertNull(matchAudioTrack("French", listOf("English", "Japanese")))
        assertNull(matchAudioTrack("French", emptyList()))
        assertNull(matchAudioTrack("", listOf("French")))
    }

    /** Un libellé qui ne porte aucune lettre ne dit rien : on n'en tire pas de règle. */
    @Test
    fun `libelles vides ignores`() {
        assertNull(matchAudioTrack("---", listOf("###", "***")))
    }

    /** L'égalité l'emporte sur l'inclusion, même si elle vient plus loin. */
    @Test
    fun `la correspondance exacte est preferee`() {
        assertEquals("French", matchAudioTrack("French", listOf("French AC3", "French")))
    }
}
