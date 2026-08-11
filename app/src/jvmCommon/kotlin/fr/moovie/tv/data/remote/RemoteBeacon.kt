package fr.moovie.tv.data.remote

/**
 * Ce qu'une annonce réseau raconte : où joindre un téléviseur, et sous quel nom.
 *
 * Jamais le jeton. Il vient de l'appairage par QR, qui suppose d'avoir vu
 * l'écran — diffuser le jeton sur le réseau reviendrait à donner la
 * télécommande à tout ce qui est connecté au même Wi-Fi.
 */
data class RemoteBeacon(val name: String, val host: String, val port: Int)

/**
 * Annonce du téléviseur sur le réseau local, et recherche depuis le téléphone.
 *
 * ### Pourquoi ça existe alors qu'on appaire déjà par QR
 *
 * Le QR donne les trois pièces d'un coup, mais deux d'entre elles périment :
 * l'adresse change au renouvellement du bail DHCP, et le port est éphémère —
 * il change à **chaque démarrage** de l'application sur la TV. Sans découverte,
 * la télécommande mémorisée cesserait de fonctionner dès le lendemain et il
 * faudrait rescanner. Avec elle, on appaire une fois.
 *
 * Android seulement : `NsdManager` n'a pas d'équivalent JVM sans embarquer une
 * pile mDNS entière, et le téléviseur comme le téléphone sont des appareils
 * Android. Les implémentations desktop ne font rien, ce qui est le comportement
 * juste : un poste de travail n'est ni la TV ni la télécommande.
 */
expect object RemoteBeacons {

    /** Le téléviseur se déclare joignable sur [port]. Sans effet ailleurs. */
    fun advertise(name: String, port: Int)

    /** Fin de l'annonce. Appelé quand l'application quitte le premier plan. */
    fun stopAdvertising()

    /**
     * Cherche pendant [timeoutMs] et rend ce qui a répondu.
     *
     * Bornée dans le temps plutôt qu'en flux continu : on s'en sert pour
     * rattraper une adresse au moment d'ouvrir la télécommande, pas pour tenir
     * une liste vivante. Un balayage mDNS qui tourne en fond coûte de la radio
     * pour rien.
     */
    suspend fun discover(timeoutMs: Long = 3_000): List<RemoteBeacon>
}

/** Nom du service mDNS. Partagé par l'annonce et la recherche. */
const val REMOTE_SERVICE_TYPE = "_moovie._tcp."
