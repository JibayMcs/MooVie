package fr.moovie.tv.ui.details

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le motif d'un panneau vide.
 *
 * Ce qui est verrouillé ici n'est pas un libellé mais une **distinction** : un
 * catalogue qui répond sans avoir le titre et un catalogue injoignable rendaient
 * jusqu'ici le même écran, « Aucune source disponible ». Le premier est une
 * réponse, le second une panne sur laquelle l'utilisateur peut agir. Confondre
 * les deux, c'est faire passer une absence normale pour une application cassée —
 * et l'inverse.
 */
class SourceDiagnosisTest {

    private fun state(
        vararg statuses: ProviderStatus,
        noProviderEnabled: Boolean = false,
    ) = SourcesState.Active(
        links = emptyList(),
        providers = statuses.mapIndexed { i, s -> ProviderProgress("p$i", s) },
        noProviderEnabled = noProviderEnabled,
    )

    @Test
    fun `tant qu un catalogue cherche il n y a rien a conclure`() {
        assertNull(diagnoseEmptySources(state(ProviderStatus.EMPTY, ProviderStatus.LOADING)))
    }

    /** Liste vide = réglages pas encore lus, pas « aucun catalogue ». */
    @Test
    fun `une liste de catalogues pas encore publiee reste en chargement`() {
        assertNull(diagnoseEmptySources(state()))
    }

    @Test
    fun `tous ont repondu sans avoir le titre`() {
        assertEquals(
            SourceDiagnosis.ABSENT,
            diagnoseEmptySources(state(ProviderStatus.EMPTY, ProviderStatus.EMPTY)),
        )
    }

    /**
     * Un catalogue peut répondre `DONE` sans que le lien survive au filtre de
     * langue : le panneau est vide, mais les catalogues ont bien répondu.
     */
    @Test
    fun `un catalogue qui a repondu compte comme ayant repondu meme en DONE`() {
        assertEquals(
            SourceDiagnosis.ABSENT,
            diagnoseEmptySources(state(ProviderStatus.DONE, ProviderStatus.EMPTY)),
        )
    }

    @Test
    fun `aucun n a repondu est une panne, pas une absence`() {
        assertEquals(
            SourceDiagnosis.UNREACHABLE,
            diagnoseEmptySources(state(ProviderStatus.FAILED, ProviderStatus.FAILED)),
        )
    }

    @Test
    fun `un melange se dit comme un melange`() {
        assertEquals(
            SourceDiagnosis.PARTIAL,
            diagnoseEmptySources(state(ProviderStatus.EMPTY, ProviderStatus.FAILED)),
        )
    }

    /**
     * Tout désactiver est possible depuis les réglages, et le panneau tournait
     * alors indéfiniment : la liste vide se lisait comme « pas encore publiée ».
     */
    @Test
    fun `aucun catalogue active ne tourne pas indefiniment`() {
        val vide = state(noProviderEnabled = true)

        assertFalse(vide.anyLoading, "une recherche jamais lancée n'est pas en cours")
        assertEquals(SourceDiagnosis.NONE_ENABLED, diagnoseEmptySources(vide))
    }

    /** Le drapeau ne doit pas casser le cas nominal, qui dépend d'anyLoading. */
    @Test
    fun `le cas nominal reste en chargement tant qu un catalogue cherche`() {
        assertTrue(state(ProviderStatus.LOADING).anyLoading)
        assertTrue(state().anyLoading)
        assertFalse(state(ProviderStatus.EMPTY).anyLoading)
    }
}
