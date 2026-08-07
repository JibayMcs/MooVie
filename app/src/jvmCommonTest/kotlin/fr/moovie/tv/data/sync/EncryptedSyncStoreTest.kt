package fr.moovie.tv.data.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedSyncStoreTest {

    /** Dépôt en mémoire : on inspecte ce qui est *réellement* écrit. */
    private class MemoryStore : SyncStore {
        val files = mutableMapOf<String, String>()
        override suspend fun list() = files.keys.map { SyncFile(it, 0) }
        override suspend fun read(name: String) = files[name]
        override suspend fun write(name: String, content: String): Long {
            files[name] = content
            return 1_000
        }
    }

    private val plain = """{"version":2,"profiles":[{"id":"default","watched":["movie:1"]}]}"""

    @Test
    fun `un aller-retour rend le contenu intact`() = runTest {
        val store = MemoryStore()
        val encrypted = EncryptedSyncStore(store, "correct horse battery staple")

        encrypted.write("moovie-sync-pc.json", plain)

        assertEquals(plain, encrypted.read("moovie-sync-pc.json"))
    }

    /** **Le test qui compte** : rien de lisible ne doit atteindre le dépôt. */
    @Test
    fun `le depot ne voit jamais le contenu en clair`() = runTest {
        val store = MemoryStore()

        EncryptedSyncStore(store, "phrase").write("moovie-sync-pc.json", plain)

        val stored = store.files.getValue("moovie-sync-pc.json")
        assertFalse("movie:1" in stored)
        assertFalse("profiles" in stored)
        assertTrue(stored.startsWith("MOOVIE-ENC1:"))
    }

    /**
     * Une mauvaise phrase rend null au lieu de lever : le moteur passe au
     * fichier suivant. Synchroniser avec deux appareils sur trois vaut mieux
     * que pas du tout.
     */
    @Test
    fun `une mauvaise phrase ne fait pas echouer la synchro`() = runTest {
        val store = MemoryStore()
        EncryptedSyncStore(store, "la bonne").write("f.json", plain)

        assertNull(EncryptedSyncStore(store, "la mauvaise").read("f.json"))
    }

    /** Un fichier écrit en clair par un appareil sans phrase est ignoré, pas fatal. */
    @Test
    fun `un fichier en clair est ignore quand on chiffre`() = runTest {
        val store = MemoryStore()
        store.files["ancien.json"] = plain

        assertNull(EncryptedSyncStore(store, "phrase").read("ancien.json"))
    }

    /**
     * Deux écritures du même contenu ne doivent pas produire le même chiffré :
     * sel et vecteur d'initialisation sont tirés à chaque fois. Sinon on
     * révélerait qu'il ne s'est rien passé entre deux synchros.
     */
    @Test
    fun `deux ecritures identiques donnent deux chiffres differents`() = runTest {
        val store = MemoryStore()
        val encrypted = EncryptedSyncStore(store, "phrase")

        encrypted.write("a.json", plain)
        val first = store.files.getValue("a.json")
        encrypted.write("a.json", plain)

        assertFalse(first == store.files.getValue("a.json"))
    }

    /** Le listage reste en clair : chiffrer les noms empêcherait de lister. */
    @Test
    fun `les noms de fichiers restent lisibles`() = runTest {
        val store = MemoryStore()
        val encrypted = EncryptedSyncStore(store, "phrase")

        encrypted.write("moovie-sync-pc.json", plain)

        assertEquals(listOf("moovie-sync-pc.json"), encrypted.list().map { it.name })
    }
}
