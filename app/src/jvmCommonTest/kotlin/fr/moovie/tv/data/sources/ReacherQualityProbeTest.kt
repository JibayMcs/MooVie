package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.usecase.nominalHeight
import fr.moovie.tv.core.sources.usecase.qualityLabel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire. `-Dmoovie.probe=1`.
 *
 * Instruit un cas précis et rapporté : sur *Reacher* S2E6, swiftflow s'affiche
 * « 720p » pour un fichier de **2,42 Go sur 46 minutes**. Le poids ne colle pas
 * au libellé, donc l'un des deux ment.
 *
 * Deux explications possibles, et elles demandent des correctifs opposés :
 *
 *  1. la **hauteur lue est fausse** — l'analyseur d'en-tête se trompe de piste
 *     ou de champ ;
 *  2. la hauteur est juste et c'est le **libellé** qui est faux, parce que
 *     `qualityLabel` classe sur la hauteur seule. Un film en 2,35:1 tient dans
 *     1920×800 : pleine largeur, donc de classe 1080p, mais 800 tombe dans le
 *     palier des 720.
 *
 * D'où le relevé de la **largeur** à côté de la hauteur : c'est elle qui
 * tranche, et elle ne figure nulle part aujourd'hui.
 */
class ReacherQualityProbeTest {

    @Test
    fun probeReacher() = runBlocking {
        if (System.getProperty("moovie.probe") == null) return@runBlocking

        val media = MediaRef.Episode(66765, "Reacher", null, 2, 6)
        val gateway = ExtractorRegistry.gateway

        val liens = ProviderRegistry.all
            .flatMap { runCatching { it.sourcesFor(media) }.getOrDefault(emptyList()) }
            .filter { it.language == "VF" }
            .distinctBy { it.url }

        println("\n%-16s %-6s %-14s %-9s %s".format("hébergeur", "format", "dimensions", "libellé", "remarque"))
        println("─".repeat(84))

        for (lien in liens.take(10)) {
            val flux = runCatching { ExtractorRegistry.resolve(lien) }.getOrNull()
            if (flux == null) {
                println("%-16s %-6s %-14s %-9s %s".format(hosterLabel(lien).take(15), "—", "—", "—", "non résolu"))
                continue
            }

            when (flux.format) {
                StreamFormat.MP4 -> {
                    val octets = gateway.fetch(
                        HttpRequest(
                            url = flux.url,
                            headers = flux.headers + ("Range" to "bytes=0-524287"),
                            binary = true,
                        ),
                    )?.bytes
                    val dims = octets?.let { dimensionsTkhd(it) }
                    val h = dims?.second
                    println(
                        "%-16s %-6s %-14s %-9s %s".format(
                            hosterLabel(lien).take(15),
                            "MP4",
                            dims?.let { "${it.first}×${it.second}" } ?: "—",
                            dims?.let { qualityLabel(nominalHeight(it.first, it.second)) } ?: "—",
                            dims?.let { ratio(it.first, it.second) } ?: "",
                        ),
                    )
                }
                StreamFormat.HLS -> {
                    val corps = gateway.fetch(
                        HttpRequest(url = flux.url, headers = flux.headers + ("Range" to "bytes=0-262143")),
                    )?.body.orEmpty()
                    val res = Regex("""RESOLUTION=(\d+)x(\d+)""").findAll(corps)
                        .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
                        .sortedByDescending { it.second }.toList()
                    val meilleur = res.firstOrNull()
                    println(
                        "%-16s %-6s %-14s %-9s %s".format(
                            hosterLabel(lien).take(15),
                            "HLS",
                            meilleur?.let { "${it.first}×${it.second}" } ?: "—",
                            meilleur?.let { qualityLabel(nominalHeight(it.first, it.second)) } ?: "—",
                            meilleur?.let { ratio(it.first, it.second) } ?: "",
                        ),
                    )
                }
                else -> println("%-16s %-6s %-14s %-9s".format(hosterLabel(lien).take(15), flux.format, "—", "—"))
            }
        }
    }

    private fun ratio(w: Int, h: Int): String =
        "ratio %.2f:1".format(w.toDouble() / h.toDouble())

    /** Largeur et hauteur de la plus grande piste, en 16.16 depuis la fin du tkhd. */
    private fun dimensionsTkhd(o: ByteArray): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var i = 4
        while (i <= o.size - 8) {
            if (o[i] == 0x74.toByte() && o[i + 1] == 0x6B.toByte() &&
                o[i + 2] == 0x68.toByte() && o[i + 3] == 0x64.toByte()
            ) {
                val debut = i - 4
                val taille = int32(o, debut)
                val fin = debut + taille
                if (taille in 84..o.size && fin <= o.size) {
                    val w = int32(o, fin - 8) ushr 16
                    val h = int32(o, fin - 4) ushr 16
                    if (h in 1..4320 && (best == null || h > best!!.second)) best = w to h
                }
            }
            i++
        }
        return best
    }

    private fun int32(o: ByteArray, at: Int): Int =
        ((o[at].toInt() and 0xFF) shl 24) or ((o[at + 1].toInt() and 0xFF) shl 16) or
            ((o[at + 2].toInt() and 0xFF) shl 8) or (o[at + 3].toInt() and 0xFF)
}
