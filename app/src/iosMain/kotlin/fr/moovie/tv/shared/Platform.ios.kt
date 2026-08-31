package fr.moovie.tv.shared

import platform.UIKit.UIDevice

actual val platformName: String =
    UIDevice.currentDevice.let { "${it.systemName} ${it.systemVersion}" }

/**
 * Vient de la source générée par Gradle plutôt que de `CFBundleShortVersionString` :
 * l'`appVersion` du build reste l'unique origine du numéro sur les quatre
 * plateformes, et l'updater compare cette valeur aux tags GitHub.
 */
actual val appVersionName: String = VERSION_GENEREE

actual val openSubtitlesApiKey: String = CLE_OPENSUBTITLES_GENEREE

/**
 * Depuis iOS 16, `UIDevice.name` ne rend plus le nom donné par l'utilisateur
 * mais le modèle (« iPhone ») sauf entitlement dédié, que le sideload n'a pas.
 * C'est sans importance ici : le contrat de [deviceName] est de nommer
 * l'appareil auprès des autres, pas d'être unique.
 */
actual val deviceName: String
    get() = UIDevice.currentDevice.name.takeIf { it.isNotBlank() } ?: "Moo-vie"
