package fr.moovie.tv.data.sources

/** Déduit un nom d'hébergeur lisible depuis le domaine (ex: vidzy.org → vidzy). */
fun hosterOf(url: String): String {
    val host = Regex("""^https?://([^/]+)""").find(url)?.groupValues?.get(1) ?: return "?"
    return host.removePrefix("www.").substringBefore('.').ifBlank { "?" }
}

/**
 * Origine (scheme://host) d'une URL, pour poser des en-têtes Referer/Origin
 * cohérents avec le domaine réel du lien plutôt qu'un domaine figé (les hôtes
 * changent souvent de TLD). Retourne [fallback] si l'URL est inexploitable.
 */
fun originOf(url: String, fallback: String): String {
    val host = Regex("""^(https?)://([^/]+)""").find(url) ?: return fallback
    return "${host.groupValues[1]}://${host.groupValues[2]}"
}

/**
 * Résout une référence relative contre une URL absolue, comme le faisait
 * `java.net.URI.resolve` avant que ce code ne devienne multiplateforme.
 *
 * Volontairement partiel : il couvre le seul cas dont les extracteurs ont
 * besoin — une base `http(s)://hôte/chemin` et une référence en `/`, `./` ou
 * `../`. Une référence déjà absolue est rendue telle quelle. Tout le reste rend
 * null, plutôt que de deviner.
 *
 * Réimplémenter plutôt qu'ajouter une dépendance d'URL : le besoin tient en
 * trente lignes, et `PortabiliteUrlTest` vérifie l'équivalence avec `URI` sur
 * les cas réels.
 */
fun resoudreRelatif(base: String, reference: String): String? {
    if (reference.startsWith("http://") || reference.startsWith("https://")) return reference

    val apresSchema = base.indexOf("://")
    if (apresSchema < 0) return null
    val debutChemin = base.indexOf('/', apresSchema + 3)
    val racine = if (debutChemin < 0) base else base.substring(0, debutChemin)
    val cheminBase = if (debutChemin < 0) "" else base.substring(debutChemin)
        .substringBefore('?')
        .substringBefore('#')

    // Requête et fragment de la référence ne participent pas à la
    // normalisation des segments : on les met de côté et on les rend tels quels.
    val coupure = reference.indexOfFirst { it == '?' || it == '#' }
    val cheminRef = if (coupure < 0) reference else reference.substring(0, coupure)
    val suffixe = if (coupure < 0) "" else reference.substring(coupure)

    // Une référence absolue en chemin repart de la racine ; une référence
    // relative repart du *répertoire* de la base, d'où le retrait du dernier
    // segment.
    val segments = if (cheminRef.startsWith("/")) {
        mutableListOf()
    } else {
        cheminBase.substringBeforeLast('/', "")
            .split('/')
            .filterTo(mutableListOf()) { it.isNotEmpty() }
    }

    for (segment in cheminRef.split('/')) {
        when (segment) {
            "", "." -> Unit
            // Un `..` de trop est **conservé**, pas absorbé. La RFC 3986 dirait
            // de le jeter, mais `java.net.URI.resolve` le garde, et c'est ce
            // comportement-là qu'Android et desktop ont toujours eu : ce code
            // les sert désormais aussi. Aligner sur la RFC serait changer le
            // comportement de deux plateformes en production pour un gain nul,
            // les serveurs normalisant de toute façon le chemin reçu.
            ".." -> if (segments.isNotEmpty() && segments.last() != "..") {
                segments.removeAt(segments.lastIndex)
            } else {
                segments.add("..")
            }
            else -> segments.add(segment)
        }
    }

    // `URI.resolve` conserve la barre finale d'un répertoire ; la reconstruction
    // par segments la perdrait.
    val barreFinale = if (cheminRef.endsWith("/") && segments.isNotEmpty()) "/" else ""
    return racine + "/" + segments.joinToString("/") + barreFinale + suffixe
}
