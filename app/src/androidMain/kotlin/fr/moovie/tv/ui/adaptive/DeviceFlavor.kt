package fr.moovie.tv.ui.adaptive

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * TV ou téléphone ? La question se pose **à l'exécution** : les deux
 * tournent sur le même APK, donc aucun `expect`/`actual` ni aucune variante
 * de build ne peut trancher.
 *
 * Deux signaux plutôt qu'un, parce qu'ils ratent chacun des cas : le mode
 * d'interface décrit ce que l'appareil est *en train* de faire (une TV
 * branchée en dock, une tablette sur un écran externe), alors que la
 * fonctionnalité `leanback` décrit ce qu'il *est*. Une box qui répond mal à
 * l'un répond en général correctement à l'autre.
 *
 * Une seule détection pour toute l'application : `MainActivity` s'en sert pour
 * la composition, et la sortie de l'application ([fr.moovie.tv.exit.AppExit])
 * pour savoir si elle doit fermer. Deux copies auraient fini par diverger, et
 * l'appli se serait fermée sur un appareil qu'elle affichait en téléphone.
 */
fun Context.deviceUiFlavor(): UiFlavor {
    val onTv = (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
        ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    return if (onTv) UiFlavor.TV else UiFlavor.TOUCH
}
