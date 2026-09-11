package fr.moovie.tv.desktop.mpv

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Sonde de diagnostic du rendu des sous-titres, sur un vrai libmpv.
 *
 * Comme les autres sondes du dépôt, elle ne tourne que si on lui donne de quoi
 * jouer — sans quoi elle se retire, pour ne pas faire échouer une CI qui n'a ni
 * fichier ni pilote vidéo :
 *
 * ```
 * ffmpeg -f lavfi -i color=c=black:s=640x360:r=25 -t 12 -c:v libx264 -pix_fmt yuv420p /tmp/test.mp4
 * PROBE_VIDEO=/tmp/test.mp4 PROBE_SRT=/tmp/a.srt PROBE_SRT2=/tmp/b.srt \
 *   ./gradlew :app:desktopTest --rerun-tasks --tests '*MpvSubtitleFlickerProbe'
 * ```
 *
 * Les deux SRT portent une réplique **affichée en continu** sur toute la durée.
 * La vidéo étant noire, une trame sans pixel clair est une trame où le
 * sous-titre a disparu : le clignotement se compte, il ne s'apprécie pas à
 * l'œil.
 */
class MpvSubtitleFlickerProbe {

    /** Vrai si la trame porte au moins un pixel clair, donc du texte. */
    private fun porteTexte(trame: TrameVideo): Boolean {
        var i = 0
        while (i < trame.pixels.size) {
            if (trame.pixels[i].toInt() and 0xFF > SEUIL_CLAIR) return true
            i += PAS_PIXEL
        }
        return false
    }

    /**
     * Le sous-titre monté par l'application reste affiché de bout en bout.
     *
     * C'est la mesure de référence : elle vérifie la chaîne de rendu elle-même
     * — boucle de rendu, publication des trames, style — indépendamment de
     * toute manipulation de pistes.
     */
    @Test
    fun `un sous-titre affiche en continu ne clignote pas`() {
        val video = System.getenv("PROBE_VIDEO") ?: return
        val srt = System.getenv("PROBE_SRT") ?: return

        val trames = mutableListOf<Boolean>()
        val moteur = MpvEngine(
            surImage = { t -> synchronized(trames) { trames += porteTexte(t) } },
            surErreur = { println("[erreur] $it") },
        )
        check(moteur.ouvre(video)) { "ouverture impossible" }
        moteur.sousTitreExterne(srt)
        Thread.sleep(LECTURE_MS)
        moteur.ferme()

        val vues = synchronized(trames) { trames.toList() }
        // La première trame précède le montage du fichier : elle ne compte pas.
        val apresMontage = vues.drop(1)
        val sans = apresMontage.count { !it }
        println("PROBE continu trames=${apresMontage.size} sansTexte=$sans")
        assertTrue(apresMontage.size > MIN_TRAMES, "trop peu de trames pour conclure")
        assertTrue(sans == 0, "$sans trames sans sous-titre alors qu'il est affiché en continu")
    }

    /**
     * Choisir une piste du flux ne fait pas disparaître ce qu'on vient de
     * choisir.
     *
     * Reproduit l'ordre exact du menu : `clear()` puis `selectSubtitle`, le
     * retrait n'arrivant qu'une recomposition plus tard. Avec un `sub-remove`
     * sans identifiant, ce retrait emportait la piste fraîchement sélectionnée
     * et l'écran restait sans texte.
     */
    @Test
    fun `retirer notre fichier ne retire pas la piste choisie entre-temps`() {
        val video = System.getenv("PROBE_VIDEO") ?: return
        val notre = System.getenv("PROBE_SRT") ?: return
        val duFlux = System.getenv("PROBE_SRT2") ?: return

        val trames = mutableListOf<Boolean>()
        val moteur = MpvEngine(
            surImage = { t -> synchronized(trames) { trames += porteTexte(t) } },
            surErreur = { println("[erreur] $it") },
        )
        check(moteur.ouvre(video)) { "ouverture impossible" }

        // Ce que fait l'écran à l'ouverture : les sous-titres de la source sont
        // ajoutés sans être activés, puis l'utilisateur choisit le nôtre.
        moteur.ajouteSousTitres(listOf(duFlux))
        moteur.sousTitreExterne(notre)
        Thread.sleep(ATTENTE_COURTE_MS)

        // Le menu : la piste du flux est celle qui n'est pas active.
        val avant = moteur.pistes("sub")
        val cible = avant.firstOrNull { !it.active }
        check(cible != null) { "la piste de la source n'a pas été ajoutée" }
        moteur.selectionneSousTitre(cible.id)
        // Le retrait que la recomposition déclenche juste après.
        moteur.sousTitreExterne(null)

        synchronized(trames) { trames.clear() }
        Thread.sleep(ATTENTE_COURTE_MS)
        val apres = synchronized(trames) { trames.toList() }
        val pistesApres = moteur.pistes("sub")
        moteur.ferme()

        // Le retrait doit avoir réellement eu lieu : un `sub-remove` qui échoue
        // en silence laisserait ce test passer tout en empilant une piste à
        // chaque réglage de synchronisation.
        println("PROBE pistes avant=${avant.size} apres=${pistesApres.size}")
        assertTrue(
            pistesApres.size == avant.size - 1,
            "notre piste n'a pas été retirée : ${avant.size} puis ${pistesApres.size}",
        )
        assertTrue(
            pistesApres.any { it.id == cible.id },
            "la piste choisie a été retirée du média",
        )

        val avecTexte = apres.count { it }
        println("PROBE apresChoix trames=${apres.size} avecTexte=$avecTexte")
        assertTrue(apres.size > MIN_TRAMES, "trop peu de trames pour conclure")
        assertTrue(
            avecTexte == apres.size,
            "la piste choisie a disparu : $avecTexte/${apres.size} trames portent du texte",
        )
    }

    private companion object {
        /** Le fond est noir pur : ce seuil sépare le texte du bruit de codec. */
        const val SEUIL_CLAIR = 0x40
        const val PAS_PIXEL = 4
        const val LECTURE_MS = 8_000L
        const val ATTENTE_COURTE_MS = 3_000L
        const val MIN_TRAMES = 20
    }
}
