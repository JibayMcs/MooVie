package fr.moovie.tv.shared

import android.os.Build

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import fr.moovie.tv.BuildConfig
import fr.moovie.tv.data.store.appContext

/**
 * « Android TV » ou « Android » selon l'appareil — **résolu à l'exécution**.
 *
 * C'était figé sur « Android TV » : une sauvegarde faite sur un téléphone
 * s'annonçait donc comme venant d'une box, et l'aperçu d'import mentait sur son
 * origine au moment précis où l'on migre entre les deux.
 *
 * Une seule et même APK sert les deux, aucun `expect`/`actual` ne peut trancher
 * — mêmes deux signaux que MainActivity : le mode d'interface dit ce que
 * l'appareil *fait*, la fonctionnalité leanback ce qu'il *est*, et une box qui
 * répond mal à l'un répond en général correctement à l'autre.
 *
 * `by lazy` : le contexte d'application n'existe pas encore au chargement de la
 * classe. Et `runCatching` parce que ceci ne sert qu'à étiqueter une sauvegarde
 * — aucune raison de faire tomber l'app si un service manque.
 */
actual val platformName: String by lazy {
    val onTv = runCatching {
        (appContext.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
            ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }.getOrDefault(false)
    if (onTv) "Android TV" else "Android"
}

actual val appVersionName: String = BuildConfig.VERSION_NAME

actual val openSubtitlesApiKey: String = BuildConfig.OPENSUBTITLES_API_KEY

actual val deviceName: String
    get() = listOfNotNull(
        Build.MANUFACTURER?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() },
        Build.MODEL?.takeIf { it.isNotBlank() },
    ).distinct().joinToString(" ").ifBlank { "Android TV" }
