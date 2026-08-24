package fr.moovie.tv.core.sources.usecase

import fr.moovie.tv.core.sources.model.EmbedLink

/**
 * Les hébergeurs qu'on ne sait pas lire, et qu'on cesse donc d'afficher.
 *
 * ## Le défaut que ça corrige
 *
 * Un panneau de sources annonçait « 20 sources · 5 catalogues » dont dix-neuf
 * marquées « Ne répond pas ». L'application avait l'air cassée — et elle l'était,
 * pour dix-neuf lignes sur vingt. Or **la plupart n'ont jamais fonctionné** : ce
 * ne sont pas des régressions mais des hébergeurs pour lesquels aucun extracteur
 * n'a jamais existé, apparus au fil des catalogues ajoutés.
 *
 * Lister une source qu'on ne sait pas lire ne rend service à personne : elle ne
 * se distingue pas d'une source cassée, elle noie les bonnes, et elle fait
 * douter de celles qui marchent.
 *
 * ## Comment cette liste a été établie, et comment la refaire
 *
 * Elle est **mesurée**, pas devinée. `HosterHealthProbeTest` résout un
 * échantillon de liens par hébergeur sur un panier de titres et compte ceux qui
 * sont réellement jouables :
 *
 * ```
 * ./gradlew :app:desktopTest --tests '*HosterHealthProbeTest' -Dmoovie.probe=1
 * ```
 *
 * Relevé du **24/08/2026** : 14 hébergeurs vivants sur 32. Les noms ci-dessous
 * sont ceux dont aucun lien n'a abouti.
 *
 * ## Ce que cette liste coûte
 *
 * Elle vieillit. Un hébergeur qui redevient lisible — parce qu'on lui écrit un
 * extracteur, ou parce qu'il cesse de filtrer — restera masqué tant que personne
 * ne l'aura retiré d'ici. C'est le prix assumé d'une liste explicite plutôt que
 * d'une détection à l'exécution : cette dernière suppose de sonder pour savoir,
 * donc d'afficher d'abord et de faire disparaître ensuite, ce qui est pire à
 * l'usage. **Relancer la sonde après chaque ajout d'extracteur.**
 */
val UNSUPPORTED_HOSTERS: Set<String> = setOf(
    // Aucun extracteur, aucun renifleur ne les ramasse : rien n'est résolu.
    "bryantenunder",
    "flemmix",
    "hgcloud",
    "jefferycontrolmodel",
    "jessicayeahcatch",
    "netu",
    "oneupload",
    "savefiles",
    "sendvid",
    "serix",
    "ssblongvu",
    "vidara",
    "vido",
    "vidsonic",
    "xshotcok",
    // Résolus, puis refusés par l'hôte. Le décodage marche — vérifié sur vidzy,
    // dont l'URL signée est reconstruite correctement — mais le CDN répond 403
    // à tout ce qui ne vient pas de chez lui, sans en-tête ni cookie qui y
    // change quoi que ce soit. Même classe que wiflix : filtré côté serveur.
    "vidzy",
    "waaw",
    // Étiquette générique de certains catalogues, jamais jouable telle quelle.
    "video",
)

/**
 * Vrai si cet hébergeur vaut d'être proposé.
 *
 * Insensible à la casse : les catalogues n'ont aucune convention commune, et le
 * même hébergeur s'y écrit `Netu`, `netu` ou `NETU`.
 */
fun isHosterSupported(hoster: String): Boolean =
    hoster.trim().lowercase() !in UNSUPPORTED_HOSTERS

/**
 * Ne garde que les liens qu'on a une chance de lire.
 *
 * Appliqué **à l'agrégation**, donc avant le cache : un lien écarté ici ne coûte
 * ni sonde de jouabilité, ni ligne à l'écran, ni requête vers un hôte qui va de
 * toute façon refuser.
 *
 * Un hébergeur vide n'est pas écarté : il n'affirme rien, et le reniflage par
 * structure de page reste sa chance.
 */
fun keepSupportedHosters(links: List<EmbedLink>): List<EmbedLink> =
    links.filter { it.hoster.isBlank() || isHosterSupported(it.hoster) }
