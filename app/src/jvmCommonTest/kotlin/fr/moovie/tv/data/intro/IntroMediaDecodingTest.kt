package fr.moovie.tv.data.intro

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Décodage de la réponse /media avec la configuration du client
 * ([IntroDbRepository]) sur des réponses v4 réelles : segments intro et
 * credits, champs inconnus ignorés, absences et valeurs nulles tolérées.
 */
class IntroMediaDecodingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Une réponse sans `tmdb_id` (absent en v4) ne doit pas faire échouer le décodage. */
    @Test
    fun `une reponse sans tmdb_id se decode et garde les segments`() {
        val media = json.decodeFromString<IntroMedia>(
            """{"tvdb_id": 81189, "intro": [{"start_ms": 0, "end_ms": 90000}], "credits": [{"start_ms": 1380000}]}""",
        )

        assertEquals(listOf(Segment(startMs = 0, endMs = 90_000)), media.intro)
        assertEquals(listOf(Segment(startMs = 1_380_000, endMs = null)), media.credits)
    }

    /** Une clé inconnue (recap, preview…) est ignorée, pas fatale. */
    @Test
    fun `une cle inconnue est ignoree`() {
        val media = json.decodeFromString<IntroMedia>(
            """{"recap": [{"start_ms": 5, "end_ms": 10}], "intro": [{"end_ms": 60000}]}""",
        )

        assertEquals(listOf(Segment(startMs = null, endMs = 60_000)), media.intro)
        assertEquals(emptyList(), media.credits)
    }

    /** Pas de données : les listes absentes valent vide, et rien n'est inventé. */
    @Test
    fun `une reponse vide donne des listes vides`() {
        val media = json.decodeFromString<IntroMedia>("{}")

        assertEquals(emptyList(), media.intro)
        assertEquals(emptyList(), media.credits)
        assertNull(media.intro.firstOrNull())
    }

    /** Réponse v4 réelle d'un épisode (tmdb_id=1396, S1E1). */
    @Test
    fun `reponse v4 reelle d un episode`() {
        val media = json.decodeFromString<IntroMedia>(
            """{"tvdb_id":81189,"type":"tv","season":1,"episode":1,"intro":[{"start_ms":228664,"end_ms":246143}],"credits":[{"start_ms":3431000,"end_ms":null}]}""",
        )

        assertEquals(listOf(Segment(228_664, 246_143)), media.intro)
        assertEquals(listOf(Segment(3_431_000, null)), media.credits)
    }

    /** Réponse v4 réelle d'un film (tmdb_id=550) : intro sans début. */
    @Test
    fun `reponse v4 reelle d un film`() {
        val media = json.decodeFromString<IntroMedia>(
            """{"tvdb_id":247,"type":"movie","intro":[{"start_ms":null,"end_ms":119000}],"credits":[{"start_ms":8177000,"end_ms":8348000}]}""",
        )

        assertEquals(listOf(Segment(null, 119_000)), media.intro)
        assertEquals(listOf(Segment(8_177_000, 8_348_000)), media.credits)
    }

    /** La lecture vise /v4 et s'adresse par tmdb_id, saison et épisode. */
    @Test
    fun `l url de lecture est en v4 avec tmdb_id season episode`() {
        assertEquals(
            "https://api.theintrodb.org/v4/media?tmdb_id=1396&season=1&episode=1",
            urlMedia(1396, isTv = true, season = 1, episode = 1, durationMs = 0),
        )
        assertEquals(
            "https://api.theintrodb.org/v4/media?tmdb_id=550",
            urlMedia(550, isTv = false, season = 0, episode = 0, durationMs = 0),
        )
    }
}
