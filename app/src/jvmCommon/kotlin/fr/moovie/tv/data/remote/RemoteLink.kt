package fr.moovie.tv.data.remote

import java.net.URLDecoder

/**
 * Lit un lien d'appairage `moovie://remote?h=…&p=…&t=…&n=…`.
 *
 * ## Pourquoi ça ne vit plus dans `MainActivity`
 *
 * Le téléphone reçoit ce lien en scannant le QR du téléviseur : Android le lui
 * livre tout analysé, dans un `Intent`. **Un ordinateur n'a pas de caméra**, donc
 * pas d'intent — il n'a que ce que [fr.moovie.tv.ui.pairing.PairingDialog] écrit
 * en toutes lettres sous le QR, et que quelqu'un recopie ou colle.
 *
 * Les deux bouts doivent comprendre exactement la même chose. Recopier l'analyse
 * dans le desktop l'aurait fait diverger au premier paramètre ajouté, et le
 * symptôme aurait été un appairage qui marche d'un côté seulement.
 *
 * ## Tolérante, parce qu'un humain tape
 *
 * Le chemin Android reçoit une URI propre ; celui du desktop reçoit ce qu'on a
 * collé. On accepte donc les espaces autour, une casse quelconque sur le schéma,
 * et un lien recopié sans son `moovie://`. On ne devine rien de plus : un lien
 * amputé de son jeton n'est pas rattrapable, et le laisser passer donnerait une
 * cible qui répond 404 sans dire pourquoi.
 *
 * Rend null si le lien n'est pas un appairage exploitable.
 */
fun parseRemoteLink(raw: String?): RemoteTarget? {
    val texte = raw?.trim().orEmpty()
    if (texte.isEmpty()) return null

    // Le schéma est facultatif : quelqu'un qui recopie à la main s'arrête
    // souvent au premier caractère utile.
    val sansSchema = texte.removePrefix("moovie://").removePrefix("MOOVIE://")
        .let { if (it.startsWith("remote", ignoreCase = true)) it else return null }

    val requete = sansSchema.substringAfter('?', "")
    if (requete.isEmpty()) return null

    val champs = requete.split('&')
        .mapNotNull { morceau ->
            val cle = morceau.substringBefore('=', "")
            val valeur = morceau.substringAfter('=', "")
            if (cle.isEmpty()) null else cle to decode(valeur)
        }
        .toMap()

    val host = champs["h"]?.takeIf { it.isNotBlank() } ?: return null
    val port = champs["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    val token = champs["t"]?.takeIf { it.isNotBlank() } ?: return null
    // Le nom n'est qu'un libellé : son absence ne doit pas faire échouer un
    // appairage par ailleurs complet. L'adresse fait un repli lisible.
    val name = champs["n"]?.takeIf { it.isNotBlank() } ?: host

    return RemoteTarget(name = name, host = host, port = port, token = token)
}

/**
 * `URLDecoder.decode(String, String)` et non la surcharge à `Charset` : cette
 * dernière date de Java 10, et minSdk est 23. Le genre d'API qui compile, passe
 * les tests sur le poste, et tombe sur la box.
 */
private fun decode(value: String): String =
    runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
