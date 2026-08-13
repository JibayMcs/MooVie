package fr.moovie.tv.data.settings

import androidx.compose.runtime.mutableStateOf
import fr.moovie.tv.data.store.moovieDataStoreFile
import java.util.Locale

/** Langues proposées. `tag == null` = suivre la langue du système. */
enum class DesktopLanguage(val tag: String?) {
    SYSTEM(null),
    FRENCH("fr"),
    ENGLISH("en"),
    SPANISH("es"),
}

/**
 * La langue de l'application sur desktop.
 *
 * ### Ce qui manquait
 *
 * Le réglage existait sur Android et **nulle part ailleurs** : l'écran des
 * réglages affichait, côté desktop, un simple libellé « Langue du système » que
 * rien ne rendait cliquable. On ne pouvait donc pas choisir, et l'application
 * suivait `Locale.getDefault()` sans recours — d'où l'impression de langues
 * mélangées dès que la locale du poste ne correspondait pas à ce qu'on voulait
 * lire : l'interface d'un côté, les dates, l'heure du lecteur et les
 * métadonnées TMDB de l'autre, toutes tirées de la même source qu'on ne pouvait
 * pas corriger.
 *
 * ### Un fichier de propriétés, pas le DataStore
 *
 * La langue doit être appliquée **avant la première composition** — c'est
 * `Locale.setDefault` qui décide de ce que les ressources Compose, les
 * formateurs de dates et la requête TMDB rendront. Or le DataStore se lit dans
 * une coroutine : le temps qu'il réponde, l'écran est déjà dessiné dans
 * l'ancienne langue. Un fichier lu de façon synchrone au démarrage lève ce
 * problème, au prix d'un format de plus, minuscule.
 */
object DesktopLocale {

    private val fichier = java.io.File(moovieDataStoreFile("langue").parentFile, "langue.properties")

    /**
     * Change à chaque choix, et sert de clé de recomposition à la racine.
     *
     * Sans elle, `Locale.setDefault` change bien la langue mais rien ne
     * redemande les chaînes : l'écran reste tel quel jusqu'au prochain
     * redessin, ce qui donne un réglage qui a l'air de ne pas marcher.
     */
    val generation = mutableStateOf(0)

    fun current(): DesktopLanguage {
        val tag = runCatching {
            if (!fichier.isFile) return@runCatching null
            java.util.Properties().apply { fichier.inputStream().use { load(it) } }
                .getProperty("language")
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
        return DesktopLanguage.entries.firstOrNull { it.tag == tag } ?: DesktopLanguage.SYSTEM
    }

    /**
     * Pose la locale choisie. À appeler **au tout début** de `main`.
     *
     * `SYSTEM` ne touche à rien : la valeur par défaut de la JVM est déjà celle
     * du poste, et la réécrire ne ferait que risquer d'en perdre la variante
     * régionale (`fr_BE` réduit à `fr`).
     */
    fun apply() {
        val tag = current().tag ?: return
        runCatching { Locale.setDefault(Locale.forLanguageTag(tag)) }
    }

    /** Enregistre, applique, et fait redessiner l'application. */
    fun set(language: DesktopLanguage) {
        runCatching {
            fichier.parentFile?.mkdirs()
            java.util.Properties().apply {
                setProperty("language", language.tag.orEmpty())
                fichier.outputStream().use { store(it, "Moo-vie") }
            }
        }
        // La locale d'abord, la recomposition ensuite : dans l'autre ordre,
        // l'écran se redessinerait dans l'ancienne langue.
        if (language.tag == null) {
            runCatching { Locale.setDefault(Locale.forLanguageTag(systemTag())) }
        } else {
            runCatching { Locale.setDefault(Locale.forLanguageTag(language.tag)) }
        }
        generation.value += 1
    }

    /**
     * La langue du système, retenue au démarrage.
     *
     * Relevée **avant** toute écriture de `Locale.setDefault`, sans quoi revenir
     * à « Langue du système » après avoir choisi l'anglais rendrait l'anglais :
     * la valeur par défaut de la JVM n'est plus celle du poste dès qu'on y a
     * touché une fois.
     */
    private val systemeAuDemarrage: String = Locale.getDefault().toLanguageTag()

    private fun systemTag(): String = systemeAuDemarrage
}
