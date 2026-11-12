package fr.moovie.tv.exit

import fr.moovie.tv.ui.adaptive.UiFlavor
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Les deux décisions de la sortie réelle, hors d'Android.
 *
 * Ce qu'elles protègent ne se voit qu'à l'usage : une fenêtre trop large et
 * l'on quitte en voulant revenir ; un compteur faux et l'application se ferme
 * en ouvrant le lecteur, ou pendant que l'installeur écrit la mise à jour.
 */
class ExitRulesTest {

    // ── Double Retour ────────────────────────────────────────────────────

    @Test
    fun firstPressOnlyArms() {
        assertFalse(DoubleBackGate().press(1_000))
    }

    @Test
    fun secondPressWithinTwoSecondsQuits() {
        val gate = DoubleBackGate()
        gate.press(1_000)
        assertTrue(gate.press(2_500))
    }

    @Test
    fun windowEdgeIsInclusive() {
        val gate = DoubleBackGate()
        gate.press(1_000)
        assertTrue(gate.press(3_000))
    }

    @Test
    fun lateSecondPressRearmsInsteadOfQuitting() {
        val gate = DoubleBackGate()
        gate.press(1_000)
        assertFalse(gate.press(3_001), "trop tard : réarme")
        assertTrue(gate.press(4_000), "le réarmement compte comme un premier appui")
    }

    @Test
    fun quittingConsumesTheArm() {
        val gate = DoubleBackGate()
        gate.press(1_000)
        assertTrue(gate.press(1_500))
        assertFalse(gate.press(1_800), "un troisième appui repart de zéro")
    }

    @Test
    fun resetForgetsThePendingPress() {
        val gate = DoubleBackGate()
        gate.press(1_000)
        gate.reset()
        assertFalse(gate.press(1_500))
    }

    // ── Passage en arrière-plan ──────────────────────────────────────────

    private fun launched(): BackgroundExitPolicy = BackgroundExitPolicy().apply {
        activityStarted()
        activityResumed()
    }

    @Test
    fun tvLeavingForAnotherAppQuits() {
        assertTrue(launched().activityStopped(changingConfigurations = false, flavor = UiFlavor.TV, nowMs = 0L))
    }

    @Test
    fun phoneNeverQuits() {
        assertFalse(launched().activityStopped(changingConfigurations = false, flavor = UiFlavor.TOUCH, nowMs = 0L))
    }

    @Test
    fun openingThePlayerActivityIsNotLeaving() {
        val policy = launched()
        // MainActivity lance PlayerActivity : la seconde démarre avant que la
        // première ne s'arrête.
        policy.activityStarted()
        policy.activityResumed()
        assertFalse(policy.activityStopped(false, UiFlavor.TV, nowMs = 0L))
        // Retour du lecteur : MainActivity redémarre avant que PlayerActivity
        // ne s'arrête.
        policy.activityStarted()
        policy.activityResumed()
        assertFalse(policy.activityStopped(false, UiFlavor.TV, nowMs = 0L))
        // Et l'application reste fermable ensuite.
        assertTrue(policy.activityStopped(false, UiFlavor.TV, nowMs = 0L))
    }

    @Test
    fun configurationChangeIsNotLeaving() {
        val policy = launched()
        assertFalse(policy.activityStopped(changingConfigurations = true, flavor = UiFlavor.TV, nowMs = 0L))
        policy.activityStarted()
        policy.activityResumed()
        assertTrue(policy.activityStopped(false, UiFlavor.TV, nowMs = 0L))
    }

    @Test
    fun updateInstallerOrVoiceSearchIsNotLeaving() {
        val policy = launched()
        policy.expectReturn(0L)
        assertFalse(policy.activityStopped(false, UiFlavor.TV, nowMs = 0L))
    }

    @Test
    fun comingBackFromTheExternalScreenRestoresTheRule() {
        val policy = launched()
        policy.expectReturn(0L)
        policy.activityStopped(false, UiFlavor.TV, nowMs = 0L)
        policy.activityStarted()
        policy.activityResumed()
        assertTrue(policy.activityStopped(false, UiFlavor.TV, nowMs = 0L))
    }

    @Test
    fun translucentPermissionDialogOnlyPausesAndStillClearsTheException() {
        val policy = launched()
        policy.expectReturn(0L)
        // Boîte translucide : pause puis reprise, sans arrêt.
        policy.activityResumed()
        assertTrue(policy.activityStopped(false, UiFlavor.TV, nowMs = 0L))
    }

    @Test
    fun failedLaunchCancelsTheException() {
        val policy = launched()
        policy.expectReturn(0L)
        policy.cancelReturn()
        assertTrue(policy.activityStopped(false, UiFlavor.TV, nowMs = 0L))
    }

    @Test
    fun exceptionStillHoldsAtTheWindowEdge() {
        val policy = launched()
        policy.expectReturn(1_000L)
        assertFalse(policy.activityStopped(false, UiFlavor.TV, nowMs = 1_000L + BackgroundExitPolicy.ATTENTE_RETOUR_MS))
    }

    @Test
    fun exceptionExpiresWhenNothingOpens() {
        // Le lancement n'ouvre aucun écran : personne ne revient jamais. Passé
        // le délai, Retour à l'accueil doit de nouveau fermer le téléviseur.
        val policy = launched()
        policy.expectReturn(1_000L)
        assertTrue(policy.activityStopped(false, UiFlavor.TV, nowMs = 1_001L + BackgroundExitPolicy.ATTENTE_RETOUR_MS))
    }

    @Test
    fun aNewLaunchRestartsTheWindow() {
        val policy = launched()
        policy.expectReturn(0L)
        policy.expectReturn(20_000L)
        assertFalse(policy.activityStopped(false, UiFlavor.TV, nowMs = 25_000L))
    }
}
