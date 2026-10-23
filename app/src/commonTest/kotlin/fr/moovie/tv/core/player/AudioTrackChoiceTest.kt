package fr.moovie.tv.core.player

import fr.moovie.tv.ui.player.PlayerTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioTrackChoiceTest {

    private fun piste(
        id: String,
        language: String?,
        title: String = "",
        channels: Int = 2,
        isDefault: Boolean = false,
        supported: Boolean = true,
        selected: Boolean = false,
    ) = PlayerTrack(
        id = id,
        label = title.ifBlank { language.orEmpty() },
        selected = selected,
        language = language,
        title = title,
        channels = channels,
        isDefault = isDefault,
        supported = supported,
        audioDescription = isAudioDescription(title),
    )

    /** Le fichier mesuré le 10/10/2026 sur La cocinera de Castamar. */
    private val castamar = listOf(
        piste("0:0", "de", "German"),
        piste("0:1", "en", "British English"),
        piste("0:2", "fr", "French"),
        piste("0:3", "tr", "Turkish"),
        piste("0:4", "en", "", channels = 6),
        piste("0:5", "es", "European Spanish - Audio Description", selected = true),
    )

    @Test
    fun `l'audiodescription n'est jamais prise, meme dans la langue voulue`() {
        val choix = chooseAudioTrack(castamar, listOf("es"))
        assertEquals("0:4", choix?.id, "à défaut d'espagnol ordinaire, la piste la plus riche en canaux")
    }

    @Test
    fun `la langue voulue l'emporte quand elle a une piste ordinaire`() {
        assertEquals("0:2", chooseAudioTrack(castamar, listOf("fr"))?.id)
    }

    @Test
    fun `a langue egale, la piste la plus riche en canaux`() {
        assertEquals("0:4", chooseAudioTrack(castamar, listOf("en"))?.id)
    }

    @Test
    fun `la piste par defaut du fichier passe devant le nombre de canaux sans langue voulue`() {
        val pistes = listOf(
            piste("0:0", "ja", "Japanese", channels = 2, isDefault = true),
            piste("0:1", "en", "English", channels = 6),
        )
        assertEquals("0:0", chooseAudioTrack(pistes, emptyList())?.id)
    }

    @Test
    fun `une piste non decodable ne compte pas`() {
        val pistes = listOf(
            piste("0:0", "ja", "Japanese TrueHD", channels = 6, supported = false),
            piste("0:1", "ja", "Japanese", channels = 2),
        )
        assertEquals("0:1", chooseAudioTrack(pistes, listOf("ja"))?.id)
    }

    @Test
    fun `rien a choisir quand tout est audiodescription ou indecodable`() {
        val pistes = listOf(
            piste("0:0", "fr", "Audiodescription"),
            piste("0:1", "fr", "French", supported = false),
        )
        assertNull(chooseAudioTrack(pistes, listOf("fr")))
    }

    @Test
    fun `les codes a trois lettres et regionaux rejoignent la langue voulue`() {
        val pistes = listOf(piste("0:0", "en-GB", "English"), piste("0:1", "spa", "Castellano"))
        assertEquals("0:1", chooseAudioTrack(pistes, listOf("es"))?.id)
    }

    @Test
    fun `les titres d'audiodescription sont reconnus, les autres non`() {
        listOf(
            "European Spanish - Audio Description", "AD", "Audiodescription", "French (AD)",
            "Descriptive Audio", "Audiodeskription", "Director's Commentary", "English VI",
        ).forEach { assertTrue(isAudioDescription(it), it) }
        listOf("French", "Japanese 5.1", "Madrid", "Advanced", "VF", "Original").forEach {
            assertFalse(isAudioDescription(it), it)
        }
    }
}
