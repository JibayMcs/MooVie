package fr.moovie.tv.data.remote

/**
 * Aucune annonce, aucune découverte.
 *
 * Le commentaire de l'`expect` dit que le desktop ne fait rien parce qu'« un
 * poste de travail n'est ni la TV ni la télécommande ». Un iPhone, lui,
 * **serait** une télécommande parfaitement légitime — c'est même l'appareil
 * pour lequel la fonction a été pensée. Ce vide-là n'est donc pas un choix de
 * conception mais une dette, et elle mérite d'être nommée.
 *
 * Ce qu'il faudrait : `NSNetServiceBrowser` ou `NWBrowser` côté découverte,
 * `NSNetService` côté annonce, la déclaration `NSBonjourServices` avec le type
 * `_moovie._tcp` dans l'Info.plist, et surtout `NSLocalNetworkUsageDescription`
 * — depuis iOS 14, toute recherche sur le réseau local demande le consentement
 * explicite de l'utilisateur, refusable et non redemandable.
 *
 * Rendre une liste vide plutôt que lever : l'écran de télécommande affiche
 * alors « aucun appareil trouvé », ce qui est exact.
 */
actual object RemoteBeacons {
    actual fun advertise(name: String, port: Int) = Unit

    actual fun stopAdvertising() = Unit

    actual suspend fun discover(timeoutMs: Long): List<RemoteBeacon> = emptyList()
}
