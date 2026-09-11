package fr.moovie.tv.ui.player

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Le clignotement des sous-titres, à sa racine : une sélection forcée survit mal
 * à une republication des pistes.
 */
class SubtitleSelectionTest {

    private fun texte(id: String?, langue: String? = null, libelle: String? = null) =
        Format.Builder()
            .setId(id)
            .setLanguage(langue)
            .setLabel(libelle)
            .setSampleMimeType(MimeTypes.APPLICATION_SUBRIP)
            .build()

    private fun groupe(vararg formats: Format, selectionnee: Int = -1, id: String = "0") =
        Tracks.Group(
            TrackGroup(id, *formats),
            false,
            IntArray(formats.size) { androidx.media3.common.C.FORMAT_HANDLED },
            BooleanArray(formats.size) { it == selectionnee },
        )

    /**
     * Le mécanisme du bug : la clé d'une sélection forcée est le `TrackGroup`,
     * dont l'égalité comprend l'identifiant préfixé par la période. Republier
     * les pistes après une re-préparation suffit à orpheliner l'override.
     */
    @Test
    fun `une override ne survit pas au changement de periode`() {
        val avant = TrackGroup("1:ext", texte("1:$EXTERNAL_SUBTITLE_ID"))
        val apres = TrackGroup("2:ext", texte("2:$EXTERNAL_SUBTITLE_ID"))

        assertNotEquals(avant, apres)
        assertNotEquals(
            TrackSelectionOverride(avant, 0).mediaTrackGroup,
            TrackSelectionOverride(apres, 0).mediaTrackGroup,
        )
    }

    @Test
    fun `le souhait externe se retrouve malgre le changement de periode`() {
        val apres = listOf(groupe(texte("2:$EXTERNAL_SUBTITLE_ID"), id = "2:ext"))

        val cible = apres.findSubtitle(SubtitleWish.External)

        assertNotNull(cible, "le sous-titre externe doit rester trouvable après re-préparation")
        assertEquals(0, cible.second)
    }

    @Test
    fun `une piste du flux se retrouve par sa langue quand l identifiant a change`() {
        val choisi = groupe(texte("1:sub0", langue = "fr", libelle = "Français"), id = "1:g")
        val souhait = choisi.toWish(0)

        val republie = listOf(groupe(texte("2:sub0", langue = "fr", libelle = "Français"), id = "2:g"))

        assertNotNull(republie.findSubtitle(souhait))
    }

    /**
     * Deux pistes de la même langue — une complète, une « forcée » — ne se
     * confondent pas : l'identifiant est plus précis que la langue, et c'est lui
     * qui décide tant qu'il correspond.
     */
    @Test
    fun `deux pistes d une meme langue ne se confondent pas`() {
        val forcee = texte("1:sub0", langue = "fr", libelle = "Français (forcés)")
        val complete = texte("1:sub1", langue = "fr", libelle = "Français")
        val souhait = groupe(forcee, complete, id = "1:g").toWish(1)

        // Republication : les identifiants changent de préfixe, l'ordre reste.
        val republie = listOf(
            groupe(
                texte("2:sub0", langue = "fr", libelle = "Français (forcés)"),
                texte("2:sub1", langue = "fr", libelle = "Français"),
                id = "2:g",
            ),
        )

        assertEquals(1, republie.findSubtitle(souhait)?.second)
    }

    /**
     * Même sans identifiant exploitable, le libellé passe avant la langue :
     * s'en remettre à la langue choisirait la première piste venue.
     */
    @Test
    fun `sans identifiant le libelle departage avant la langue`() {
        val souhait = SubtitleWish.Stream(formatId = null, language = "fr", label = "Français")
        val pistes = listOf(
            groupe(
                texte(null, langue = "fr", libelle = "Français (forcés)"),
                texte(null, langue = "fr", libelle = "Français"),
                id = "g",
            ),
        )

        assertEquals(1, pistes.findSubtitle(souhait)?.second)
    }

    @Test
    fun `choisir une piste du flux ne se confond pas avec le fichier externe`() {
        val souhait = SubtitleWish.Stream(formatId = null, language = "fr", label = null)
        val externe = listOf(groupe(texte("1:$EXTERNAL_SUBTITLE_ID", langue = "fr"), id = "1:ext"))

        assertEquals(null, externe.findSubtitle(souhait))
    }

    @Test
    fun `satisfies voit la difference entre une selection tenue et une selection perdue`() {
        val tenue = listOf(groupe(texte("1:$EXTERNAL_SUBTITLE_ID"), selectionnee = 0, id = "1:ext"))
        val perdue = listOf(groupe(texte("2:$EXTERNAL_SUBTITLE_ID"), id = "2:ext"))

        assertTrue(tenue.satisfies(SubtitleWish.External))
        assertFalse(perdue.satisfies(SubtitleWish.External))
    }

    @Test
    fun `sans sous-titre selectionne le souhait Off est satisfait`() {
        val aucune = listOf(groupe(texte("1:sub0", langue = "fr"), id = "1:g"))

        assertTrue(aucune.satisfies(SubtitleWish.Off))
        assertEquals(null, aucune.findSubtitle(SubtitleWish.Off))
    }
}
