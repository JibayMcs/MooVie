package fr.moovie.tv.data.sync

import fr.moovie.tv.data.backup.BackupProfile
import fr.moovie.tv.data.backup.ImportMode
import fr.moovie.tv.data.backup.ImportReport
import fr.moovie.tv.data.backup.MoovieBackup
import fr.moovie.tv.data.backup.WatchState
import fr.moovie.tv.data.backup.mergeWatchState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La synchro, éprouvée **sans réseau, sans compte et sans appareil**.
 *
 * C'est la raison d'être des deux ports : un dépôt en mémoire remplace B2, un
 * sujet en mémoire remplace DataStore, et le scénario qui compte — deux
 * appareils qui ne sont jamais éveillés en même temps — devient un test au lieu
 * d'être une manipulation à deux machines.
 */
class SyncEngineTest {

    /** Le dépôt distant, en mémoire. Son horloge est volontairement décalée. */
    private class MemoryStore(private val clockSkew: Long = 0) : SyncStore {
        val files = mutableMapOf<String, String>()
        private val stamps = mutableMapOf<String, Long>()
        var tick = 1_000L

        override suspend fun list() = files.keys.map { SyncFile(it, stamps[it] ?: 0L) }

        override suspend fun read(name: String) = files[name]

        override suspend fun write(name: String, content: String): Long {
            files[name] = content
            tick += 10
            stamps[name] = tick + clockSkew
            return tick + clockSkew
        }
    }

    /** Un appareil : son état local, et la règle de fusion réelle. */
    private class Device(var state: WatchState = WatchState()) : SyncSubject {
        override suspend fun snapshot(now: Long) = MoovieBackup(
            exportedAt = now,
            profiles = listOf(
                BackupProfile(
                    id = "default",
                    watched = state.watched.toList(),
                    watchedAt = state.watchedAt,
                ),
            ),
        )

        override suspend fun merge(incoming: MoovieBackup): ImportReport {
            val entry = incoming.profiles.firstOrNull() ?: return EMPTY
            val (merged, report) = mergeWatchState(
                current = state,
                incoming = WatchState(
                    watched = entry.watched.toSet(),
                    watchedAt = entry.watchedAt,
                ),
                mode = ImportMode.MERGE,
            )
            state = merged
            return report
        }
    }

    private fun engine(store: SyncStore, id: String, device: Device) =
        SyncEngine(store, id, device)

    /**
     * **Le scénario qui a motivé la feature** : le PC au travail, la TV à la
     * maison, jamais allumés en même temps. Ils ne se voient pas, ils convergent
     * quand même — c'est le dépôt qui garde le fichier entre les deux.
     */
    @Test
    fun `deux appareils qui ne se croisent jamais convergent`() = runTest {
        val store = MemoryStore()
        val pc = Device(WatchState(watched = setOf("movie:1"), watchedAt = mapOf("movie:1" to 100)))
        val tv = Device(WatchState(watched = setOf("tv:2:s1e1"), watchedAt = mapOf("tv:2:s1e1" to 200)))

        // Le PC publie, seul, depuis le bureau.
        engine(store, "pc", pc).sync(now = 1_000)
        // Le soir, la TV se réveille : le PC est éteint depuis longtemps.
        engine(store, "tv", tv).sync(now = 2_000)
        // Le lendemain matin, le PC relit.
        engine(store, "pc", pc).sync(now = 3_000)

        assertEquals(setOf("movie:1", "tv:2:s1e1"), pc.state.watched)
        assertEquals(setOf("movie:1", "tv:2:s1e1"), tv.state.watched)
    }

    /** Chacun n'écrit que son fichier : c'est ce qui supprime tout conflit. */
    @Test
    fun `chaque appareil ecrit son propre fichier`() = runTest {
        val store = MemoryStore()

        engine(store, "pc", Device()).sync(now = 1_000)
        engine(store, "tv", Device()).sync(now = 2_000)

        assertEquals(
            setOf("moovie-sync-pc.json", "moovie-sync-tv.json"),
            store.files.keys,
        )
    }

    /** On ne relit jamais son propre fichier : ce serait fusionner avec soi-même. */
    @Test
    fun `un appareil ignore son propre fichier`() = runTest {
        val store = MemoryStore()
        val pc = Device(WatchState(watched = setOf("movie:1")))

        engine(store, "pc", pc).sync(now = 1_000)
        val second = engine(store, "pc", pc).sync(now = 2_000)

        assertEquals(0, second.devicesSeen)
    }

    /**
     * Un retrait daté traverse la synchro. Sans les pierres tombales, l'épisode
     * démarqué au bureau reviendrait sur la TV à la synchro suivante.
     */
    @Test
    fun `un episode demarque le reste apres synchro`() = runTest {
        val store = MemoryStore()
        val tv = Device(WatchState(watched = setOf("tv:1:s1e1"), watchedAt = mapOf("tv:1:s1e1" to 100)))
        val pc = Device(WatchState(watchedAt = mapOf("tv:1:s1e1" to 500)))

        engine(store, "tv", tv).sync(now = 1_000)
        engine(store, "pc", pc).sync(now = 2_000)
        engine(store, "tv", tv).sync(now = 3_000)

        assertFalse("tv:1:s1e1" in tv.state.watched)
        assertFalse("tv:1:s1e1" in pc.state.watched)
    }

    /**
     * L'horloge du serveur est la référence : un appareil qui retarde d'une
     * heure doit s'en apercevoir tout seul, sans que personne ne le règle.
     */
    @Test
    fun `la synchro mesure le decalage avec l horloge du serveur`() = runTest {
        val store = MemoryStore(clockSkew = 3_600_000)

        val report = engine(store, "pc", Device()).sync(now = 1_000)

        assertTrue(report.clockOffset > 3_500_000)
    }

    /** Un fichier illisible ne fait pas échouer la synchro : on passe au suivant. */
    @Test
    fun `un fichier corrompu est ignore`() = runTest {
        val store = MemoryStore()
        store.files["moovie-sync-vieux.json"] = "{ ceci n'est pas du JSON"
        val pc = Device(WatchState(watched = setOf("movie:1")))

        val report = engine(store, "pc", pc).sync(now = 1_000)

        assertEquals(0, report.devicesSeen)
        assertTrue(store.files.containsKey("moovie-sync-pc.json"))
    }

    private companion object {
        val EMPTY = ImportReport(0, 0, 0, 0, 0, 0)
    }
}
