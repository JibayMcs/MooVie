package fr.moovie.tv.data.settings

import fr.moovie.tv.data.store.useFileStores
import fr.moovie.tv.data.store.useInMemoryStores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La question « faut-il montrer l'installation ? », sous ses quatre angles.
 *
 * Elle a l'air simple et ne l'est pas : la réponse doit distinguer trois
 * situations qui se ressemblent toutes par la présence d'une clé TMDB — une
 * installation d'avant le parcours, un parcours terminé, et un parcours
 * abandonné juste après sa première question. Se tromper sur la première
 * imposerait un questionnaire à qui utilise l'application depuis des mois ; se
 * tromper sur la troisième escamoterait trois questions sur quatre.
 *
 * D'où un test plutôt qu'une relecture : la règle tient en une ligne de
 * `SettingsRepository`, et c'est exactement le genre de ligne qu'une
 * simplification ultérieure croit pouvoir raccourcir.
 */
class OnboardingDoneTest {

    @BeforeTest
    fun avant() = useInMemoryStores()

    @AfterTest
    fun apres() = useFileStores()

    @Test
    fun `une installation neuve doit passer par le parcours`() = runTest {
        assertFalse(SettingsRepository().onboardingDone.first())
    }

    @Test
    fun `une installation d'avant le parcours en est dispensée`() = runTest {
        // Ce que laisse une mise à jour : une clé, et aucune trace du parcours,
        // qui n'existait pas quand elle a été saisie.
        val reglages = SettingsRepository()
        reglages.setTmdbApiKey("ffffffffffffffffffffffffffffffff")

        assertTrue(
            reglages.onboardingDone.first(),
            "une mise à jour ne doit pas rouvrir l'installation",
        )
    }

    @Test
    fun `un parcours interrompu après la clé reprend`() = runTest {
        val reglages = SettingsRepository()
        // Entrée dans le questionnaire : l'installation se déclare inachevée
        // avant même la première réponse.
        reglages.setOnboardingDone(false)
        // Première question franchie — la clé validée est écrite tout de suite.
        reglages.setTmdbApiKey("ffffffffffffffffffffffffffffffff")

        assertFalse(
            reglages.onboardingDone.first(),
            "la clé seule ne vaut pas installation terminée pendant le parcours",
        )
    }

    @Test
    fun `un parcours mené à terme ne se rouvre pas`() = runTest {
        val reglages = SettingsRepository()
        reglages.setOnboardingDone(false)
        reglages.setTmdbApiKey("ffffffffffffffffffffffffffffffff")
        reglages.setOnboardingDone(true)

        assertTrue(reglages.onboardingDone.first())
    }

    @Test
    fun `une restauration sans clé laisse l'installation à faire`() = runTest {
        // Le cas de la sauvegarde importée d'un appareil qui n'avait pas de clé
        // non plus : rien n'a été rapporté, il reste tout à demander.
        val reglages = SettingsRepository()
        reglages.setStreamLanguage(StreamLanguage.VOSTFR)

        assertFalse(reglages.onboardingDone.first())
    }
}
