package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpMethod
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.HttpResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Un 403 au HEAD n'est pas un verdict sur la ressource : la sonde doit retomber
 * sur le GET borné, comme pour un 405.
 *
 * Certains CDN refusent le HEAD mais servent le même GET avec un `Range` (206).
 * Avant ce correctif, le 403 écartait la source sans qu'un GET soit tenté.
 */
class StreamProbeForbiddenHeadTest {

    /** Répond selon la méthode : un statut pour le HEAD, un autre pour le GET. */
    private class MethodGateway(
        private val headStatus: Int,
        private val getStatus: Int,
    ) : HttpGateway {
        val methods = mutableListOf<HttpMethod>()

        override suspend fun fetch(request: HttpRequest): HttpResponse {
            methods += request.method
            val status = if (request.method == HttpMethod.HEAD) headStatus else getStatus
            return HttpResponse(
                status = status,
                url = request.url,
                headers = mapOf("Content-Type" to "video/mp4"),
            )
        }
    }

    private val stream = PlayableStream(
        url = "https://cdn.example/films/heat.mp4",
        format = StreamFormat.MP4,
        headers = mapOf("Referer" to "https://cdn.example/"),
    )

    @Test
    fun `HEAD 403 puis GET 206 est jouable`() = runTest {
        val gateway = MethodGateway(headStatus = 403, getStatus = 206)

        val playable = isStreamPlayable(stream, expectedMinutes = null, http = gateway)

        assertTrue(playable, "le GET borné répond 206, le flux doit être jouable")
        assertTrue(
            HttpMethod.GET in gateway.methods,
            "le GET de repli doit être tenté après un 403 au HEAD",
        )
    }

    @Test
    fun `HEAD 403 puis GET 403 n'est pas jouable`() = runTest {
        val gateway = MethodGateway(headStatus = 403, getStatus = 403)

        val playable = isStreamPlayable(stream, expectedMinutes = null, http = gateway)

        assertFalse(playable, "une ressource refusée au GET aussi ne doit pas être jouable")
    }

    @Test
    fun `HEAD 405 puis GET 206 est jouable`() = runTest {
        val gateway = MethodGateway(headStatus = 405, getStatus = 206)

        val playable = isStreamPlayable(stream, expectedMinutes = null, http = gateway)

        assertTrue(playable, "un 405 au HEAD doit toujours retomber sur le GET")
    }
}
