package fr.moovie.tv.data.sync

import fr.moovie.tv.data.remote.mayRecordOnTv
import fr.moovie.tv.data.store.ActiveProfile
import fr.moovie.tv.data.store.DEFAULT_PROFILE_ID
import fr.moovie.tv.data.store.useFileStores
import fr.moovie.tv.data.store.useInMemoryStores
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * L'empreinte qui autorise — ou non — un téléviseur à écrire.
 *
 * **Pourquoi ce test existe.** Une régression ici serait *doublement*
 * silencieuse. Trop stricte, la box cesse d'enregistrer : personne ne remarque
 * une absence, on croit juste que la reprise ne marche pas. Trop laxiste, elle
 * écrit dans un compte qui n'est pas le bon, et ça ne se voit qu'une fois la
 * pollution installée. Aucun des deux ne casse quoi que ce soit à l'écran, donc
 * rien ne le rapportera jamais.
 *
 * Elle se teste depuis que [useInMemoryStores] existe : le blocage n'a jamais
 * été la fonction, mais le DataStore qu'elle lit.
 */
class SyncFingerprintTest {

    private val creds = mapOf("keyId" to "005abc", "appKey" to "K005secret", "bucket" to "moovie")

    @BeforeTest
    fun fresh() {
        useInMemoryStores()
        ActiveProfile.id = DEFAULT_PROFILE_ID
    }

    @AfterTest
    fun restore() {
        ActiveProfile.id = DEFAULT_PROFILE_ID
        useFileStores()
    }

    private suspend fun configured(
        provider: SyncProvider = SyncProvider.BACKBLAZE_B2,
        credentials: Map<String, String> = creds,
        passphrase: String = "phrase secrète",
    ): SyncSettingsRepository = SyncSettingsRepository().apply {
        setProvider(provider)
        setCredentials(credentials)
        setPassphrase(passphrase)
    }

    /**
     * **Le test qui compte.** Sans synchro, il n'y a pas de destination commune
     * à prouver — et [mayRecordOnTv] refuse une empreinte vide. Rendre autre
     * chose que la chaîne vide ferait enregistrer la box dès que les deux
     * appareils sont également non configurés, c'est-à-dire dans le cas par
     * défaut de toute installation.
     */
    @Test
    fun `sans fournisseur l empreinte est vide`() = runTest {
        val repo = SyncSettingsRepository()
        repo.setPassphrase("une phrase")
        assertEquals("", repo.syncFingerprint())
        assertFalse(mayRecordOnTv(repo.syncFingerprint(), repo.syncFingerprint()))
    }

    @Test
    fun `la meme configuration donne la meme empreinte`() = runTest {
        val phone = configured().syncFingerprint()
        useInMemoryStores() // un second appareil, magasins vierges
        val tv = configured().syncFingerprint()

        assertTrue(phone.isNotEmpty())
        assertEquals(phone, tv)
        assertTrue(mayRecordOnTv(phone, tv), "deux appareils identiques doivent se reconnaître")
    }

    @Test
    fun `l empreinte est stable d un appel a l autre`() = runTest {
        val repo = configured()
        assertEquals(repo.syncFingerprint(), repo.syncFingerprint())
    }

    /**
     * Deux comptes B2 différents ne se réconcilient pas : ce que la box écrirait
     * chez l'un, le téléphone ne le lirait jamais.
     */
    @Test
    fun `des identifiants differents changent l empreinte`() = runTest {
        val mine = configured().syncFingerprint()
        useInMemoryStores()
        val theirs = configured(credentials = creds + ("bucket" to "autre")).syncFingerprint()

        assertNotEquals(mine, theirs)
        assertFalse(mayRecordOnTv(mine, theirs))
    }

    /**
     * Même bucket, phrases différentes : les fichiers sont chiffrés avec elle,
     * donc illisibles d'en face. Les confondre ferait écrire la box dans un
     * fichier que le téléphone ne saura jamais déchiffrer.
     */
    @Test
    fun `une phrase secrete differente change l empreinte`() = runTest {
        val mine = configured(passphrase = "la mienne").syncFingerprint()
        useInMemoryStores()
        val theirs = configured(passphrase = "la sienne").syncFingerprint()

        assertNotEquals(mine, theirs)
        assertFalse(mayRecordOnTv(mine, theirs))
    }

