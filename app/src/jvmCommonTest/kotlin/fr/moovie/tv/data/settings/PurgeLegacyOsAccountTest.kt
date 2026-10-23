package fr.moovie.tv.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.moovie.tv.data.store.preferencesStore
import fr.moovie.tv.data.store.useFileStores
import fr.moovie.tv.data.store.useInMemoryStores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Le retrait des identifiants OpenSubtitles laissés par une version précédente.
 *
 * Le compte et le mot de passe en clair ne doivent plus rester dans le magasin,
 * quel que soit le type sous lequel ils y sont écrits. Le test lit le magasin
 * brut : c'est la seule façon de voir une clé qu'aucun dépôt ne relit plus.
 */
class PurgeLegacyOsAccountTest {

    @BeforeTest
    fun avant() = useInMemoryStores()

    @AfterTest
    fun apres() = useFileStores()

    @Test
    fun `retire les cinq clés OpenSubtitles quel que soit leur type`() = runTest {
        val magasin = preferencesStore("moovie_settings")
        magasin.edit {
            it[stringPreferencesKey("os_username")] = "jb"
            it[stringPreferencesKey("os_password")] = "secret-en-clair"
            it[booleanPreferencesKey("os_remember")] = true
            // Une écriture sous un autre type reste aussi une trace à effacer.
            it[stringPreferencesKey("os_remember")] = "oui"
            it[stringPreferencesKey("os_token")] = "jeton"
            it[longPreferencesKey("os_token_at")] = 1_700_000_000_000L
        }

        SettingsRepository().purgeLegacyOsAccount()

        val restes = magasin.data.first().asMap().keys.map { it.name }.filter { it.startsWith("os_") }
        assertTrue(restes.isEmpty(), "clés OpenSubtitles restantes : $restes")
    }

    @Test
    fun `sans identifiants enregistrés, l'appel ne change rien`() = runTest {
        val reglages = SettingsRepository()
        reglages.setTmdbApiKey("ffffffffffffffffffffffffffffffff")
        val magasin = preferencesStore("moovie_settings")
        val avant = magasin.data.first().asMap()

        reglages.purgeLegacyOsAccount()

        assertEquals(avant, magasin.data.first().asMap())
    }
}
