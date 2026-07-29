package fr.moovie.tv.data.sources

/** Déduit un nom d'hébergeur lisible depuis le domaine (ex: vidzy.org → vidzy). */
fun hosterOf(url: String): String {
    val host = Regex("""^https?://([^/]+)""").find(url)?.groupValues?.get(1) ?: return "?"
    return host.removePrefix("www.").substringBefore('.').ifBlank { "?" }
}