    /** Chiffrer d'un côté et pas de l'autre n'est pas la même destination. */
    @Test
    fun `une phrase secrete absente d un cote change l empreinte`() = runTest {
        val chiffre = configured(passphrase = "une phrase").syncFingerprint()
        useInMemoryStores()
        val clair = configured(passphrase = "").syncFingerprint()

        assertNotEquals(chiffre, clair)
    }

    /**
     * Chaque profil a ses propres magasins : une box posée sur « Enfants » qui
     * reçoit une diffusion du profil « Jibay » rangerait la reprise dans le
     * mauvais.
     */
    @Test
    fun `un profil actif different change l empreinte`() = runTest {
        val repo = configured()
        val defaut = repo.syncFingerprint()
        ActiveProfile.id = "p42"
        val autre = repo.syncFingerprint()

        assertNotEquals(defaut, autre)
        assertFalse(mayRecordOnTv(defaut, autre))
    }

    /**
     * L'ordre d'insertion des identifiants ne doit rien changer : deux appareils
     * configurés dans un ordre différent sont bien sur le même compte. C'est ce
     * que `toSortedMap()` garantit — sans lui, la moitié des appairages
     * échoueraient sans raison visible.
     */
    @Test
    fun `l ordre des identifiants ne change rien`() = runTest {
        val ordre = configured(credentials = creds).syncFingerprint()
        useInMemoryStores()
        val inverse = configured(
            credentials = creds.entries.reversed().associate { it.key to it.value },
        ).syncFingerprint()

        assertEquals(ordre, inverse)
    }

    /**
     * Deux champs concaténés ne doivent pas pouvoir se déguiser l'un en l'autre.
     * `keyId=ab` + `appKey=c` et `keyId=a` + `appKey=bc` sont deux comptes
     * distincts ; sans le séparateur NUL, ils donneraient le même matériau.
     */
    @Test
    fun `deux configurations ne se confondent pas par concatenation`() = runTest {
        val premier = configured(credentials = mapOf("a" to "xy", "b" to "z")).syncFingerprint()
        useInMemoryStores()
        val second = configured(credentials = mapOf("a" to "x", "b" to "yz")).syncFingerprint()

        assertNotEquals(premier, second)
    }

    /**
     * L'empreinte traverse le réseau local sur un serveur **sans TLS**. Elle ne
     * doit donc rien laisser filtrer de ce qu'elle résume : ni clé B2, ni phrase
     * secrète, ni nom de bucket.
     */
    @Test
    fun `l empreinte ne laisse filtrer aucun identifiant`() = runTest {
        val secret = "K005trèsSecret"
        val empreinte = configured(
            credentials = creds + ("appKey" to secret),
            passphrase = "ma phrase",
        ).syncFingerprint()

        listOf(secret, "ma phrase", "005abc", "moovie").forEach {
            assertFalse(it in empreinte, "l'empreinte contient « $it »")
        }
        assertTrue(
            empreinte.all { it in "0123456789abcdef" },
            "l'empreinte doit rester hexadécimale, reçu « $empreinte »",
        )
        assertEquals(16, empreinte.length)
    }

    /**
     * La règle de décision elle-même. Le cas des deux vides est le seul piège :
     * `"" == ""` est vrai, et l'autoriser ferait enregistrer toute box appairée
     * à un téléphone sans synchro — l'installation par défaut.
     */
    @Test
    fun `la box n enregistre que sur preuve d une destination commune`() {
        assertTrue(mayRecordOnTv("a1b2c3", "a1b2c3"))
        assertFalse(mayRecordOnTv("", ""), "deux absences de synchro ne prouvent rien")
        assertFalse(mayRecordOnTv("a1b2c3", ""))
        assertFalse(mayRecordOnTv("", "a1b2c3"))
        assertFalse(mayRecordOnTv("a1b2c3", "d4e5f6"))
    }
}
