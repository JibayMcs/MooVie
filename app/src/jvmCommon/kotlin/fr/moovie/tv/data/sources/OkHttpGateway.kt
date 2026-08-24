package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpMethod
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.HttpResponse
import fr.moovie.tv.core.sources.port.NetworkProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

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
                val json = request.json
                if (json != null) {
                    builder.post(json.toRequestBody(JSON_MEDIA_TYPE))
                } else {
                    val form = FormBody.Builder()
                    request.form.orEmpty().forEach { (k, v) -> form.add(k, v) }
                    builder.post(form.build())
                }
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
                    //
                    // Texte **ou** octets, jamais les deux : `string()` et
                    // `bytes()` consomment tous deux le flux, et le second appel
                    // rendrait vide.
                    body = when {
                        request.method == HttpMethod.HEAD -> null
                        request.binary -> null
                        else -> resp.body?.string()
                    },
                    bytes = if (request.binary && request.method != HttpMethod.HEAD) {
                        resp.body?.let(::litPlafonne)
                    } else {
                        null
                    },
                )
            }
        }.getOrNull()
    }

    /**
     * Lit un corps binaire, **borné**.
     *
     * ## Le défaut que ça corrige
     *
     * `bytes()` matérialise tout le corps. Les seuls appels binaires servent à
     * lire un en-tête MP4, et demandent donc une plage de quelques centaines de
     * kilo-octets — sauf qu'un `Range` est une *demande*, pas une garantie.
     *
     * Mesuré le 24/08/2026 sur SwiftFlow : l'URL du catalogue redirige vers un
     * proxy qui **ne transmet pas l'en-tête `Range`**. La plage disparaît, le
     * serveur répond 200 avec le fichier entier, et `bytes()` entreprend de
     * charger **3,8 Go** en mémoire pour y lire quatre nombres. L'URL signée
     * finale, elle, répond bien 206 — c'est le saut intermédiaire qui perd la
     * plage, ce qui rend le défaut invisible à qui teste l'adresse d'arrivée.
     *
     * Sur un téléphone ou sur la box, c'est l'`OutOfMemoryError` que ce projet
     * a déjà payé une fois, sur un fichier de 1,24 Go. Le lecteur ne s'ouvrait
     * même pas : la mesure de qualité tuait l'application avant.
     *
     * ## Pourquoi un plafond plutôt qu'un refus
     *
     * Un en-tête tronqué reste exploitable — `mp4Height` cherche des boîtes dans
     * ce qu'on lui donne et rend null s'il ne trouve pas. Refuser tout corps non
     * borné priverait de la qualité les hôtes qui ignorent `Range` mais servent
     * un fichier « faststart », dont l'en-tête tient dans les premiers octets.
     *
     * Le plafond est au-dessus de ce que demande le plus gros appel
     * (`MAX_MP4_HEADER_BYTES`, 512 Ko) : il ne tronque donc jamais une réponse
     * correctement bornée, et n'agit que quand le serveur a ignoré la consigne.
     */
    private fun litPlafonne(corps: okhttp3.ResponseBody): ByteArray = corps.byteStream().use { flux ->
        val tampon = ByteArray(PLAFOND_BINAIRE)
        var lus = 0
        while (lus < tampon.size) {
            // `read` à la main : `readNBytes` est une API Java 9, et ce fichier
            // est compilé pour minSdk 23 — elle passerait les tests sur le poste
            // de travail et lèverait sur la box.
            val n = flux.read(tampon, lus, tampon.size - lus)
            if (n < 0) break
            lus += n
        }
        if (lus == tampon.size) tampon else tampon.copyOf(lus)
    }

    private companion object {
        /**
         * Un `moov` de long métrage pèse quelques centaines de kilo-octets. Un
         * mégaoctet laisse de la marge sans jamais approcher le poids d'un média.
         */
        const val PLAFOND_BINAIRE = 1 shl 20
    }
}
