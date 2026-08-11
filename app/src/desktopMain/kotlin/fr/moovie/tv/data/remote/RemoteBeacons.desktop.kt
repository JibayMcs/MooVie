package fr.moovie.tv.data.remote

/**
 * Sans effet sur desktop, et c'est le comportement juste : un poste de travail
 * n'est ni le téléviseur qu'on pilote, ni la télécommande qu'on tient. Une pile
 * mDNS embarquée pour un cas qui n'existe pas serait une dépendance de plus
 * dans un projet qui en compte peu.
 */
actual object RemoteBeacons {
    actual fun advertise(name: String, port: Int) = Unit
    actual fun stopAdvertising() = Unit
    actual suspend fun discover(timeoutMs: Long): List<RemoteBeacon> = emptyList()
}
