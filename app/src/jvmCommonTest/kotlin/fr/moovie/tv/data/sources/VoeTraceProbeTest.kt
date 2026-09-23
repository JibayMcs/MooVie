package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Trace un lien VOE, saut par saut, jusqu'à la charge utile.
 *
 * ```
 * ./gradlew :app:desktopTest --tests '*VoeTraceProbeTest' -Dmoovie.probe=1
 * ```
 *
 * [HosterHealthProbeTest] dit **combien** de liens VOE résolvent, jamais
 * pourquoi les autres échouent — et les trois causes possibles n'appellent pas
 * du tout le même correctif :
 *
 * - la marche de redirections n'aboutit pas (alias mort, boucle, 404) : rien à
 *   décoder, le lien était périmé au catalogue ;
 * - la page arrive mais ne porte aucune charge : ce n'est pas du VOE, ou c'est
 *   une page d'erreur déguisée en 200 ;
 * - la charge est là et ne se décode pas : **rotation des marqueurs**, la
 *   panne silencieuse que [VoePayload.LEGACY_MARKERS] documente.
 *
 * La trace tranche entre les trois en une exécution, ce qu'aucun compteur ne
 * fait.
 */
class VoeTraceProbeTest {

    /**
     * Le panier par défaut, ou **le titre qu'on lui donne**.
     *
     * ```
     * ./gradlew :app:desktopTest --tests '*VoeTraceProbeTest' -Dmoovie.probe=1 \
     *   -Dmoovie.tmdb=113962 -Dmoovie.title="Special Ops: Lioness" \
     *   -Dmoovie.season=2 -Dmoovie.episode=2
     * ```
     *
     * Le titre importe autant que l'identifiant : les catalogues cherchent par
     * nom, l'id TMDB ne servant qu'à confirmer la fiche. Sans lui, on trace un
     * autre épisode que celui dont on se plaint.
     */
    private val panier: List<MediaRef> = System.getProperty("moovie.tmdb")?.toIntOrNull()?.let { id ->
        val titre = System.getProperty("moovie.title").orEmpty()
        val saison = System.getProperty("moovie.season")?.toIntOrNull()
        val episode = System.getProperty("moovie.episode")?.toIntOrNull()
        listOf(
            if (saison != null && episode != null) MediaRef.Episode(id, titre, null, saison, episode)
            else MediaRef.Movie(id, titre, null),
        )
    } ?: listOf(
        MediaRef.Movie(550, "Fight Club", "1999"),
        MediaRef.Movie(77338, "Intouchables", "2011"),
        MediaRef.Movie(603, "Matrix", "1999"),
        MediaRef.Episode(1416, "Grey's Anatomy", null, 1, 1),
    )

    @Test
    fun traceVoe() = runBlocking {
        if (System.getProperty("moovie.probe") != "1") {
            println("[sonde voe] ignorée (relancer avec -Dmoovie.probe=1)")
            return@runBlocking
        }

        val liens = panier.flatMap { media ->
            ProviderRegistry.all.flatMap { p ->
                runCatching { p.sourcesFor(media) }.getOrDefault(emptyList())
            }
        }.filter { it.hoster.equals("voe", true) || VoeExtractor(ExtractorRegistry.gateway).canHandle(it.url) }
            .distinctBy { it.url }

        if (liens.isEmpty()) {
            println("[sonde voe] aucun lien VOE dans les catalogues")
            return@runBlocking
        }

        println("\n════════ TRACE VOE (${liens.size} liens) ════════")
        for (lien in liens) {
            println("\n──── ${lien.url}")

            // Même marche que l'extracteur, mais bavarde : on veut la page
            // terminale ET le chemin qui y mène.
            var url = lien.url
            var page: String? = null
            var sauts = 0
            while (sauts < 40) {
                val resp = runCatching {
                    ExtractorRegistry.gateway.fetch(
                        HttpRequest(
                            url = url,
                            headers = mapOf(
                                "User-Agent" to Ua.BROWSER,
                                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                            ),
                            followRedirects = false,
                        ),
                    )
                }.getOrNull()

                if (resp == null) {
                    println("  saut $sauts   : ❌ injoignable ($url)")
                    break
                }
                val suivant = when {
                    resp.isRedirect -> resp.header("Location")
                    // Mêmes motifs que l'extracteur, sans quoi la trace
                    // s'arrêterait là où lui continue.
                    else -> VoeExtractor.JS_REDIRECTS.firstNotNullOfOrNull {
                        it.find(resp.body.orEmpty())?.groupValues?.get(1)
                    }
                }
                if (suivant == null || suivant == url) {
                    println("  sauts     : $sauts, statut ${resp.status}, arrivée ${url.take(80)}")
                    page = resp.body
                    break
                }
                url = if (suivant.startsWith("http")) suivant else url.substringBeforeLast('/') + "/" + suivant
                sauts++
            }

            if (page == null) {
                println("  page      : ❌ aucune page terminale")
                continue
            }
            println("  page      : ${page.length} octets")
            println("  charge    : ${if (VoePayload.hasPayload(page)) "présente" else "ABSENTE"}")
            println("  figés     : ${if (VoePayload.findSource(page) != null) "décodent" else "ÉCHOUENT"}")

            val bundleUrl = VoePayload.bundleUrl(page)
            println("  bundle    : ${bundleUrl?.take(90) ?: "aucun script adjacent"}")
            if (bundleUrl != null) {
                val absolu = if (bundleUrl.startsWith("http")) bundleUrl else {
                    val origine = Regex("""^(https?://[^/]+)""").find(url)?.groupValues?.get(1).orEmpty()
                    if (bundleUrl.startsWith("/")) origine + bundleUrl
                    else url.substringBeforeLast('/') + "/" + bundleUrl
                }
                val js = runCatching {
                    ExtractorRegistry.gateway.fetch(
                        HttpRequest(absolu, headers = mapOf("User-Agent" to Ua.BROWSER, "Referer" to url)),
                    )?.body
                }.getOrNull()
                println("  bundle js : ${js?.length ?: 0} octets")
                val markers = js?.let { VoePayload.markersFrom(it) }
                println("  marqueurs : ${markers ?: "table introuvable"}")
                if (markers != null) {
                    println("  décodage  : ${VoePayload.findSource(page, markers)?.take(90) ?: "ÉCHOUE MÊME AVEC LA TABLE"}")
                }
            }
            println("  en clair  : ${VoePayload.plainSource(page)?.take(90) ?: "rien"}")

            val flux = runCatching { ExtractorRegistry.resolve(lien) }.getOrNull()
            println("  résolu    : ${flux?.url?.take(90) ?: "❌"}")
            if (flux != null) {
                println("  jouable   : ${runCatching { isStreamPlayable(flux) }.getOrDefault(false)}")
            }
        }
    }
}
