package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpMethod
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.HttpResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La sonde ne doit **jamais** télécharger un flux en entier.
 *
 * Elle le faisait, et ça a tué l'application sur la box : pour mesurer la durée,
 * `hlsDurationSeconds` lisait tout le corps en mémoire afin d'y chercher
 * `#EXTM3U`. Anodin sur une playlist de quelques kilo-octets ; sur le MP4
 * progressif de 1,24 Go que sert SwiftFlow, c'est un `OutOfMemoryError` au
 * moment précis où l'utilisateur appuie sur Lire.
 *
 * Le symptôme ne désignait pas sa cause : l'application disparaissait vers le
 * lanceur, sans message, ce qui ressemble à n'importe quoi sauf à une sonde de
 * durée. D'où ce test — il tient sur la **méthode des requêtes**, la seule chose
 * observable qui distingue « je vérifie » de « je télécharge ».
 */
class StreamProbeMemoryTest {

    /** Note chaque requête, et répond comme un CDN qui accepte tout. */
    private class RecordingGateway(private val contentType: String) : HttpGateway {
        val requests = mutableListOf<HttpRequest>()
        override suspend fun fetch(request: HttpRequest): HttpResponse {
            requests += request
            return HttpResponse(
                status = 200,
                url = request.url,
                body = "",
                headers = mapOf("Content-Type" to contentType),
            )
        }

        /** Une requête qui ramènerait tout : ni HEAD, ni bornée par un `Range`. */
        fun unboundedGets(): List<HttpRequest> = requests.filter {
            it.method == HttpMethod.GET && it.headers["Range"] == null
        }
    }

    private val mp4 = PlayableStream(
        url = "https://french.deliciouss.lol/series/VF/Inception/S01/Inception-S01-E01.mp4",
        format = StreamFormat.MP4,
        headers = mapOf("Referer" to "https://french.deliciouss.lol/"),
    )

    /**
     * L'invariant qui compte. `expectedMinutes` renseigné est le cas normal —
     * TMDB connaît la durée de tous les films — donc c'est le chemin que prend
     * n'importe quelle lecture, pas un cas limite.
     */
    @Test
    fun `un MP4 n'est jamais telecharge pour mesurer sa duree`() = runTest {
        val gateway = RecordingGateway("video/mp4")

        val playable = isStreamPlayable(mp4, expectedMinutes = 148, http = gateway)

        assertTrue(playable, "le flux répond, il doit être jouable")
        assertEquals(
            emptyList(),
            gateway.unboundedGets().map { it.url },
            "aucune requête ne doit ramener le fichier entier",
        )
    }

    /** Sans durée attendue non plus, évidemment — mais par un autre chemin. */
    @Test
    fun `sans duree attendue, la sonde se contente d'un HEAD`() = runTest {
        val gateway = RecordingGateway("video/mp4")

        isStreamPlayable(mp4, expectedMinutes = null, http = gateway)

        assertEquals(listOf(HttpMethod.HEAD), gateway.requests.map { it.method })
    }

    /**
     * Le HLS, lui, doit toujours être lu : c'est une playlist de quelques
     * kilo-octets, et c'est la seule façon de repérer les liens qui servent une
     * bande-annonce de trente secondes à la place du film.
     */
    @Test
    fun `une playlist HLS reste lue pour mesurer la duree`() = runTest {
        val gateway = RecordingGateway("application/vnd.apple.mpegurl")
        val hls = mp4.copy(url = "https://cdn.example/master.m3u8", format = StreamFormat.HLS)

        isStreamPlayable(hls, expectedMinutes = 148, http = gateway)

        assertTrue(
            gateway.unboundedGets().isNotEmpty(),
            "la playlist doit bien être téléchargée",
        )
    }
}
