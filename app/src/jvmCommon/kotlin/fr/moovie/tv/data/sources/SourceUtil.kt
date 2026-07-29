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
