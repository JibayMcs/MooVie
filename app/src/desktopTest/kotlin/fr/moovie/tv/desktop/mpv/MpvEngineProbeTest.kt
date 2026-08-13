package fr.moovie.tv.desktop.mpv

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Le moteur mpv mesuré sur un média local : cadence, pause, seek, fin de flux.
 *
 * Ces mesures sont exactement celles qui ont condamné les deux lecteurs
 * précédents — le seek HLS de libVLC posé sept à dix secondes avant la cible,
 * la barre qui avance sur une image figée. Les refaire sur mpv n'est pas de la
 * cérémonie : c'est le contrat d'entrée du remplaçant.
 *
 * La position d'un saut est vérifiée **sur l'image** (luminance = temps, voir
 * [MediaDeTest]) et pas seulement sur l'horodatage : un lecteur qui rend la
 * position demandée plutôt que la sienne — libVLC pendant 1,5 s après chaque
 * seek — passerait un test qui ne regarde que les nombres.
 */
class MpvEngineProbeTest {

    private class Image(val luminosite: Double, val recueA: Long)

    /** Vrai si la machine permet la mesure ; sinon on s'abstient en le disant. */
    private fun outillage(): Boolean {
        if (Libmpv.instance == null) {
            println("[sonde mpv] ignorée — libmpv introuvable sur cette machine")
            return false
        }
        if (MediaDeTest.fichier == null) {
            println("[sonde mpv] ignorée — ffmpeg absent, pas de média de test")
            return false
        }
        return true
    }

    @Test
    fun `les images sortent a la cadence du temps reel, et la pause les arrete`() {
        if (!outillage()) return
        val images = CopyOnWriteArrayList<Image>()
        val moteur = MpvEngine(
            surImage = { images += Image(MediaDeTest.luminosite(it), System.currentTimeMillis()) },
        )
        try {
            assertTrue(moteur.ouvre(MediaDeTest.fichier!!.absolutePath), "le média de test ne s'ouvre pas")

            Thread.sleep(OBSERVATION_MS)
            val recues = images.size
            val position = moteur.positionMs()
            assertTrue(recues > CADENCE_MINIMALE, "trop peu d'images : $recues en ${OBSERVATION_MS} ms")
            assertTrue(
                abs(position - OBSERVATION_MS) < TOLERANCE_MS,
                "position hors du temps réel : $position ms après $OBSERVATION_MS ms",
            )

            moteur.pause(true)
            Thread.sleep(REPIT_MS)
            val figees = images.size
            val positionFigee = moteur.positionMs()
            Thread.sleep(REPIT_MS)
            assertTrue(images.size - figees <= IMAGES_TOLEREES_EN_PAUSE, "des images sortent en pause")
            assertTrue(
                abs(moteur.positionMs() - positionFigee) < DERIVE_PAUSE_MS,
                "la position avance en pause : $positionFigee → ${moteur.positionMs()}",
            )

            moteur.pause(false)
            Thread.sleep(REPIT_MS)
            assertTrue(images.size > figees, "la reprise ne produit plus d'images")
            println("[sonde mpv] $recues images en ${OBSERVATION_MS} ms, pause étanche, reprise vivante")
        } finally {
            moteur.ferme()
        }
    }

    @Test
    fun `le saut atterrit a la position demandee, image a l'appui`() {
        if (!outillage()) return
        val images = CopyOnWriteArrayList<Image>()
        val moteur = MpvEngine(
            surImage = { images += Image(MediaDeTest.luminosite(it), System.currentTimeMillis()) },
        )
        try {
            assertTrue(moteur.ouvre(MediaDeTest.fichier!!.absolutePath), "le média de test ne s'ouvre pas")
            Thread.sleep(REPIT_MS)

            val demande = System.currentTimeMillis()
            moteur.seek(CIBLE_MS)
            // Pendant le saut, la position rendue est la cible : c'est ce qui
            // permet à deux appuis rapides de partir de la bonne base.
            assertTrue(
                moteur.positionMs() == CIBLE_MS,
                "pendant le saut la position devrait être la cible, pas ${moteur.positionMs()}",
            )

            // L'image qui prouve : luminance = 15 × t, donc ~135 à 9 s.
            val attendue = MediaDeTest.PENTE_Y * CIBLE_MS / 1000
            val limite = System.currentTimeMillis() + VERDICT_MAX_MS
            var posee: Image? = null
            while (posee == null && System.currentTimeMillis() < limite) {
                posee = images.lastOrNull {
                    it.recueA > demande && abs(it.luminosite - attendue) < MediaDeTest.PENTE_Y
                }
                if (posee == null) Thread.sleep(50)
            }
            assertTrue(posee != null, "aucune image de la position cible ${CIBLE_MS} ms n'est arrivée")

            val position = moteur.positionMs()
            assertTrue(
                abs(position - CIBLE_MS) < TOLERANCE_SEEK_MS,
                "position après saut : $position ms pour une cible à $CIBLE_MS ms",
            )
            println(
                "[sonde mpv] saut à $CIBLE_MS ms : image de luminance ${posee.luminosite.toInt()} " +
                    "(attendue ~${attendue.toInt()}), position $position ms",
            )
        } finally {
            moteur.ferme()
        }
    }

