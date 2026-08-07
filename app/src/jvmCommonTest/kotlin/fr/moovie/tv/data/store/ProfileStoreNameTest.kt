package fr.moovie.tv.data.store

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileStoreNameTest {

    @AfterTest
    fun reset() {
        ActiveProfile.id = DEFAULT_PROFILE_ID
    }

    /**
     * **Le test qui compte.** Le profil d'origine ne suffixe rien : ses fichiers
     * portent le nom qu'ils ont depuis toujours. C'est ce qui fait qu'une
     * installation existante devient ce profil sans migration — si ce test
     * tombe, la mise à jour vide les reprises et l'historique de tout le monde.
     */
    @Test
    fun `le profil d origine garde le nom de fichier historique`() {
        assertEquals("moovie_watch", profileStoreName(STORE_WATCH, DEFAULT_PROFILE_ID))
        assertEquals("moovie_home", profileStoreName(STORE_HOME, DEFAULT_PROFILE_ID))
    }

    @Test
    fun `tout autre profil ecrit dans son propre fichier`() {
        assertEquals("moovie_watch__p42", profileStoreName(STORE_WATCH, "p42"))
        assertEquals("moovie_home__p42", profileStoreName(STORE_HOME, "p42"))
    }

    @Test
    fun `sans profil precise le nom suit le profil actif`() {
        ActiveProfile.id = "p7"
        assertEquals("moovie_watch__p7", profileStoreName(STORE_WATCH))
        ActiveProfile.id = DEFAULT_PROFILE_ID
        assertEquals("moovie_watch", profileStoreName(STORE_WATCH))
    }

    /**
     * Deux profils ne doivent jamais se retrouver dans le même fichier, et aucun
     * ne doit retomber sur celui de l'installation.
     */
    @Test
    fun `deux profils n ecrivent jamais au meme endroit`() {
        val names = listOf(DEFAULT_PROFILE_ID, "p1", "p2")
            .map { profileStoreName(STORE_WATCH, it) }
        assertEquals(names.size, names.toSet().size)
    }

    /**
     * La liste sert à effacer un profil supprimé : un magasin personnel qui n'y
     * figure pas survivrait à la suppression et resservirait ses données au
     * prochain profil du même identifiant.
     */
    @Test
    fun `les magasins personnels sont tous declares`() {
        assertTrue(STORE_WATCH in PROFILE_SCOPED_STORES)
        assertTrue(STORE_HOME in PROFILE_SCOPED_STORES)
    }
}
