package fr.moovie.tv.core.sources.port

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef

/**
 * Un catalogue de sources : pour un titre donné, il rend les liens d'embed
 * d'hébergeurs (voe, uqload…), la langue renseignée quand le site la déclare.
 *
 * C'est un **port** : le domaine ne sait pas si l'implémentation scrape du HTML,
 * appelle une API JSON ou lit un fichier. Une source qui meurt devient un
 * adaptateur qui rend une liste vide — la cascade passe à la suivante, et aucun
 * autre fichier n'a à changer.
 */
interface SourceProvider {

    /** Identifiant stable, affiché dans les réglages et utilisé pour l'ordre. */
    val name: String

    /**
     * Liens d'embed pour ce média, ou liste vide si le catalogue ne l'a pas.
     *
     * Ne doit pas lever : un site injoignable est un cas normal, pas une erreur
     * exceptionnelle. Les implémentations qui ne gèrent pas les séries rendent
     * simplement une liste vide pour [MediaRef.Episode].
     */
    suspend fun sourcesFor(media: MediaRef): List<EmbedLink>
}