    @Test
    fun `la fin de flux est annoncee, une seule fois le media epuise`() {
        if (!outillage()) return
        val fin = CountDownLatch(1)
        val moteur = MpvEngine(surImage = {}, surFin = { fin.countDown() })
        try {
            // Départ à une seconde et demie de la fin : prouve au passage que
            // la position de départ est honorée.
            val depart = MediaDeTest.DUREE_S * 1000L - 1_500
            assertTrue(moteur.ouvre(MediaDeTest.fichier!!.absolutePath, departMs = depart))
            assertTrue(moteur.positionMs() >= depart - TOLERANCE_MS, "le départ n'est pas honoré")
            assertTrue(fin.await(FIN_MAX_S, TimeUnit.SECONDS), "la fin de flux n'a pas été annoncée")
            println("[sonde mpv] départ à $depart ms honoré, fin annoncée")
        } finally {
            moteur.ferme()
        }
    }

    @Test
    fun `deux entrees separees se lisent ensemble`() {
        if (!outillage()) return
        val video = MediaDeTest.fichierVideo
        val audio = MediaDeTest.fichierAudio
        if (video == null || audio == null) {
            println("[sonde mpv] ignorée — ffmpeg n'a pas su séparer les pistes")
            return
        }
        val images = CopyOnWriteArrayList<Image>()
        val moteur = MpvEngine(
            surImage = { images += Image(MediaDeTest.luminosite(it), System.currentTimeMillis()) },
        )
        try {
            assertTrue(
                moteur.ouvre(video.absolutePath, urlAudio = audio.absolutePath),
                "le moteur n'a pas ouvert les deux entrées",
            )
            // Le son vient bien de la seconde entrée : sans elle, le fichier
            // image n'a aucune piste audio à montrer.
            assertTrue(moteur.pistes("audio").isNotEmpty(), "la piste audio externe est invisible")

            Thread.sleep(OBSERVATION_MS)
            assertTrue(images.size > CADENCE_MINIMALE, "trop peu d'images : ${images.size}")
            assertTrue(
                abs(moteur.positionMs() - OBSERVATION_MS) < TOLERANCE_MS,
                "cadence hors du temps réel sur deux entrées : ${moteur.positionMs()} ms",
            )

            // Le saut doit replacer les **deux** démultiplexeurs : celui qui
            // resterait en arrière rejouerait le son de l'ancienne position
            // par-dessus la nouvelle image. La lecture continue pendant
            // l'attente : la position légitime est la cible **plus** le temps
            // écoulé, pas la cible seule.
            moteur.seek(CIBLE_MS)
            Thread.sleep(REPIT_MS * 2)
            val avance = moteur.positionMs() - CIBLE_MS
            assertTrue(
                avance in 0..(REPIT_MS * 2 + TOLERANCE_SEEK_MS),
                "saut à $CIBLE_MS ms posé à ${moteur.positionMs()} ms",
            )
            println("[sonde mpv] deux entrées : ${images.size} images, saut posé à ${moteur.positionMs()} ms")
        } finally {
            moteur.ferme()
        }
    }

    @Test
    fun `les pistes du media sont visibles`() {
        if (!outillage()) return
        val moteur = MpvEngine(surImage = {})
        try {
            assertTrue(moteur.ouvre(MediaDeTest.fichier!!.absolutePath))
            val audio = moteur.pistes("audio")
            assertTrue(audio.isNotEmpty(), "la piste audio du média de test est invisible")
            assertTrue(audio.any { it.active }, "aucune piste audio active")
            println("[sonde mpv] pistes audio : ${audio.joinToString { it.libelle }}")
        } finally {
            moteur.ferme()
        }
    }

    private companion object {
        const val OBSERVATION_MS = 3_000L
        const val REPIT_MS = 600L

        /** 25 i/s pendant 3 s : en réclamer 40 laisse la moitié de marge. */
        const val CADENCE_MINIMALE = 40

        const val TOLERANCE_MS = 900L
        const val TOLERANCE_SEEK_MS = 1_000L
        const val DERIVE_PAUSE_MS = 250L
        const val IMAGES_TOLEREES_EN_PAUSE = 2
        const val CIBLE_MS = 9_000L
        const val VERDICT_MAX_MS = 4_000L
        const val FIN_MAX_S = 8L
    }
}
