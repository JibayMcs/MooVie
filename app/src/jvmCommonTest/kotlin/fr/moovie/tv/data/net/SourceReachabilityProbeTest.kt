package fr.moovie.tv.data.net

import fr.moovie.tv.data.sources.ClientExtraction
import fr.moovie.tv.data.sources.ExtractorRegistry
import fr.moovie.tv.data.sources.Ua
import okhttp3.Request
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire.
 *
 * Vérifie que les sites sources visés répondent bien au client d'extraction de
 * l'app (OkHttp + DoH), et pas seulement à curl. Les deux ne présentent pas la
 * même empreinte TLS : un site peut laisser passer l'un et bloquer l'autre.
 * C'est cette sonde, et non un `curl` depuis le terminal, qui dit si une source
 * est réellement atteignable on-device.
 *
 *     ./gradlew :app:desktopTest --tests '*SourceReachabilityProbeTest*' \
 *         -Dmoovie.probe=1
 */
class SourceReachabilityProbeTest {

    private val targets = listOf(
        "cinestream — recherche" to "https://cinestream.info/search?q=Dune",
        "cinestream — fiche film" to "https://cinestream.info/film/dune-premiere-partie-2021",
        "cinestream — player" to "https://cinestream.info/player/438631/0",
        "frembed — API film" to "https://frembed.casa/api/public/v1/movies/438631",
        "frembed — API série" to "https://frembed.casa/api/public/v1/tv/1396?sa=1&epi=1",
        "1jour1film — résolveur" to "https://1jour1film2026.site/go/",
        "frenchstream" to "https://frenchstream.food/",
        "purstream — API" to "https://api.purstream.cc/api/v1",
        "voirdrama" to "https://voirdrama.to/",
        // Témoin négatif : réputé sous bouclier Cloudflare.
        "vidsrc.to (témoin)" to "https://vidsrc.to/",
    )

    @Test
    fun probeTargets() {
        if (System.getProperty("moovie.probe") == null) {
            println("[sonde sources] ignorée (relancer avec -Dmoovie.probe=1)")
            return
        }

        println("\n%-28s %-6s %-9s %s".format("cible", "code", "taille", "indice"))
        println("-".repeat(78))

        for ((label, url) in targets) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", Ua.BROWSER)
                .build()

            val line = runCatching {
                ClientExtraction.http.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    // Ne PAS se fier à « cdn-cgi/challenge-platform » : ce script est
                    // injecté par Cloudflare sur des pages servies normalement. Un vrai
                    // blocage, c'est un interstitiel (titre « Just a moment… »,
                    // formulaire Turnstile) ou un 403/503.
                    val challenged = body.contains("Just a moment", true) ||
                        body.contains("_cf_chl_opt", true) ||
                        body.contains("challenges.cloudflare.com/turnstile", true)
                    val hint = when {
                        challenged -> "⛔ challenge Cloudflare"
                        resp.code == 403 || resp.code == 503 -> "⛔ bloqué (${resp.code})"
                        resp.code >= 400 -> "⚠ erreur HTTP"
                        body.isBlank() -> "⚠ corps vide"
                        else -> "✅ contenu servi"
                    }
                    "%-28s %-6d %-9d %s".format(label, resp.code, body.length, hint)
                }
            }.getOrElse { "%-28s %-6s %-9s ⛔ %s".format(label, "—", "—", it::class.simpleName) }

            println(line)
        }
    }
}
