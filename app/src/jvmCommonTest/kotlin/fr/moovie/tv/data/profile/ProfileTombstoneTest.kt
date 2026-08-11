package fr.moovie.tv.data.profile

import fr.moovie.tv.data.store.DEFAULT_PROFILE_ID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La règle qui décide si un profil supprimé a le droit de revenir.
 *
 * Le cas rapporté : supprimer un profil pendant qu'une synchro Backblaze tourne,
 * puis le voir réapparaître au lancement suivant. Le fichier distant contenait
 * encore le profil, l'import le recréait sans condition, et rien ne permettait
 * de distinguer « supprimé » de « pas encore reçu ».
 *
 * Même règle que pour les épisodes démarqués : **la décision la plus récente
 * gagne**, et à égalité on garde — un vieux fichier ne doit rien effacer.
 */
class ProfileTombstoneTest {

    private val repo = ProfileRepository()
    private fun profile(id: String, createdAt: Long) = Profile(id = id, createdAt = createdAt)

    /** Le cas rapporté : créé, puis supprimé après. Il ne revient pas. */
    @Test
    fun `un profil supprime apres sa creation ne revient pas`() {
        assertTrue(repo.isDeleted(profile("p1", createdAt = 100), mapOf("p1" to 200)))
    }

    /**
     * L'inverse doit rester vrai, sinon on ne pourrait plus jamais recréer un
     * profil au même identifiant après l'avoir supprimé.
     */
    @Test
    fun `un profil recree apres son retrait l'emporte`() {
        assertFalse(repo.isDeleted(profile("p1", createdAt = 300), mapOf("p1" to 200)))
    }

    /** Aucun retrait connu : on garde, évidemment. */
    @Test
    fun `sans pierre tombale, le profil vit`() {
        assertFalse(repo.isDeleted(profile("p1", createdAt = 100), emptyMap()))
    }

    /**
     * À égalité on garde. C'est la convention du projet pour les données d'avant
     * l'horodatage : deux zéros veulent dire « on ne sait pas », et l'ignorance
     * ne doit pas effacer.
     */
    @Test
    fun `a egalite, on garde le profil`() {
        assertFalse(repo.isDeleted(profile("p1", createdAt = 0), mapOf("p1" to 0)))
        assertFalse(repo.isDeleted(profile("p1", createdAt = 200), mapOf("p1" to 200)))
    }

    /**
     * Le profil d'origine ne se supprime pas : il n'a pas de fichiers à lui, ce
     * sont ceux de l'installation. Une pierre tombale mal formée ne doit pas
     * pouvoir effacer l'historique de tout le monde.
     */
    @Test
    fun `le profil d'origine ne peut jamais etre marque supprime`() {
        assertFalse(
            repo.isDeleted(
                profile(DEFAULT_PROFILE_ID, createdAt = 0),
                mapOf(DEFAULT_PROFILE_ID to Long.MAX_VALUE),
            ),
        )
    }

    /**
     * Le retrait d'un autre profil ne doit pas déborder sur celui-ci — une
     * confusion d'identifiant effacerait des données sans rapport.
     */
    @Test
    fun `une pierre tombale ne vise que son profil`() {
        assertFalse(repo.isDeleted(profile("p1", createdAt = 100), mapOf("p2" to 999)))
    }
}
