package fr.moovie.tv.data.trailer

import fr.moovie.tv.data.sources.ExtractorRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire. Lancer avec `-Dmoovie.probe=1`.
 *
 * **Question posée : googlevideo refuse-t-il vraiment de servir un flux
 * entier, ou est-ce notre façon de le demander qui le fait refuser ?**
 *
 * Le relais découpe les requêtes en morceaux d'un mégaoctet parce qu'une
 * mesure passée disait qu'une requête sans plage, ou en `bytes=0-`, rendait
 * 403. Cette mesure a pu être faussée : elle a été prise sur des URL déjà
 * sollicitées par les essais précédents. Si l'URL avait déjà été « grillée »,
 * tout ce qu'on lui demandait ensuite rendait 403 — y compris la forme la plus
 * simple, qui aurait très bien pu marcher sur une URL neuve.
 *
 * L'enjeu est direct : si une requête unique passe, le découpage, la cadence,
 * le mur et la troncature de la bande-annonce à cinquante secondes n'ont plus
 * de raison d'être, et une bande-annonce se joue en entier.
 *
 * Chaque forme est essayée sur **sa propre URL fraîche** : c'est tout le point.
 */
class GoogleVideoFetchProbeTest {

    private companion object {
        /** Bande-annonce réelle : les restrictions varient selon la vidéo. */
        const val VIDEO_ID = "d9MyW72ELq0"
        const val MORCEAU = 1L * 1024 * 1024
        const val TAILLE_TAMPON = 64 * 1024
    }

    @Test
    fun sonde() = runBlocking {
        if (System.getProperty("moovie.probe") != "1") {
            println("[sonde googlevideo] ignorée (relancer avec -Dmoovie.probe=1)")
            return@runBlocking
        }
        val http = OkHttpClient.Builder()
            .callTimeout(5, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .build()

        println("\n[sonde googlevideo] chaque forme sur une URL fraîche\n")

        urlFraiche()?.let { (url, agent) ->
            aspire(http, url, agent, "requête unique, AUCUNE plage", null)
        }
        urlFraiche()?.let { (url, agent) ->
            aspire(http, url, agent, "requête unique, Range: bytes=0-", "bytes=0-")
        }
        urlFraiche()?.let { (url, agent) ->
            enMorceaux(http, url, agent, cadence = false)
        }
        urlFraiche()?.let { (url, agent) ->
            enMorceaux(http, url, agent, cadence = true)
        }
        Unit
    }

    /** Résout la bande-annonce et rend l'URL vidéo seule, jamais touchée. */
    private suspend fun urlFraiche(): Pair<String, String>? {
        val extracteur = YoutubeTrailerExtractor(ExtractorRegistry.gateway)
        val flux = extracteur.resolve(VIDEO_ID, "fr") ?: return null
        val url = flux.videoOnlyUrl ?: return null
        return url to flux.headers["User-Agent"].orEmpty()
    }

    /** Une seule requête, lue jusqu'au bout. La question tient dans le total. */
    private fun aspire(
        http: OkHttpClient,
        url: String,
        agent: String,
        libelle: String,
        plage: String?,
    ) {
        val attendu = url.substringAfter("clen=", "").substringBefore('&').toLongOrNull() ?: 0L
        val depart = System.currentTimeMillis()
        val requete = Request.Builder().url(url)
            .header("User-Agent", agent)
            .apply { plage?.let { header("Range", it) } }
            .build()
        val resultat = runCatching {
            http.newCall(requete).execute().use { reponse ->
                if (!reponse.isSuccessful) return@use "code ${reponse.code}"
                val corps = reponse.body ?: return@use "sans corps"
                var lus = 0L
                val tampon = ByteArray(TAILLE_TAMPON)
                corps.byteStream().use { flux ->
                    while (true) {
                        val n = flux.read(tampon)
                        if (n < 0) break
                        lus += n
                    }
                }
                val secondes = (System.currentTimeMillis() - depart) / 1000.0
                val complet = if (attendu > 0 && lus >= attendu) "COMPLET" else "TRONQUÉ"
                "$complet ${lus / 1024 / 1024} Mo / ${attendu / 1024 / 1024} Mo " +
                    "en ${"%.1f".format(secondes)} s (${(lus / 1024 / secondes).toInt()} Ko/s)"
            }
        }.getOrElse { "exception ${it::class.simpleName} : ${it.message}" }
        println("  $libelle → $resultat")
    }

    /** Le découpage du relais, avec ou sans la cadence temps réel. */
    private fun enMorceaux(http: OkHttpClient, url: String, agent: String, cadence: Boolean) {
        val taille = url.substringAfter("clen=", "").substringBefore('&').toLongOrNull() ?: return
        val duree = url.substringAfter("&dur=", "").substringBefore('&').toDoubleOrNull() ?: 0.0
        val debit = if (duree > 0) (taille / duree).toLong() else 0L
        val depart = System.currentTimeMillis()
        var position = 0L
        var refus = 0
        while (position < taille) {
            if (cadence && debit > 0) {
                val rafale = 4L * 1024 * 1024
                while (position >= rafale + (System.currentTimeMillis() - depart) * debit / 1000) {
                    Thread.sleep(100)
                }
            }
            val fin = minOf(position + MORCEAU - 1, taille - 2)
            val requete = Request.Builder().url(url)
                .header("User-Agent", agent)
                .header("Range", "bytes=$position-$fin")
                .build()
            val lus = runCatching {
                http.newCall(requete).execute().use { reponse ->
                    if (!reponse.isSuccessful) {
                        refus++
                        return@use -1L
                    }
                    var total = 0L
                    val tampon = ByteArray(TAILLE_TAMPON)
                    reponse.body?.byteStream()?.use { flux ->
                        while (true) {
                            val n = flux.read(tampon)
                            if (n < 0) break
                            total += n
                        }
                    }
                    total
                }
            }.getOrDefault(-1L)
            if (lus <= 0) break
            position += lus
        }
        val secondes = (System.currentTimeMillis() - depart) / 1000.0
        val complet = if (position >= taille - 1) "COMPLET" else "TRONQUÉ"
        println(
            "  morceaux de 1 Mo${if (cadence) " + cadence temps réel" else ""} → " +
                "$complet ${position / 1024 / 1024} Mo / ${taille / 1024 / 1024} Mo " +
                "en ${"%.1f".format(secondes)} s, $refus refus " +
                "(${(position * 100 / taille)} %)",
        )
    }
}
