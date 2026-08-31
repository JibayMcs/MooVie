package fr.moovie.tv.data.trailer

/**
 * Encode un chemin absolu pour la partie chemin d'une URI `file://`, comme le
 * faisait `java.io.File.toURI().rawPath`.
 *
 * ## Pourquoi pas `encodeURLPath` de Ktor
 *
 * Parce qu'il n'encode pas la même chose. Ktor percent-encode **tout** ce qui
 * n'est pas ASCII : `/home/renée/…` devient `/home/ren%C3%A9e/…`. Java, lui,
 * laisse les caractères non-ASCII intacts et ne cite que les ASCII interdits
 * dans une URI — l'espace au premier chef.
 *
 * La nuance n'est pas théorique : c'est exactement le cas que le commentaire
 * d'origine de `DashManifestStore` signalait comme le seul à survivre, celui du
 * nom d'utilisateur accentué. Un encodage plus zélé produit une URI que le
 * lecteur ouvre autrement — et seulement chez les gens concernés, jamais sur la
 * machine de développement.
 *
 * `UriFichierTest` compare cette fonction à `File.toURI().rawPath` sur les
 * chemins qui posent problème.
 */
internal fun enCheminUri(chemin: String): String = buildString(chemin.length) {
    // Parcours par **caractère** et non par octet : parcourir les octets UTF-8
    // et les rendre un à un comme des Char reconstruit chaque séquence
    // multi-octets en autant de caractères Latin-1, et « é » ressort en « Ã© ».
    // Les caractères non-ASCII étant recopiés tels quels, il n'y a de toute
    // façon rien à encoder pour eux ; et un ASCII tient toujours sur un octet,
    // donc son encodage se lit directement de son code.
    for (c in chemin) {
        when {
            // Non-ASCII : laissé intact, comme Java.
            c.code > 0x7F -> append(c)
            c in AUTORISES -> append(c)
            else -> {
                append('%')
                append(HEX[c.code shr 4])
                append(HEX[c.code and 0x0F])
            }
        }
    }
}

/**
 * Le jeu que `java.net.URI` accepte sans citation dans un chemin : les
 * caractères non réservés, les sous-délimiteurs, `:` et `@`, plus le séparateur.
 * Tout le reste — espace, `%`, `#`, `?`, crochets, accolades, guillemets — est
 * cité.
 */
private const val AUTORISES =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" +
        "-._~!$&'()*+,;=:@/"

private const val HEX = "0123456789ABCDEF"
