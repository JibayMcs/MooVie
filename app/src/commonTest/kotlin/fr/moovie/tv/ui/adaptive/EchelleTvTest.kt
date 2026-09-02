package fr.moovie.tv.ui.adaptive

import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * La correction d'unité des téléviseurs.
 *
 * Ce qu'on vérifie ici n'est pas une mise en page mais une **invariance** :
 * quelle que soit la largeur qu'un téléviseur déclare, l'arbre doit finir par
 * mesurer 960 points. C'est la propriété dont dépend tout le reste, et c'est la
 * seule chose de cette correction qui se teste sans écran.
 */
class EchelleTvTest {

    private fun proche(attendu: Float, obtenu: Float) =
        assertTrue(abs(attendu - obtenu) < 0.001f, "attendu $attendu, obtenu $obtenu")

    @Test
    fun `la box de reference n'est pas touchee`() {
        // 1920 × 1080 à 320 dpi. Toutes les dimensions en dur de l'application
        // ont été relevées dessus : la correction doit y être l'identité, sans
        // quoi elle déréglerait ce qu'elle est censée préserver.
        assertEquals(1f, echelleTv(UiFlavor.TV, 960.dp))
    }

    @Test
    fun `un televiseur qui declare moins de points voit son unite reduite`() {
        // Le cas observé : la même dalle rendue en 1280 pixels au lieu de 1920.
        // Une affiche de 138 points y prenait une fois et demie la place prévue,
        // et la rangée passait de cinq titres à trois.
        proche(2f / 3f, echelleTv(UiFlavor.TV, 640.dp))
    }

    @Test
    fun `un televiseur qui declare plus de points voit son unite agrandie`() {
        proche(1.5f, echelleTv(UiFlavor.TV, 1440.dp))
    }

    @Test
    fun `la largeur logique revient toujours a la reference`() {
        // La propriété qui fait tout tenir : l'arbre mesure 960 points quoi que
        // le téléviseur annonce. Les bornes ne mordent pas sur cette plage.
        listOf(640, 720, 800, 960, 1080, 1280, 1440, 1920).forEach { annoncee ->
            val logique = annoncee.dp / echelleTv(UiFlavor.TV, annoncee.dp)
            proche(960f, logique.value)
        }
    }

    @Test
    fun `hors televiseur le point reste le point`() {
        // Sur un téléphone la densité déclarée veut dire quelque chose : 48
        // points y font la trace du pouce. Corriger l'unité y serait un contre-
        // sens, et la fonction doit rendre 1 sans regarder la largeur.
        listOf(360, 448, 997, 1440).forEach { largeur ->
            assertEquals(1f, echelleTv(UiFlavor.TOUCH, largeur.dp))
            assertEquals(1f, echelleTv(UiFlavor.POINTER, largeur.dp))
        }
    }

    @Test
    fun `une largeur aberrante est bornee plutot que suivie`() {
        // Un mode d'affichage exotique, un `wm density` posé à la main : mieux
        // vaut une interface un peu trop grande qu'une interface illisible.
        assertEquals(0.5f, echelleTv(UiFlavor.TV, 200.dp))
        assertEquals(2.5f, echelleTv(UiFlavor.TV, 5000.dp))
        assertEquals(1f, echelleTv(UiFlavor.TV, 0.dp))
    }
}
