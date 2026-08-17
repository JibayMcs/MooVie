package fr.moovie.tv.data.remote

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ce que le téléviseur a le droit d'écrire quand il n'est qu'un écran.
 *
 * La règle : il n'enregistre que si l'on peut **prouver** que les deux appareils
 * écrivent au même endroit. Sinon, diffuser un titre polluerait un compte qui
 * n'a rien demandé — on retrouverait dans le « Reprendre » du salon des titres
 * lancés depuis le téléphone de quelqu'un d'autre.
 *
 * Ce qui est tenu ici est surtout la **portée** de l'oubli. Un booléen global
 * resté allumé ferait cesser au téléviseur d'enregistrer *ses propres* lectures,
 * sans que rien ne le signale : le défaut durerait jusqu'à ce qu'on remarque que
 * la box ne retient plus rien. En retenant la clé du média, l'oubli ne peut
 * porter que sur ce titre-là.
 */
class RemoteCastTest {

    @AfterTest
    fun cleanup() = RemoteCast.clear()

    @Test
    fun `le titre diffuse ne s enregistre pas`() {
        RemoteCast.markEphemeral("tv:66765:s2e6")

        assertTrue(RemoteCast.isEphemeral("tv:66765:s2e6"))
    }

    /** Le point qui rend un oubli inoffensif. */
    @Test
    fun `les autres titres continuent de s enregistrer`() {
        RemoteCast.markEphemeral("tv:66765:s2e6")

        assertFalse(RemoteCast.isEphemeral("tv:66765:s2e7"), "l'épisode suivant")
        assertFalse(RemoteCast.isEphemeral("movie:550"), "un film lancé localement")
    }

    @Test
    fun `une lecture locale reprend la main`() {
        RemoteCast.markEphemeral("movie:550")
        RemoteCast.clear()

        assertFalse(RemoteCast.isEphemeral("movie:550"))
    }

    /** Une clé vide ne doit jamais désigner « tout ». */
    @Test
    fun `une cle vide ne marque rien`() {
        RemoteCast.markEphemeral("")

        assertFalse(RemoteCast.isEphemeral(""))
        assertFalse(RemoteCast.isEphemeral("movie:550"))
    }

    /**
     * Le défaut de la demande : un téléphone d'avant cette version n'envoie pas
     * le champ, et doit garder le comportement qu'il avait.
     */
    @Test
    fun `une demande sans consigne autorise l enregistrement`() {
        assertTrue(PlayRequest(tmdbId = 1, isTv = false).record)
    }
}
