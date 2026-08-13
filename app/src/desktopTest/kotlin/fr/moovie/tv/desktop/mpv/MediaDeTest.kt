package fr.moovie.tv.desktop.mpv

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Le média sur lequel les sondes du moteur mesurent : douze secondes dont la
 * **luminance encode le temps** (`lum = 15 × t`), avec une sinusoïde en piste
 * audio. Un seek vérifié sur l'horodatage seul croirait un lecteur qui ment ;
 * la luminance permet de vérifier **sur l'image** qu'on est bien à la position
 * annoncée.
 *
 * Généré par le ffmpeg du système — la branche n'embarque plus de FFmpeg à
 * elle. Machine sans ffmpeg : les sondes s'abstiennent en le disant, elles ne
 * rougissent pas pour un outil absent.
 */
internal object MediaDeTest {

    const val DUREE_S = 12
    const val CADENCE = 25

    /** Pente de la rampe : lum = PENTE_Y × t, donc t = lum / PENTE_Y. */
    const val PENTE_Y = 15.0

    /** Le fichier, fabriqué au premier appel et réutilisé ensuite. Null sans ffmpeg. */
    val fichier: File? by lazy { fabrique("moovie-mpv-media-de-test.mkv", video = true, audio = true) }

    /**
     * Les deux mêmes pistes, dans deux fichiers séparés — la forme sous
     * laquelle YouTube sert ses bandes-annonces : plus aucun flux progressif,
     * tout en pistes distinctes. Le moteur doit savoir les tenir ensemble.
     */
    val fichierVideo: File? by lazy { fabrique("moovie-mpv-video-de-test.mkv", video = true, audio = false) }

    val fichierAudio: File? by lazy { fabrique("moovie-mpv-audio-de-test.mkv", video = false, audio = true) }

    private fun fabrique(nom: String, video: Boolean, audio: Boolean): File? {
        val cible = File(System.getProperty("java.io.tmpdir"), nom)
        if (cible.exists() && cible.length() > 0) return cible
        if (!genere(cible, video, audio)) return null
        cible.deleteOnExit()
        return cible
    }

    private fun genere(cible: File, video: Boolean, audio: Boolean): Boolean = runCatching {
        val arguments = buildList {
            addAll(listOf("ffmpeg", "-y"))
            if (video) {
                addAll(
                    listOf(
                        "-f", "lavfi", "-i",
                        "color=black:s=320x240:r=$CADENCE:d=$DUREE_S,geq=lum='$PENTE_Y*T':cb=128:cr=128",
                    ),
                )
            }
            if (audio) {
                addAll(listOf("-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100:duration=$DUREE_S"))
            }
            if (video) addAll(listOf("-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p"))
            if (audio) addAll(listOf("-c:a", "aac"))
            if (video && audio) add("-shortest")
            add(cible.absolutePath)
        }
        val processus = ProcessBuilder(arguments).redirectErrorStream(true).start()
        processus.inputStream.readBytes()
        processus.waitFor(60, TimeUnit.SECONDS) && processus.exitValue() == 0 && cible.length() > 0
    }.getOrDefault(false)

    /**
     * Luminosité moyenne d'une trame BGRA grise : les trois canaux sont égaux,
     * le vert fait foi. Échantillonné, pas exhaustif — la précision utile est
     * de l'ordre d'une seconde de rampe, soit quinze niveaux.
     */
    fun luminosite(trame: TrameVideo): Double {
        var somme = 0L
        var n = 0
        var i = 1 // canal G du premier pixel (ordre B,G,R,A)
        while (i < trame.pixels.size) {
            somme += trame.pixels[i].toInt() and 0xFF
            n++
            i += PAS_ECHANTILLON
        }
        return if (n == 0) 0.0 else somme.toDouble() / n
    }

    private const val PAS_ECHANTILLON = 4 * 97
}
