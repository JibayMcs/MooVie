package fr.moovie.tv.core.sources.usecase

import fr.moovie.tv.core.sources.model.EmbedLink

/**
 * Prochain lien à tenter pour la langue demandée.
 *
 * **Aucune substitution entre langues.** Chaque étiquette désigne un montage
 * distinct, pas une approximation d'un autre :
 *
 * - `VF` est doublée ;
 * - `VO` est la piste d'origine, image nue ;
 * - `VOSTFR` est cette même piste **avec des sous-titres français incrustés** —
 *   ces catalogues les gravent dans l'image, on ne les retire pas.
 *
 * Jouer un VOSTFR à qui a demandé la VO lui impose donc des sous-titres dont il
 * ne peut pas se débarrasser. Le rapprochement paraît séduisant côté audio, il
 * ne tient pas à l'écran. Une langue qu'on n'a pas est une langue qu'on n'a
 * pas : la cascade s'arrête, le panneau montre le reste, et c'est à
 * l'utilisateur de choisir.
 *
 * Corollaire assumé : élargir la couverture d'une langue est un travail de
 * **sources**, jamais d'assouplissement du filtre. Voir VidapiProvider.
 *
 * Cette fonction ne connaît aucune langue en particulier — elle compare des
 * chaînes. En ajouter une (VES, VOSTA…) ne la touche pas.
 */
fun nextLinkFor(
    links: List<EmbedLink>,
    preferred: String,
    excluded: Set<String> = emptySet(),
): EmbedLink? = links.firstOrNull { it.language == preferred && it.url !in excluded }
