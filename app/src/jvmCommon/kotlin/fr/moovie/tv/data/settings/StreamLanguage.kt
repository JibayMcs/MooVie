package fr.moovie.tv.data.settings

/**
 * Langue de flux préférée, telle que les catalogues l'étiquettent.
 *
 * Chaque entrée désigne un **montage distinct**, jamais l'approximation d'un
 * autre : la VF est doublée, la VO est la piste d'origine sur image nue, le
 * VOSTFR est cette même piste avec des sous-titres français **incrustés**. Rien
 * ne se substitue à rien — voir `nextLinkFor`.
 *
 * L'ordre de déclaration est celui des sections du panneau des sources et des
 * boutons du réglage. Ajouter une langue (VES, VOSTA…) tient donc en une ligne
 * ici : la sélection compare des chaînes et n'en connaît aucune en dur, et le
 * panneau affiche de toute façon les étiquettes qu'il reçoit, connues ou non.
 *
 * Le nom de l'entrée **est** l'étiquette attendue des providers : `VO` ici doit
 * correspondre au `language = "VO"` qu'ils émettent.
 *
 * `LAT` (doublage latino-américain), `CAST` (doublage castillan) et `VOSE`
 * (version originale sous-titrée en espagnol, symétrique de VOSTFR) viennent
 * d'[UnlimplayProvider] : ce sont exactement les étiquettes que rend
 * `UnlimplayProvider.languageOf`, à partir des clés `latino` / `castellano` /
 * `subtitulado` que le catalogue déclare lui-même.
 */
enum class StreamLanguage { VF, VOSTFR, VO, LAT, CAST, VOSE }
