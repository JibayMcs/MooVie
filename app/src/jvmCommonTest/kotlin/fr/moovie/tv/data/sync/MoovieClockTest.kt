package fr.moovie.tv.data.sync

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoovieClockTest {

    @BeforeTest
    fun clean() = MoovieClock.reset()

    @AfterTest
    fun restore() = MoovieClock.reset()

    /**
     * Deux décisions dans la même milliseconde restent ordonnées. Sans ça, la
     * fusion tomberait sur l'égalité et referait l'union, c'est-à-dire
     * ressusciterait ce qu'on vient de retirer.
     */
    @Test
    fun `deux horodatages successifs ne sont jamais egaux`() {
        val stamps = List(1_000) { MoovieClock.now() }

        assertEquals(stamps.size, stamps.toSet().size)
        assertEquals(stamps.sorted(), stamps)
    }

    /**
     * Le cas causal : après avoir lu la décision d'un appareil en avance, la
     * nôtre doit la dépasser — sinon on se ferait écraser par ce qu'on vient
     * justement de corriger.
     */
    @Test
    fun `une decision prise apres en avoir lu une la depasse`() {
        val remote = System.currentTimeMillis() + 60_000

        MoovieClock.observe(remote)

        assertTrue(MoovieClock.now() > remote)
    }

    /**
     * Un seul appareil dont l'horloge est en 2038 ne doit pas emporter celle de
     * tous les autres, définitivement.
     */
    @Test
    fun `un horodatage aberrant est ignore`() {
        val absurd = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000

        MoovieClock.observe(absurd)

        assertTrue(MoovieClock.now() < absurd)
    }

    /** Un horodatage distant plus ancien que le nôtre ne nous fait pas reculer. */
    @Test
    fun `un horodatage distant plus ancien ne recule pas l horloge`() {
        val before = MoovieClock.now()

        MoovieClock.observe(1_000)

        assertTrue(MoovieClock.now() > before)
    }

    /** La correction mesurée face au dépôt s'applique aux écritures suivantes. */
    @Test
    fun `la correction decale les horodatages`() {
        val plain = MoovieClock.now()
        MoovieClock.correctBy(10 * 60_000)

        assertTrue(MoovieClock.now() > plain + 9 * 60_000)
    }

    /** Un horodatage reste un epoch en millisecondes : rien à réinterpréter. */
    @Test
    fun `un horodatage reste un epoch lisible`() {
        val now = MoovieClock.now()

        assertTrue(now > 1_700_000_000_000)
        assertTrue(now < 4_000_000_000_000)
    }
}
