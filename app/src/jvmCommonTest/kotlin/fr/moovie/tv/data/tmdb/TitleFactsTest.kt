package fr.moovie.tv.data.tmdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TitleFactsTest {

    // ── Classification ────────────────────────────────────────────────────

    private fun ratings(vararg pairs: Pair<String, String>) =
        ContentRatingResults(pairs.map { ContentRating(country = it.first, rating = it.second) })

    @Test
    fun `la classification du pays de l'utilisateur prime`() {
        val r = ratings("US" to "TV-MA", "FR" to "16", "GB" to "18")
        assertEquals("16", r.forCountry("FR"))
        assertEquals("TV-MA", r.forCountry("US"))
    }

    @Test
    fun `a defaut du pays on prend les Etats-Unis`() {
        // TMDB renseigne presque toujours les États-Unis, rarement le reste.
        assertEquals("TV-MA", ratings("US" to "TV-MA", "GB" to "18").forCountry("FR"))
    }

    @Test
    fun `sans le pays ni les Etats-Unis on n'invente pas`() {
        // « 18 » britannique affiché à un public français ne veut rien dire.
        assertNull(ratings("GB" to "18", "DE" to "16").forCountry("FR"))
        assertNull(null as ContentRatingResults?)?.let { }
        assertNull((null as ContentRatingResults?).forCountry("FR"))
    }

    @Test
    fun `une classification vide est ignoree et non rendue`() {
        assertEquals("16", ratings("FR" to "", "US" to "16").forCountry("FR"))
    }

    @Test
    fun `cote films la classification vient des dates de sortie`() {
        val r = ReleaseDateResults(
            listOf(
                ReleaseDateCountry("US", listOf(ReleaseDateEntry("PG-13", 3))),
                ReleaseDateCountry(
                    "FR",
                    // La première entrée n'a pas de classification : c'est le cas
                    // réel d'une sortie en salle non classée suivie d'une autre.
                    listOf(ReleaseDateEntry("", 1), ReleaseDateEntry("-12", 3)),
                ),
            ),
        )
        assertEquals("-12", r.forCountry("FR"))
        assertEquals("PG-13", r.forCountry("US"))
    }

    // ── Formats ───────────────────────────────────────────────────────────

    @Test
    fun `les sommes sont groupees par milliers`() {
        assertEquals("150\u202F000\u202F000\u202F$", formatMoney(150_000_000))
        assertEquals("999\u202F$", formatMoney(999))
        assertEquals("1\u202F234\u202F$", formatMoney(1234))
    }

    @Test
    fun `une somme nulle n'est pas une somme`() {
        // TMDB rend 0 pour « non renseigné » : la ligne doit disparaître.
        assertNull(formatMoney(0))
        assertNull(formatMoney(-5))
    }

    @Test
    fun `les durees passent en heures au-dela d'une heure`() {
        assertEquals("47 min", formatRuntime(47))
        assertEquals("2 h 17", formatRuntime(137))
        // Zéro minute de reste doit rester sur deux chiffres, pas « 2 h 0 ».
        assertEquals("2 h 00", formatRuntime(120))
        assertNull(formatRuntime(0))
        assertNull(formatRuntime(null))
    }

    @Test
    fun `les dates ISO passent au format francais`() {
        assertEquals("11/08/2026", formatDate("2026-08-11"))
    }

    @Test
    fun `une date incomplete est rendue telle quelle plutot que perdue`() {
        assertEquals("2026", formatDate("2026"))
        assertNull(formatDate(""))
        assertNull(formatDate(null))
    }
}
