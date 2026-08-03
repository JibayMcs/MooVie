package fr.moovie.tv.core.sources.usecase

import fr.moovie.tv.core.sources.model.EmbedLink

/**
 * Langues acceptables pour une préférence, de la meilleure à la moins bonne.
 *
 * Le classement suit **la piste audio**, seule chose qu'on ne peut pas ajouter
 * après coup :
 *
 * - `VO` et `VOSTFR` portent le **même audio original** ; ils ne diffèrent que
 *   par des sous-titres français incrustés ou non. Se rabattre de l'un sur
 *   l'autre donne à l'utilisateur exactement la langue qu'il a demandée, et les
 *   sous-titres se règlent par ailleurs (OpenSubtitles).
 * - `VF` est un **doublage** : rien ne s'y substitue. Qui demande du français
 *   parlé n'a que faire d'une VO, et lui en lancer une serait pire que de lui
 *   afficher la liste.
 *
 * Sans ce repli, choisir VO revenait à désactiver la lecture rapide : les
 * catalogues francophones étiquettent massivement en VOSTFR, et deux d'entre
 * eux ne savent produire aucun « VO ».
 */
fun languageCascade(preferred: String): List<String> = when (preferred.uppercase()) {
    "VO" -> listOf("VO", "VOSTFR")
    "VOSTFR" -> listOf("VOSTFR", "VO")
    else -> listOf(preferred)
}

/**
 * Premier lien jouable selon [languageCascade], en épuisant chaque langue avant
 * de passer à la suivante.
 *
 * L'ordre des langues prime sur celui des liens : mieux vaut le dernier
 * hébergeur d'une VO que le premier d'un repli.
 */
fun nextLinkFor(
    links: List<EmbedLink>,
    preferred: String,
    excluded: Set<String> = emptySet(),
): EmbedLink? = languageCascade(preferred).firstNotNullOfOrNull { lang ->
    links.firstOrNull { it.language == lang && it.url !in excluded }
}
