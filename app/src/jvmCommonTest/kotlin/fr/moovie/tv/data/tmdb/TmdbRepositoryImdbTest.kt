package fr.moovie.tv.data.tmdb

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Cache des identifiants IMDB de [TmdbRepository.imdbId], avec une fausse API
 * qui compte ses appels : aucun réseau.
 *
 * Ce qu'on vérifie : une absence se mémorise (un seul aller-retour), un échec
 * ne se mémorise pas (le titre se retente au prochain appel).
 *
 * Les identifiants TMDB sont choisis loin de tout titre réel : le cache est
 * partagé par le processus, et un test ne doit pas hériter d'un autre.
 */
class TmdbRepositoryImdbTest {

    private class ApiFactice(var reponse: suspend () -> ExternalIds) :
        TmdbApi(HttpClient(OkHttp)) {

        var appels = 0

        override suspend fun externalIds(media: String, id: Int, apiKey: String): ExternalIds {
            appels++
            return reponse()
        }
    }

    @Test
    fun absenceEstMemorisee() = runBlocking {
        val api = ApiFactice { ExternalIds(imdbId = null) }
        val repo = TmdbRepository(api = api)

        assertNull(repo.imdbId("cle", isTv = false, id = 910001))
        assertNull(repo.imdbId("cle", isTv = false, id = 910001))
        assertEquals(1, api.appels)
    }

    @Test
    fun echecNEstPasMemorise() = runBlocking {
        val api = ApiFactice { throw IOException("réseau coupé") }
        val repo = TmdbRepository(api = api)

        assertNull(repo.imdbId("cle", isTv = false, id = 910002))

        api.reponse = { ExternalIds(imdbId = "tt0000042") }
        assertEquals("tt0000042", repo.imdbId("cle", isTv = false, id = 910002))
        // Cette fois c'est en cache : pas de troisième appel.
        assertEquals("tt0000042", repo.imdbId("cle", isTv = false, id = 910002))
        assertEquals(2, api.appels)
    }
}
