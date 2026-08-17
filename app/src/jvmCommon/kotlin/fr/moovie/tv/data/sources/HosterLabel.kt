package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink

/**
 * Nom d'hébergeur **présentable**, pour l'écran.
 *
 * ## Le problème
 *
 * `EmbedLink.hoster` se déduit du domaine, et c'est la bonne règle : le libellé
 * affiché par les catalogues ment (« DdStream » est servi par playmogo,
 * « netu » par waaw). Mais un domaine n'est pas un nom : l'utilisateur lisait
 * « jefferycontrolmodel », « morencius », « bysebuho » — des adresses jetables
 * que VOE et consorts renouvellent tous les mois. C'était le dernier endroit où
 * l'application montrait sa plomberie.
 *
 * ## Deux étages, et aucune liste d'alias à tenir
 *
 * 1. **Qui revendique ce lien ?** Les extracteurs connaissent déjà les alias de
 *    leur famille — celle de VOE vit dans `VoeExtractor.canHandle`, et elle est
 *    tenue à jour parce que la *lecture* en dépend. Interroger le registre fait
 *    donc du libellé un sous-produit d'une liste qui a déjà une raison d'exister,
 *    au lieu d'une seconde à maintenir en parallèle.
 * 2. **Sinon, le domaine, présenté proprement.** Un hébergeur inédit s'affiche
 *    « Bysebuho » plutôt que « bysebuho » : ce n'est pas un vrai nom, mais ça
 *    ressemble à quelque chose, et surtout ça ne demande aucune mise à jour.
 *
 * Les extracteurs qui reconnaissent une **forme** plutôt qu'un hôte sont exclus
 * du premier étage : `DirectStreamExtractor` revendique tout ce qui finit en
 * `.mp4`, et il rebaptiserait « Direct » la moitié du panneau. Le lien porte
 * déjà, dans ce cas, un `hoster` que son catalogue a renseigné.
 */
fun hosterLabel(link: EmbedLink): String {
    val claimed = ExtractorRegistry.extractorFor(link.url)
        ?.hoster
        ?.takeIf { it !in SHAPE_BASED }
    return hosterLabel(claimed ?: link.hoster)
}

/** La même chose depuis un identifiant seul, quand l'URL n'est pas sous la main. */
fun hosterLabel(hoster: String): String =
    KNOWN[hoster.lowercase()] ?: hoster.replaceFirstChar { it.uppercase() }

/**
 * Extracteurs qui se reconnaissent à la forme de l'URL, pas à son hôte. Leur nom
 * décrit un mécanisme, jamais un hébergeur.
 */
private val SHAPE_BASED = setOf("direct", "packed")

/**
 * Casse d'origine des hébergeurs rencontrés.
 *
 * La table ne sert qu'à **l'orthographe** : sans elle « doodstream » s'afficherait
 * « Doodstream », ce qui est lisible mais faux. Rien ne dépend de sa complétude —
 * un absent passe par le repli et reste parfaitement utilisable.
 */
private val KNOWN = mapOf(
    "voe" to "Voe",
    "dood" to "DoodStream",
    "doodstream" to "DoodStream",
    "uqload" to "Uqload",
    "vidzy" to "Vidzy",
    "fsvid" to "FsVid",
    "sibnet" to "Sibnet",
    "lulustream" to "LuluStream",
    "luluvdo" to "LuluVdo",
    "luluvid" to "LuluVdo",
    "seekstreaming" to "SeekStreaming",
    "ansembed" to "AnsEmbed",
    "swiftflow" to "SwiftFlow",
    "playmogo" to "PlayMogo",
    "minochinos" to "Minochinos",
    "savefiles" to "SaveFiles",
    "oneupload" to "OneUpload",
    "supervideo" to "SuperVideo",
    "filemoon" to "FileMoon",
    "vidmoly" to "Vidmoly",
    "streamtape" to "StreamTape",
    "darkibox" to "Darkibox",
    "upstream" to "Upstream",
    "evoload" to "Evoload",
    "waaw" to "Waaw",
    "netu" to "Netu",
    "vidapi" to "Vidapi",
    "frembed" to "Frembed",
)
