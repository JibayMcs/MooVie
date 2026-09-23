package fr.moovie.tv.data.sources

import kotlin.concurrent.Volatile

/**
 * Le domaine SwiftFlow **réellement joignable**, partagé entre le catalogue et
 * l'extracteur.
 *
 * ## Pourquoi les deux en ont besoin
 *
 * [SwiftFlowProvider] découvre le domaine en essayant ses candidats jusqu'à ce
 * que l'un rende du JSON. [SwiftFlowExtractor], lui, n'a qu'une URL de fichier à
 * poser sur le lecteur — mais il doit l'accompagner du `Referer` du **site**,
 * faute de quoi Cloudflare rend 403 sur le CDN.
 *
 * Tant que le domaine était une constante, les deux la lisaient chacun de leur
 * côté et restaient d'accord par construction. Dès lors qu'il se découvre à
 * l'exécution, l'extracteur ne peut plus le deviner : il lirait l'ancien pendant
 * que le catalogue sert le nouveau, et chaque fichier reviendrait en 403 — une
 * panne d'autant plus déroutante que la liste des sources, elle, s'afficherait
 * parfaitement.
 *
 * ## Un état global assumé
 *
 * Le projet n'a pas d'injection de dépendances (voir `ActiveProfile`), et faire
 * traverser ce domaine du catalogue jusqu'à l'extracteur imposerait de le porter
 * dans `EmbedLink`, c'est-à-dire dans le modèle du domaine, pour un détail de
 * transport propre à une source. La valeur par défaut est celle du dernier
 * domaine connu : une application qui n'a encore rien découvert se comporte
 * exactement comme avant.
 */
internal object SwiftFlowBase {

    @Volatile
    var courant: String = SwiftFlowProvider.API_BASE

    /** Le `Referer` attendu par le CDN : l'origine du site, avec sa barre finale. */
    val referer: String get() = "$courant/"
}
