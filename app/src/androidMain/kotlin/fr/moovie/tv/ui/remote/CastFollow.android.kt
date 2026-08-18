package fr.moovie.tv.ui.remote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Suit la box en arrière-plan, et demande de quoi le montrer.
 *
 * Android 13 masque toute notification non autorisée **sans rien dire à
 * l'application** : sans cette demande, le service tournerait parfaitement et
 * personne ne verrait jamais les commandes de la diffusion. La recopie de la
 * progression, elle, n'en dépend pas — c'est pourquoi le service démarre dans
 * tous les cas, autorisation ou non.
 */
@Composable
actual fun rememberCastFollow(): () -> Unit {
    val context = LocalContext.current
    val askNotify = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    return remember(context) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            CastSessionService.start(context)
        }
    }
}
