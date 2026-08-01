package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpMethod
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.HttpResponse
import fr.moovie.tv.core.sources.port.NetworkProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Implémentation OkHttp de [HttpGateway].
 *
 * Deux clients dérivés d'un même socle : ils partagent pool de connexions, DNS
 * (DoH) et timeouts, et ne diffèrent que par le suivi des redirections. Le
 * variant sans suivi sert aux chaînes trop longues pour OkHttp, dont le plafond
 * de 20 redirections est codé en dur (`MAX_FOLLOW_UPS`) — VOE en enchaîne 28.
 *
 * [NetworkProfile.BROWSER] n'a pas encore d'implémentation : la requête part sur
 * le client par défaut. Une source qui l'exigerait vraiment répond 403, ce que
 * l'appelant traite déjà comme un échec ordinaire — pas de plantage, juste un
 * lien qui ne se résout pas.
 */
class OkHttpGateway(private val client: OkHttpClient) : HttpGateway {

    private val noRedirect: OkHttpClient by lazy {
        client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    }

    override suspend fun fetch(request: HttpRequest): HttpResponse? = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (name, value) -> builder.header(name, value) }

        when (request.method) {
            HttpMethod.GET -> builder.get()
            HttpMethod.HEAD -> builder.head()
            HttpMethod.POST -> {
                val form = FormBody.Builder()
                request.form.orEmpty().forEach { (k, v) -> form.add(k, v) }
                builder.post(form.build())
            }
        }

        val http = if (request.followRedirects) client else noRedirect

        runCatching {
            http.newCall(builder.build()).execute().use { resp ->
                HttpResponse(
                    status = resp.code,
                    // `resp.request.url` est l'URL finale après redirections :
                    // c'est elle, et non celle demandée, qui doit servir de base
                    // au Referer chez les hébergeurs à alias tournants.
                    url = resp.request.url.toString(),
                    headers = resp.headers.toMultimap()
                        .mapValues { (_, v) -> v.firstOrNull().orEmpty() },
                    // HEAD n'a pas de corps ; string() rendrait une chaîne vide,
                    // ce qui est correct mais inutile à matérialiser.
                    body = if (request.method == HttpMethod.HEAD) null else resp.body?.string(),
                )
            }
        }.getOrNull()
    }
}
