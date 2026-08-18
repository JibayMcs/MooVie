package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.usecase.MEASURE_DEAD_TTL_MS
import fr.moovie.tv.core.sources.usecase.MEASURE_OK_TTL_MS
import fr.moovie.tv.data.store.useFileStores
import fr.moovie.tv.data.store.useInMemoryStores
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le magasin des mesures de qualité, de bout en bout.
 *
 * `SourceCacheFreshnessTest` verrouille la **règle** de péremption ; celui-ci
 * vérifie que le dépôt l'applique vraiment, qu'un aller-retour disque rend ce
 * qu'on y a mis, et que la purge ne mange pas les entrées récentes. Autant de
 * choses que la règle seule ne dit pas.
 *
 * Il ne tient debout que grâce au magasin en mémoire — c'est exactement le
 * déblocage qu'on en attendait au-delà de `syncFingerprint`.
 */
class StreamMeasureRepositoryTest {

    private val now = 1_800_000_000_000L

    @BeforeTest
    fun fresh() = useInMemoryStores()

    @AfterTest
    fun restore() = useFileStores()

    @Test
    fun `une mesure ecrite se relit telle quelle`() = runTest {
        val repo = StreamMeasureRepository()
        repo.put("https://vidzy.org/e/abc", listOf(1080, 720, 480), playable = true, now = now)

        val mesure = repo.all(now)["https://vidzy.org/e/abc"]

        assertEquals(listOf(1080, 720, 480), mesure?.heights)
        assertEquals(true, mesure?.playable)
    }

    /**
     * **Le gain recherché.** Sans ça, rouvrir une fiche de quinze sources après
     * un redémarrage relance quinze extractions pour retrouver des hauteurs déjà
     * connues.
     */
    @Test
    fun `plusieurs liens se relisent en une seule lecture`() = runTest {
        val repo = StreamMeasureRepository()
        repeat(15) { i -> repo.put("https://h.tld/e/$i", listOf(1080), playable = true, now = now) }

        val tout = repo.all(now)

        assertEquals(15, tout.size)
        assertTrue(tout.values.all { it.heights == listOf(1080) })
    }

    @Test
    fun `une mesure jouable survit a la journee mais pas au-dela`() = runTest {
        val repo = StreamMeasureRepository()
        repo.put("https://h.tld/e/1", listOf(1080), playable = true, now = now)

        assertEquals(1, repo.all(now + MEASURE_OK_TTL_MS - 1).size)
        assertEquals(0, repo.all(now + MEASURE_OK_TTL_MS + 1).size)
    }

    /**
     * **Le test qui compte.** Un verdict d'échec gardé comme une mesure
     * laisserait la source grisée au lancement suivant, sans recours visible :
     * il faudrait vider tout le cache des sources en ayant deviné le rapport.
     * La sonde a des faux négatifs connus — un `HEAD` refusé suffit.
     */
    @Test
    fun `un echec s oublie de lui-meme quand une mesure tiendrait encore`() = runTest {
        val repo = StreamMeasureRepository()
        repo.put("https://mort.tld/e/1", emptyList(), playable = false, now = now)
        repo.put("https://vivant.tld/e/1", listOf(720), playable = true, now = now)

        val apres = repo.all(now + MEASURE_DEAD_TTL_MS + 1)

        assertNull(apres["https://mort.tld/e/1"], "l'echec doit etre re-sonde")
        assertEquals(listOf(720), apres["https://vivant.tld/e/1"]?.heights)
    }

    @Test
    fun `un echec recent evite de re-sonder`() = runTest {
        val repo = StreamMeasureRepository()
        repo.put("https://mort.tld/e/1", emptyList(), playable = false, now = now)

        val mesure = repo.all(now + MEASURE_DEAD_TTL_MS - 1)["https://mort.tld/e/1"]

        assertEquals(false, mesure?.playable)
    }

    /**
     * Le vidage des réglages doit emporter les deux magasins : n'en vider qu'un
     * laisserait les sources reclassées sur des hauteurs d'avant.
     */
    @Test
    fun `vider n en laisse aucune`() = runTest {
        val repo = StreamMeasureRepository()
        repeat(5) { i -> repo.put("https://h.tld/e/$i", listOf(1080), playable = true, now = now) }

        repo.clear()

        assertEquals(emptyMap(), repo.all(now))
    }

    /**
     * La purge garde les plus récentes. Une purge qui trierait à l'envers
     * viderait le magasin de ce qu'on vient d'apprendre, et le symptôme serait
     * une lenteur qui revient sans raison au bout d'un moment d'usage.
     */
    @Test
    fun `la purge garde les mesures les plus recentes`() = runTest {
        val repo = StreamMeasureRepository()
        // Au-delà du plafond, en datant chacune pour que l'ordre soit décidable.
        repeat(420) { i ->
            repo.put("https://h.tld/e/$i", listOf(i), playable = true, now = now + i)
        }

        val restantes = repo.all(now + 420)

        assertTrue(restantes.size <= 400, "plafond depasse : ${restantes.size}")
        assertTrue(
            "https://h.tld/e/419" in restantes,
            "la derniere mesure ecrite doit survivre a la purge",
        )
        assertTrue(
            "https://h.tld/e/0" !in restantes,
            "la plus ancienne devait partir la premiere",
        )
    }
}
