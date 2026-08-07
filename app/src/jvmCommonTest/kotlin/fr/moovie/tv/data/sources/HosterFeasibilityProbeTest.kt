package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Hébergeurs proposés par les catalogues mais jamais résolus.
 *
 * `filmoon` en est sorti : la question est tranchée, on ne l'écrira pas (page
 * devenue une coquille SPA, rien à décoder sans exécuter son JavaScript), et
 * `FstreamProvider` ne le propose plus. Le sonder reviendrait à réinstruire un
 * dossier clos.
 */
private val HOSTERS = setOf("netu", "waaw", "savefiles", "serix", "flemmix")

/**
 * Retire les commentaires JS avant d'y chercher des indices d'extraction.
 *
 * Sans ça, un gabarit de lecteur qui garde en commentaire une URL d'exemple
 * fait croire à une piste exploitable. C'est arrivé deux fois sur waaw.
 */
private fun stripComments(html: String): String =
    html.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")

/**
 * Sonde de diagnostic — PAS un test unitaire.
 *
 * `netu` est le plus gros gisement inexploité (27 propositions, 0 flux jouable),
 * mais avant d'écrire un extracteur il faut savoir **où pointent** ces liens.
 * `FstreamProvider` développe un identifiant nu en `https://www.fembed.com/v/…`,
 * or fembed a fermé en 2022 : si c'est bien là qu'on envoie le client, le défaut
 * n'est pas l'absence d'extracteur mais une URL morte — le même piège que le
 * gabarit périmé de waaw.
 */
class HosterFeasibilityProbeTest {

    @Test
    fun probeHosterFeasibility() = runBlocking {
        if (System.getProperty("moovie.probe") == null) return@runBlocking

        val medias = listOf(
            MediaRef.Movie(693134, "Dune : Deuxième partie", "2024"),
            MediaRef.Movie(550, "Fight Club", "1999"),
            MediaRef.Episode(1396, "Breaking Bad", null, 2, 1),
        )

        for (media in medias) {
            val netu = ProviderRegistry.all.flatMap { p ->
                runCatching { p.sourcesFor(media) }.getOrDefault(emptyList())
                    .map { it to p.name }
            }.filter { it.first.hoster.lowercase() in HOSTERS }

            println("\n════════ $media ════════")
            println("liens netu : ${netu.size}")

            for ((link, provider) in netu.take(4)) {
                println("\n  catalogue : $provider")
                println("  langue    : ${link.language}")
                println("  URL       : ${link.url}")

                val resp = ExtractorRegistry.gateway.fetch(
                    HttpRequest(url = link.url, headers = mapOf("User-Agent" to Ua.BROWSER)),
                )
                println("  statut    : ${resp?.status ?: "échec réseau (hôte injoignable ?)"}")
                println("  URL finale: ${resp?.url}")
                println("  type      : ${resp?.header("Content-Type")}")
                resp?.body?.take(180)?.replace("\n", " ")?.let { println("  début     : $it") }
                resp?.body?.let { b ->
                    val live = stripComments(b)
                    val hints = listOf("m3u8", "eval(function", "jwplayer", "sources:")
                        .filter { h -> h in live }
                    println("  indices   : " + hints.joinToString().ifEmpty { "aucun" })
                    // Le même piège deux fois : chercher « m3u8 » dans le corps
                    // brut fait passer du code mort pour une piste. Chez waaw,
                    // l'unique occurrence était une URL d'exemple de 2018 —
                    // jeton expiré fin 2020 — enfermée dans un bloc commenté.
                    val dead = "m3u8" in b && "m3u8" !in live
                    if (dead) println("  ⚠️ m3u8 présent mais **en commentaire** : gabarit mort, pas une piste")
                }
            }
        }
    }
}
