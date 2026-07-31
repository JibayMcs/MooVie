package fr.moovie.tv.data.net

import fr.moovie.tv.data.sources.ExtractorRegistry
import fr.moovie.tv.data.sources.Ua
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire.
 *
 * Mesure l'empreinte TLS (JA3/JA4) et HTTP/2 (Akamai) que nos clients OkHttp
 * présentent réellement, pour savoir ce que voient les boucliers anti-bot des
 * sites sources. Répond à la question « peut-on atteindre les sources derrière
 * Cloudflare sans usurpation d'empreinte ? » par la mesure et non par
 * l'hypothèse.
 *
 * Nécessite le réseau → ne s'exécute que sur demande explicite :
 *
 *     ./gradlew :app:desktopTest --tests '*TlsFingerprintProbeTest*' \
 *         -Dmoovie.probe=1 --info
 *
 * Attention : lancé en test unitaire Android, il mesure la pile TLS de la JVM
 * hôte, pas celle d'Android. L'empreinte Android réelle demande un test
 * instrumenté ou un passage sur l'émulateur.
 */
class TlsFingerprintProbeTest {

    private companion object {
        const val ECHO_URL = "https://tls.peet.ws/api/all"
        val json = Json { ignoreUnknownKeys = true }
    }

    @Test
    fun probeFingerprints() {
        if (System.getProperty("moovie.probe") == null) {
            println("[sonde TLS] ignorée (relancer avec -Dmoovie.probe=1)")
            return
        }

        report("client d'extraction de l'app (ExtractorRegistry.http)", ExtractorRegistry.http)

        // Variante : HTTP/1.1 forcé. Beaucoup de détections portent sur
        // l'empreinte HTTP/2 (ordre des SETTINGS) et non sur TLS ; si c'est le
        // cas, se replier en HTTP/1.1 coûte une ligne au lieu d'une lib de spoof.
        val http1 = ExtractorRegistry.http.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
        report("même client, HTTP/1.1 forcé", http1)

        // Client OkHttp nu (DNS système, aucune option) : sépare ce qui vient de
        // notre configuration de ce qui vient de la pile TLS de la plateforme.
        val bare = OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        report("OkHttp par défaut (référence)", bare)
    }

    private fun report(label: String, client: OkHttpClient) {
        val request = Request.Builder()
            .url(ECHO_URL)
            .header("User-Agent", Ua.BROWSER)
            .build()

        val body = runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use "!! HTTP ${resp.code}"
                resp.body?.string()
            }
        }.getOrElse { "!! ${it::class.simpleName}: ${it.message}" }

        println("\n──────── $label")
        if (body == null || body.startsWith("!!")) {
            println("  échec : $body")
            return
        }

        val root = json.parseToJsonElement(body).jsonObject
        val tls = root["tls"]?.jsonObject
        fun field(obj: kotlinx.serialization.json.JsonObject?, key: String): String =
            obj?.get(key)?.jsonPrimitive?.contentOrNull() ?: "n/a"

        println("  http_version : ${field(root, "http_version")}")
        println("  ja3_hash     : ${field(tls, "ja3_hash")}")
        println("  ja3          : ${field(tls, "ja3")}")
        println("  ja4          : ${field(tls, "ja4")}")
        println("  peetprint    : ${field(tls, "peetprint_hash")}")
        println("  akamai (h2)  : ${field(root["http2"]?.jsonObject, "akamai_fingerprint")}")
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        content.takeIf { it.isNotBlank() }
}
