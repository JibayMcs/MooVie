package fr.moovie.tv.core.sources.usecase

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.port.SourceExtractor

/**
 * Politique de résolution d'un lien d'embed en flux jouable.
 *
 * Deux règles, dans cet ordre :
 *
 *  1. **par domaine** — le premier extracteur qui reconnaît l'URL. Chemin
 *     normal, aucune requête gaspillée.
 *  2. **par reniflage** — si personne ne revendique l'URL, ou si celui qui la
 *     revendiquait a échoué, les renifleurs sont essayés à leur tour : ils
 *     téléchargent la page et s'identifient sur sa structure. C'est ce qui rend
 *     l'app insensible aux rotations de domaines, mensuelles chez certains
 *     hébergeurs.
 *
 * La **sonde de jouabilité** n'est délibérément pas ici : elle appartient à la
 * cascade, qui s'en sert pour écarter durablement un lien refusé. La rejouer à
 * ce niveau doublerait les requêtes et masquerait la différence entre « pas
 * résolu » et « résolu mais mort ».
 *
 * Sans I/O ni dépendance de plateforme : tout passe par les ports, donc la
 * politique se teste avec de faux extracteurs, sans réseau.
 */
class StreamResolution(
    private val extractors: List<SourceExtractor>,
    private val sniffers: List<SourceExtractor> = emptyList(),
) {

    /** Extracteur qui revendique ce domaine, ou null. */
    fun extractorFor(url: String): SourceExtractor? =
        extractors.firstOrNull { it.canHandle(url) }

    /**
     * true si un extracteur revendique ce domaine. Ne préjuge pas du résultat :
     * un lien non revendiqué reste résoluble par reniflage.
     */
    fun claimsDomain(url: String): Boolean = extractorFor(url) != null

    /** Flux pour ce lien, ou null si aucune règle n'aboutit. */
    suspend fun resolve(link: EmbedLink): PlayableStream? {
        val matched = extractorFor(link.url)
        attempt(matched, link)?.let { return it }

        for (sniffer in sniffers) {
            if (sniffer === matched) continue // déjà tenté à l'étape 1
            attempt(sniffer, link)?.let { return it }
        }
        return null
    }

    /**
     * Un extracteur qui lève (regex, parsing, réseau) ne doit pas interrompre la
     * chaîne : on le traite comme un échec et on passe au suivant.
     */
    private suspend fun attempt(extractor: SourceExtractor?, link: EmbedLink): PlayableStream? =
        try {
            extractor?.extract(link)
        } catch (_: Throwable) {
            null
        }
}
