package fr.moovie.tv.data.backup

import fr.moovie.tv.data.watch.ResumeEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Le format 2 : ce qu'il porte, et ce qu'il doit encore savoir lire. */
class BackupProfilesTest {

    private fun resume(key: String, at: Long) =
        ResumeEntry(key = key, tmdbId = 1, isTv = false, positionMs = 1_000, updatedAt = at)

    private val twoProfiles = MoovieBackup(
        exportedAt = 1_000,
        profiles = listOf(
            BackupProfile(
                id = "default",
                resume = listOf(resume("movie:1", 10)),
                watched = listOf("movie:9"),
            ),
            BackupProfile(
                id = "p42",
                name = "Enfants",
                colorIndex = 1,
                resume = listOf(resume("movie:2", 20)),
                watched = listOf("movie:7", "movie:8"),
            ),
        ),
    )

    /**
     * L'aperçu est ce que l'utilisateur lit **avant** de décider. Compter le
     * premier niveau annoncerait « 0 vu » sur un fichier qui en porte trois.
     */
    @Test
    fun `l apercu compte les donnees des profils`() {
        val summary = twoProfiles.summary()

        assertEquals(3, summary.watched)
        assertEquals(2, summary.resume)
        assertEquals(2, summary.profiles)
    }

    /** Une sauvegarde d'avant les profils continue de se lire, et se dit sans profil. */
    @Test
    fun `une sauvegarde d avant les profils reste lisible`() {
        val legacy = MoovieBackup(watched = listOf("movie:1", "movie:2"))

        assertEquals(2, legacy.summary().watched)
        assertEquals(0, legacy.summary().profiles)
        assertTrue(legacy.profiles.isEmpty())
    }

    @Test
    fun `un aller-retour conserve les profils et leur identifiant`() {
        val decoded = BackupJson.decode(BackupJson.encode(twoProfiles))

        assertEquals(listOf("default", "p42"), decoded?.profiles?.map { it.id })
        assertEquals("Enfants", decoded?.profiles?.get(1)?.name)
        assertEquals(1, decoded?.profiles?.get(1)?.colorIndex)
        assertEquals(20, decoded?.profiles?.get(1)?.resume?.first()?.updatedAt)
    }

    /**
     * Le cumul sert le bilan d'un import multi-profils : ce que l'écran d'après
     * annonce est ce qui a bougé sur l'appareil, pas sur l'une des personnes.
     */
    @Test
    fun `les bilans par profil se cumulent`() {
        val a = ImportReport(1, 2, 3, 4, 5, 6)
        val b = ImportReport(10, 20, 30, 40, 50, 60)

        val total = a + b

        assertEquals(11, total.watchedAdded)
        assertEquals(22, total.watchedAlreadyThere)
        assertEquals(33, total.resumeAdded)
        assertEquals(66, total.historyAdded)
    }

    /**
     * Le format 2 ne recopie plus rien au premier niveau : une version
     * antérieure refuse le fichier entier de toute façon ([BackupJson.decode]),
     * et deux copies de la même chose finissent toujours par diverger.
     */
    @Test
    fun `un fichier a profils laisse le premier niveau vide`() {
        assertTrue(twoProfiles.resume.isEmpty())
        assertTrue(twoProfiles.watched.isEmpty())
    }
}
