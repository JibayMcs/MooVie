package fr.moovie.tv.core.sources.port

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream

/**
 * Transforme un lien d'embed d'hébergeur en flux jouable. Un extracteur par
 * hébergeur — ou par *forme de page*, voir [canHandle].
 */
interface SourceExtractor {

    /** Identifiant d'hébergeur, ex. « voe », « uqload ». */
    val hoster: String

    /**
     * true si cet extracteur reconnaît cette URL à son seul domaine.
     *
     * Renvoyer false n'interdit pas de résoudre le lien : un extracteur peut
     * n'être qu'un **renifleur**, enregistré comme tel, essayé en dernier
     * recours et capable de s'identifier sur le contenu de la page. C'est ce qui
     * permet de suivre les hébergeurs qui renouvellent leurs domaines tous les
     * mois — VOE en tête — sans publier une version de l'app à chaque rotation.
     */
    fun canHandle(url: String): Boolean

    /**
     * Résout le flux, ou null si échec (source morte, format changé, page qui
     * n'est pas la sienne).
     *
     * **Contrat du reniflage** : sur une page qui ne lui appartient pas, un
     * extracteur doit rendre null sans effet de bord. C'est ce qui rend la
     * chaîne de renifleurs sûre.
     */
    suspend fun extract(link: EmbedLink): PlayableStream?
}
