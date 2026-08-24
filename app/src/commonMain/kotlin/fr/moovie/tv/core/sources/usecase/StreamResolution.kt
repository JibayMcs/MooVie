package fr.moovie.tv.core.sources.usecase

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.port.SourceExtractor

/**
 * Politique de résolution d'un lien d'embed en flux jouable.
 *
 * Trois règles, dans cet ordre :
 *
 *  1. **par nom** — l'extracteur que le lien désigne (`EmbedLink.hoster`). Le
 *     catalogue sait de qui vient son lien ; c'est une information sûre, et
 *     jusqu'ici elle était **jetée**.
 *  2. **par domaine** — le premier extracteur qui reconnaît l'URL. Chemin des
 *     liens dont personne n'a nommé l'hébergeur.
 *  3. **par reniflage** — si personne ne revendique l'URL, ou si celui qui la
 *     revendiquait a échoué, les renifleurs sont essayés à leur tour : ils
 *     téléchargent la page et s'identifient sur sa structure. C'est ce qui rend
 *     l'app insensible aux rotations de domaines, mensuelles chez certains
 *     hébergeurs.
 *
 * ## Pourquoi le nom passe devant, et ce que ça a coûté de ne pas l'avoir
 *
 * SwiftFlow a changé le domaine de son CDN. Son extracteur ne reconnaissait plus
 * l'URL, le lien est donc tombé sur `DirectStreamExtractor` — qui revendique
 * tout `.mp4` — et est reparti **sans le `Referer`** que le CDN exige. Celui-ci
 * répond alors une page HTML *en 200*, que la sonde de jouabilité rejette : la
 * source la plus fiable du catalogue s'est éteinte en silence.
 *
 * Le lien portait pourtant `hoster = "swiftflow"` depuis le début. Router sur ce
 * nom rend la résolution insensible aux rotations de CDN : un catalogue qui
 * change d'hébergement ne change pas d'identité.
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

    /**
     * L'extracteur que le lien **nomme**, s'il existe.
     *
     * Comparaison insensible à la casse et rien d'autre : ces identifiants sont
     * des constantes des deux côtés, et un rapprochement approximatif ferait
     * router « premium » vers l'extracteur dont le nom lui ressemble le plus.
     */
    fun extractorNamed(hoster: String): SourceExtractor? =
        hoster.takeIf { it.isNotBlank() }
            ?.let { nom -> extractors.firstOrNull { it.hoster.equals(nom, ignoreCase = true) } }

    /** Flux pour ce lien, ou null si aucune règle n'aboutit. */
    suspend fun resolve(link: EmbedLink): PlayableStream? {
        val named = extractorNamed(link.hoster)
        attempt(named, link)?.let { return it }

        val matched = extractorFor(link.url)
        if (matched !== named) attempt(matched, link)?.let { return it }

        for (sniffer in sniffers) {
            // Déjà tentés aux étapes 1 et 2.
            if (sniffer === matched || sniffer === named) continue
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
