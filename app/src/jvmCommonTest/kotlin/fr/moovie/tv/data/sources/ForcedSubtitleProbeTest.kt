package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.getBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire.
 *
 * Question posée : peut-on faire des **sous-titres forcés** à la Plex, c'est-à-dire
 * n'afficher que les passages en langue étrangère par-dessus une piste doublée ?
 *
 * Techniquement la réponse est oui — HLS le déclare avec `FORCED=YES` sur une
 * ligne `#EXT-X-MEDIA:TYPE=SUBTITLES`. Mais ça ne sert à rien si nos hébergeurs
 * ne publient pas l'information. On mesure donc **avant** de construire la
 * sélection automatique : combien de flux exposent seulement une piste de
 * sous-titres, combien la marquent forcée, et combien portent plusieurs pistes
 * audio — sans quoi la notion de « forcé » n'a même pas de sens.
 *
 * Le même réflexe que pour la qualité vidéo, où la mesure avait montré que seuls
 * 8 liens sur 23 exposaient l'information.
 */
class ForcedSubtitleProbeTest {

    private data class T(val media: MediaRef, val label: String)

    private val panier = listOf(
        T(MediaRef.Movie(693134, "Dune : Deuxième partie", "2024"), "Dune 2"),
        T(MediaRef.Movie(872585, "Oppenheimer", "2023"), "Oppenheimer"),
        T(MediaRef.Movie(634649, "Spider-Man : No Way Home", "2021"), "Spider-Man NWH"),
        T(MediaRef.Movie(550, "Fight Club", "1999"), "Fight Club"),
        T(MediaRef.Episode(1429, "L'Attaque des Titans", null, 1, 1), "Titans S1E1"),
        T(MediaRef.Episode(1396, "Breaking Bad", null, 2, 1), "Breaking Bad S2E1"),
        T(MediaRef.Episode(94997, "House of the Dragon", null, 1, 1), "HotD S1E1"),
        T(MediaRef.Episode(60625, "Rick et Morty", null, 1, 1), "Rick et Morty S1E1"),
    )

    private data class Row(
        val titre: String,
        val hoster: String,
        val langue: String,
        val subs: List<Media>,
        val audios: List<Media>,
    )

    /** Une déclaration `#EXT-X-MEDIA` du manifeste. */
    private data class Media(
        val name: String?,
        val language: String?,
        val forced: Boolean,
        val default: Boolean,
    )

    @Test
    fun probeForcedSubtitles() = runBlocking {
        if (System.getProperty("moovie.probe") == null) return@runBlocking

        val rows = mutableListOf<Row>()
        val slots = Semaphore(4)

        println("\n%-20s %-12s %-7s %-30s %s".format("titre", "hébergeur", "langue", "sous-titres", "pistes audio"))
        println("─".repeat(120))

        for (t in panier) {
            val links = ProviderRegistry.all.flatMap { p ->
                runCatching { p.sourcesFor(t.media) }.getOrDefault(emptyList())
            }.filter { it.language == "VF" || it.language == "VOSTFR" }
                .distinctBy { it.url }

            val measured = coroutineScope {
                links.map { link ->
                    async {
                        slots.withPermit {
                            val stream = runCatching { ExtractorRegistry.resolve(link) }.getOrNull()
                                ?: return@withPermit null
                            if (stream.format != StreamFormat.HLS) return@withPermit null
                            val body = runCatching {
                                ExtractorRegistry.gateway.getBody(stream.url, stream.headers)
                            }.getOrNull() ?: return@withPermit null
                            if (!body.startsWith("#EXTM3U")) return@withPermit null
                            Row(
                                titre = t.label,
                                hoster = link.hoster,
                                langue = link.language.orEmpty(),
                                subs = parseMedia(body, "SUBTITLES"),
                                audios = parseMedia(body, "AUDIO"),
                            )
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            for (r in measured) {
                println(
                    "%-20s %-12s %-7s %-30s %s".format(
                        r.titre.take(19),
                        r.hoster.take(11),
                        r.langue,
                        r.subs.joinToString(" ") { describe(it) }.ifEmpty { "—" },
                        r.audios.joinToString(" ") { describe(it) }.ifEmpty { "—" },
                    ),
                )
            }
            rows += measured
        }

        summary(rows)
    }

    private fun describe(m: Media): String {
        val name = m.name ?: m.language ?: "?"
        val flags = buildString {
            if (m.forced) append("!FORCÉ")
            if (m.default) append("*")
        }
        return name + flags
    }

    /**
     * Lit les déclarations `#EXT-X-MEDIA` du type demandé.
     *
     * Analyse volontairement littérale : on ne cherche pas à réimplémenter un
     * lecteur HLS, seulement à savoir si l'attribut existe dans la vraie vie.
     */
    private fun parseMedia(playlist: String, type: String): List<Media> =
        playlist.lineSequence()
            .filter { it.startsWith("#EXT-X-MEDIA:") && "TYPE=$type" in it }
            .map { line ->
                Media(
                    name = attr(line, "NAME"),
                    language = attr(line, "LANGUAGE"),
                    forced = attr(line, "FORCED")?.equals("YES", ignoreCase = true) == true,
                    default = attr(line, "DEFAULT")?.equals("YES", ignoreCase = true) == true,
                )
            }
            .toList()

    private fun attr(line: String, key: String): String? =
        Regex("""$key="?([^",]*)"?""").find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    private fun summary(rows: List<Row>) {
        println("\n════════ SYNTHÈSE ════════")
        println("flux HLS analysés            : ${rows.size}")
        println("avec au moins un sous-titre  : ${rows.count { it.subs.isNotEmpty() }}")
        println("avec une piste FORCÉE        : ${rows.count { r -> r.subs.any { it.forced } }}")
        println("avec plusieurs pistes audio  : ${rows.count { it.audios.size > 1 }}")

        val langues = rows.flatMap { it.subs }.mapNotNull { it.language }.groupingBy { it }.eachCount()
        if (langues.isNotEmpty()) {
            println("\nlangues de sous-titres rencontrées :")
            langues.toList().sortedByDescending { it.second }
                .forEach { (l, n) -> println("   %-8s %d".format(l, n)) }
        }

        println("\n→ Sans piste marquée FORCED=YES, la sélection automatique n'a rien")
        println("  sur quoi s'appuyer : il n'existe pas de moyen de deviner qu'une")
        println("  piste ne contient que les passages en langue étrangère.")
    }
}
