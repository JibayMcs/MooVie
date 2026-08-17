package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.HttpRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire. `-Dmoovie.probe=1`.
 *
 * Décide s'il est possible de donner une définition aux sources **MP4**.
 *
 * Aujourd'hui `streamHeights` rend une liste vide hors HLS : uqload, doodstream
 * et swiftflow n'ont donc jamais de qualité affichée, et le tri les range au
 * pivot des 720 faute de mieux. C'est ce qui fait paraître le classement
 * aléatoire — la moitié de la liste n'est pas mesurée du tout.
 *
 * La seule source de vérité pour un MP4 est son en-tête : la boîte `moov`
 * contient un `tkhd` par piste, dont les 8 derniers octets portent largeur et
 * hauteur en virgule fixe 16.16. Reste deux questions qu'aucune lecture de
 * documentation ne tranche, et que cette sonde pose au réseau réel :
 *
 *  1. **`moov` est-il au début du fichier ?** Un MP4 « faststart » le place
 *     avant les données ; un fichier produit sans cette option le met à la fin,
 *     et il faudrait alors une seconde requête sur la queue.
 *  2. **Les hébergeurs acceptent-ils une requête par plage ?** Sans `Range`, lire
 *     l'en-tête voudrait dire télécharger le film.
 *
 * Le relevé imprime, par source : le format, la définition connue aujourd'hui,
 * et ce que l'en-tête aurait donné.
 */
class Mp4HeightProbeTest {

    private val panier = listOf(
        MediaRef.Movie(693134, "Dune : Deuxième partie", "2024"),
        MediaRef.Movie(550, "Fight Club", "1999"),
        MediaRef.Episode(1429, "L'Attaque des Titans", null, 1, 1),
    )

    /** Premiers octets, là où un fichier « faststart » range son `moov`. */
    private val TETE = 512 * 1024

    @Test
    fun probeMp4Heights() = runBlocking {
        if (System.getProperty("moovie.probe") == null) return@runBlocking

        val gateway = ExtractorRegistry.gateway
        println("\n%-14s %-6s %-9s %-9s %s".format("hébergeur", "format", "aujourd'hui", "en-tête", "remarque"))
        println("─".repeat(88))

        var mp4Total = 0
        var mp4Resolus = 0
        var rangeRefuse = 0

        for (media in panier) {
            val liens = ProviderRegistry.all
                .flatMap { runCatching { it.sourcesFor(media) }.getOrDefault(emptyList()) }
                .filter { it.language == "VF" }
                .distinctBy { it.hoster }
                .take(6)

            for (lien in liens) {
                val flux = runCatching { ExtractorRegistry.resolve(lien) }.getOrNull() ?: continue
                val actuel = runCatching { streamHeights(flux).firstOrNull() }.getOrNull()

                if (flux.format != StreamFormat.MP4) {
                    println("%-14s %-6s %-9s %-9s %s".format(lien.hoster.take(13), flux.format, actuel ?: "—", "—", "HLS : déjà couvert"))
                    continue
                }

                mp4Total++
                val reponse = runCatching {
                    gateway.fetch(
                        HttpRequest(
                            url = flux.url,
                            headers = flux.headers + ("Range" to "bytes=0-$TETE"),
                        ),
                    )
                }.getOrNull()

                if (reponse == null || !reponse.isSuccessful) {
                    rangeRefuse++
                    println("%-14s %-6s %-9s %-9s %s".format(lien.hoster.take(13), "MP4", actuel ?: "—", "—", "plage refusée (${reponse?.status})"))
                    continue
                }

                val corps = reponse.body.orEmpty()
                val octets = corps.toByteArray(Charsets.ISO_8859_1)
                val hauteur = hauteurDepuisTkhd(octets)
                if (hauteur != null) mp4Resolus++

                println(
                    "%-14s %-6s %-9s %-9s %s".format(
                        lien.hoster.take(13),
                        "MP4",
                        actuel ?: "—",
                        hauteur ?: "—",
                        if (hauteur != null) "moov en tête" else if ("moov" in corps) "moov présent, tkhd illisible" else "moov absent des ${TETE / 1024} Ko de tête",
                    ),
                )
            }
        }

        println("\n════════ VERDICT ════════")
        println("sources MP4 rencontrées        : $mp4Total")
        println("dont la hauteur est lisible    : $mp4Resolus")
        println("dont la plage a été refusée    : $rangeRefuse")
    }

    /**
     * Hauteur portée par le premier `tkhd` rencontré, ou null.
     *
     * `tkhd` : version(1) + flags(3) + dates + track_id + … et **les huit derniers
     * octets** sont largeur et hauteur en 16.16. On lit donc à rebours depuis la
     * fin de la boîte, ce qui évite d'avoir à traiter les deux versions (0 et 1)
     * dont les champs de tête n'ont pas la même taille.
     *
     * Une piste audio porte un `tkhd` à zéro : d'où le premier **non nul**.
     */
    private fun hauteurDepuisTkhd(octets: ByteArray): Int? {
        val marqueur = "tkhd".toByteArray(Charsets.US_ASCII)
        var i = 0
        while (i < octets.size - marqueur.size - 4) {
            if (octets.regionMatches(i, marqueur)) {
                // Taille de la boîte : les 4 octets qui précèdent le type.
                val debut = i - 4
                if (debut < 0) { i++; continue }
                val taille = lisEntier32(octets, debut)
                val fin = debut + taille
                if (taille in 1..octets.size && fin <= octets.size) {
                    val hauteur = lisEntier32(octets, fin - 4) ushr 16
                    if (hauteur in 1..4320) return hauteur
                }
            }
            i++
        }
        return null
    }

    private fun lisEntier32(o: ByteArray, at: Int): Int =
        ((o[at].toInt() and 0xFF) shl 24) or
            ((o[at + 1].toInt() and 0xFF) shl 16) or
            ((o[at + 2].toInt() and 0xFF) shl 8) or
            (o[at + 3].toInt() and 0xFF)

    private fun ByteArray.regionMatches(at: Int, other: ByteArray): Boolean {
        if (at + other.size > size) return false
        for (k in other.indices) if (this[at + k] != other[k]) return false
        return true
    }
}
