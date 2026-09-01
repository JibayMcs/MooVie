package fr.moovie.tv.data.net

/**
 * Résolveurs DNS-over-HTTPS proposés dans les réglages.
 *
 * Séparé d'`AppDns` : l'énumération est un **réglage**, que les écrans lisent et
 * que le dépôt de préférences persiste, alors que le résolveur qui l'applique
 * est une implémentation OkHttp. Les garder ensemble clouait le réglage à la
 * JVM, et donc tout `SettingsRepository` avec lui.
 *
 * Les adresses d'amorçage restent des littéraux IP : c'est ce qui permet de
 * joindre le résolveur sans avoir à résoudre son nom, donc sans dépendre du DNS
 * du FAI — celui-là même qu'on contourne.
 *
 * iOS n'a pas d'implémentation : NSURLSession n'expose aucun point d'entrée de
 * résolution. Le réglage y reste lisible et persistant, simplement sans effet —
 * voir la documentation de `KtorGateway`.
 */
enum class DohProvider(
    val label: String,
    val url: String,
    val bootstrap: List<String>,
) {
    CLOUDFLARE("Cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    QUAD9("Quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
}
