package fr.moovie.tv.data.cast

/**
 * Un récepteur Cast trouvé sur le réseau local.
 *
 * @param name le nom **lisible**, celui que l'utilisateur a donné à l'appareil.
 *   Voir [castFriendlyName] : ce n'est pas le nom du service mDNS.
 * @param model ce que l'appareil dit être — « Chromecast », « Google Nest Hub »,
 *   ou le nom d'un téléviseur. Purement informatif, mais c'est ce qui permet de
 *   distinguer deux appareils portant le même nom dans deux pièces.
 */
data class CastDevice(
    val name: String,
    val host: String,
    val port: Int = CastTls.PORT,
    val model: String = "",
)

/** Le service mDNS des récepteurs Cast. Le point final n'est pas décoratif. */
const val CAST_SERVICE_TYPE = "_googlecast._tcp."

/**
 * Le nom à montrer, tiré des enregistrements TXT de l'annonce.
 *
 * ## Le piège
 *
 * Le nom du **service** mDNS est un identifiant : mesuré sur un appareil réel,
 * `Chromecast-a5b1f58f89bccb0138437cb1de4c1f71`. L'afficher donnerait une liste
 * de chaînes hexadécimales où l'utilisateur devrait deviner laquelle est son
 * salon. Le nom qu'il a choisi vit dans le TXT `fn=` — relevé : `fn=Salon`.
 *
 * ## Le repli
 *
 * Un appareil qui n'annonce pas `fn` est soit très ancien, soit en train de
 * démarrer. Plutôt que de l'écarter — il est joignable, et c'est ce qui compte —
 * on retombe sur son modèle (`md=`), puis sur son adresse. Un nom laid vaut
 * mieux qu'un appareil absent de la liste.
 *
 * @param attributs les enregistrements TXT, décodés en texte.
 * @param repli ce qu'on affiche si l'annonce ne dit rien d'utilisable.
 */
fun castFriendlyName(attributs: Map<String, String?>, repli: String): String =
    attributs["fn"]?.takeIf { it.isNotBlank() }
        ?: attributs["md"]?.takeIf { it.isNotBlank() }
        ?: repli

/**
 * Cherche les récepteurs Cast du réseau local.
 *
 * ## Pourquoi mDNS et pas un balayage de ports
 *
 * Mesuré : un balayage TCP du `/24` sur le port 8009 **n'a pas vu** un
 * Chromecast que mDNS trouvait en une seconde. Un récepteur ne répond pas
 * forcément à une connexion nue venue de nulle part, et de toute façon un
 * balayage complet prend des dizaines de secondes, réveille la radio et
 * ressemble à une reconnaissance réseau. La découverte passe par l'annonce.
 *
 * ## Bornée dans le temps
 *
 * Comme [fr.moovie.tv.data.remote.RemoteBeacons.discover], et pour la même
 * raison : on cherche au moment où l'on ouvre la liste, pas en permanence.
 */
expect object CastDiscovery {
    suspend fun discover(timeoutMs: Long = 4_000): List<CastDevice>
}
