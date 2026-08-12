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
/**
 * @param heights hauteur d'image **déjà mesurée**, par URL de lien. Voir le
 *   classement ci-dessous ; une carte vide rend exactement le comportement
 *   d'avant, ce qui est le cas d'une fiche à peine ouverte.
 */
fun nextLinkFor(
    links: List<EmbedLink>,
    preferred: String,
    excluded: Set<String> = emptySet(),
    heights: Map<String, Int> = emptyMap(),
): EmbedLink? = orderedLinksFor(links, preferred, excluded, heights).firstOrNull()

/**
 * Les liens jouables pour cette langue, du plus souhaitable au moins.
 *
 * La cascade descend cette liste : le premier qui échoue laisse la place au
 * suivant, jusqu'à la plus modeste des sources. C'est ce qui donne « la
 * meilleure qualité si elle marche, une qualité moindre sinon » sans jamais
 * renoncer à lire.
 *
 * ## L'ordre, et pourquoi celui-là
 *
 * On trie par définition décroissante, et **un lien non mesuré compte pour
 * [UNKNOWN_HEIGHT]** — ni le meilleur, ni le pire.
 *
 * Les deux réglages extrêmes sont faux, et chacun d'une façon différente :
 *
 * - traiter l'inconnu comme **mauvais** (tout à la fin) ferait dégringoler le
 *   meilleur catalogue pour la seule raison qu'on ne l'a pas encore interrogé.
 *   Mesurer coûte une résolution complète, une à trois secondes par lien, et la
 *   fiche vient parfois de s'ouvrir : à cet instant *tout* est inconnu ;
 * - traiter l'inconnu comme **excellent** (tout au début) rendrait la mesure
 *   inutile, puisqu'aucun lien mesuré ne remonterait jamais devant un lien qui
 *   ne l'est pas.
 *
 * Au milieu, la mesure fait exactement son office : elle **promeut** ce qui
 * s'avère meilleur que la moyenne, **relègue** ce qui s'avère pire, et laisse
 * l'ordre des providers décider du reste. À définition égale — deux liens
 * mesurés à la même hauteur, ou deux inconnus — c'est cet ordre d'origine qui
 * départage, le tri étant stable.
 *
 * Corollaire visible : plus on reste sur une fiche, meilleur est le choix, les
 * mesures arrivant en fond. C'est voulu, et sans effet de bord — le classement
 * ne change pas une lecture déjà lancée.
 */
fun orderedLinksFor(
    links: List<EmbedLink>,
    preferred: String,
    excluded: Set<String> = emptySet(),
    heights: Map<String, Int> = emptyMap(),
): List<EmbedLink> = links
    .filter { it.language == preferred && it.url !in excluded }
    .sortedByDescending { heights[it.url] ?: UNKNOWN_HEIGHT }

/**
 * Ce que vaut un lien dont on ne sait rien encore.
 *
 * 720p : ce que rend une source correcte sans être remarquable. Le chiffre n'a
 * pas à être juste, seulement à séparer « nettement mieux que l'ordinaire » de
 * « nettement moins bien » — c'est un pivot, pas une estimation.
 */
const val UNKNOWN_HEIGHT = 720
