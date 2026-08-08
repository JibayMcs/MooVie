package fr.moovie.tv.ui.download

import fr.moovie.tv.data.download.Download
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Le regroupement des téléchargements par titre.
 *
 * Le défaut qu'il empêche s'est vu à l'écran : onze épisodes de Reacher
 * regroupés, et quatre autres de la même série en lignes isolées. Le
 * regroupement se faisait sur `isTv`, un champ facultatif que le bouton du
 * lecteur n'écrivait pas — ses téléchargements retombaient donc sur `false`,
 * puis sur leur clé entière, unique par épisode.
 *
 * La clé, elle, est l'identité du média : elle ne peut pas être oubliée.
 */
class DownloadGroupKeyTest {

    private fun dl(key: String, isTv: Boolean) = Download(key = key, title = "x", isTv = isTv)

    /** Le cas observé : le même titre, écrit par deux chemins différents. */
    @Test
    fun `regroupe malgre un isTv oublie`() {
        val items = listOf(
            dl("tv:1396:s2e2", isTv = true),   // appui long : champ renseigné
            dl("tv:1396:s3e3", isTv = false),  // bouton du lecteur : champ oublié
        )
        assertEquals(1, items.map { it.groupKey() }.distinct().size)
    }

    /** Toutes saisons confondues : c'est la série qu'on regroupe, pas la saison. */
    @Test
    fun `ignore la saison`() {
        assertEquals(
            dl("tv:1396:s1e1", true).groupKey(),
            dl("tv:1396:s9e12", true).groupKey(),
        )
    }

    /** Un film reste seul : le grouper avec lui-même n'ajouterait qu'un pli. */
    @Test
    fun `laisse les films seuls`() {
        assertEquals("movie:550", dl("movie:550", false).groupKey())
        assertEquals(
            2,
            listOf(dl("movie:550", false), dl("movie:551", false))
                .map { it.groupKey() }.distinct().size,
        )
    }

    /** Deux séries différentes ne se mélangent pas. */
    @Test
    fun `separe deux series`() {
        assertEquals(
            2,
            listOf(dl("tv:1396:s1e1", true), dl("tv:1400:s1e1", true))
                .map { it.groupKey() }.distinct().size,
        )
    }
}

/** Miroir de la règle privée de DownloadsSection, verrouillée ici. */
private fun Download.groupKey(): String =
    if (key.startsWith("tv:")) key.split(':').take(2).joinToString(":") else key
