package fr.moovie.tv.core.sources.model

import fr.moovie.tv.ui.navigation.Screen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayableStreamAudioLanguageTest {
    @Test
    fun sansLangueDemandeeLeFluxNeChangePas() {
        val s = PlayableStream("https://x/a.m3u8", StreamFormat.HLS)
        assertNull(s.audioLanguage)
        assertNull(Screen.Player(streamUrl = s.url).audioLanguage)
    }

    @Test
    fun laLangueSurvitALaCopieEtALaNavigation() {
        val s = PlayableStream("https://x/a.m3u8", StreamFormat.HLS, audioLanguage = "en")
        assertEquals("en", s.copy(quality = "1080p").audioLanguage)
        assertEquals("en", Screen.Player(streamUrl = s.url, audioLanguage = s.audioLanguage).audioLanguage)
    }
}
