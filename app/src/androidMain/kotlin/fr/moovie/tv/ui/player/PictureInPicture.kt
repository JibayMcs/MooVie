package fr.moovie.tv.ui.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.VideoSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Le lecteur qui continue dans une vignette quand on quitte l'application.
 *
 * ### Ce que le système signale, et ce qu'il ne signale pas
 *
 * Android ne prévient que d'un **départ délibéré** : l'accueil, le geste de
 * retour au lanceur, les applications récentes. C'est `onUserLeaveHint`, et
 * c'est tout ce sur quoi on peut se brancher. Tirer le volet de notifications,
 * recevoir une boîte de dialogue système ou éteindre l'écran ne déclenche rien —
 * volontairement : une vignette qui surgit parce qu'on consulte ses
 * notifications serait une nuisance, pas un service. « Quitter par mégarde » est
 * donc bien couvert ; « perdre le focus » ne l'est pas, et ne doit pas l'être.
 *
 * ### Deux chemins d'entrée selon la version
 *
 * À partir d'Android 12, `setAutoEnterEnabled` laisse le système basculer
 * lui-même, avec une animation continue entre le plein écran et la vignette.
 * En deçà, il faut appeler [entre] depuis `onUserLeaveHint`, et la bascule est
 * visiblement plus abrupte. On ne fait **pas** les deux sur 12+ : la demande
 * manuelle rejouerait une animation que le système a déjà jouée.
 *
 * ### Pourquoi un objet global
 *
 * `onUserLeaveHint` est une affaire d'Activity, l'état de lecture vit dans la
 * composition du lecteur, et le bouton de la vignette revient par un
 * `BroadcastReceiver`. Trois entrées pour un même sujet, dont aucune ne peut
 * tenir les autres par la main : même motif que
 * `fr.moovie.tv.data.remote.remoteTarget` et
 * `fr.moovie.tv.ui.remote.RemoteVolumeKeys`, pour la même raison.
 */
object Pip {

    private val _actif = MutableStateFlow(false)

    /** Vrai tant que la lecture tient dans la vignette. La chrome s'en efface. */
    val actif: StateFlow<Boolean> = _actif.asStateFlow()

    /**
     * Ce que le lecteur expose au système, ou null si aucun lecteur n'est là.
     *
     * Des lambdas plutôt que des valeurs : les paramètres de la vignette sont
     * reconstruits à chaque changement d'état, et une copie prise à
     * l'inscription serait déjà périmée — la taille de l'image, notamment,
     * n'est pas connue avant la première trame.
     *
     * Les deux libellés, eux, sont bien des valeurs : ils viennent de
     * `composeResources`, qui ne se lit que depuis une composition, alors que la
     * vignette se construit depuis l'Activity. Les résoudre à l'inscription est
     * le seul moyen de ne pas les dupliquer dans `androidMain/res`.
     */
    @Volatile
    private var source: Source? = null

    class Source(
        val taille: () -> VideoSize,
        val enLecture: () -> Boolean,
        val bascule: () -> Unit,
        val libellePause: String,
        val libelleLecture: String,
    )

    /**
     * À poser par le lecteur, tant qu'il est à l'écran.
     *
     * [actif] à false ferme la porte sans condition : c'est ce qui exclut le
     * téléviseur, où une vignette n'a aucun sens — on n'y quitte pas
     * l'application en pleine lecture, et le PiP du Leanback répond à un besoin
     * différent.
     */
    @Composable
    fun Register(
        actif: Boolean,
        taille: () -> VideoSize,
        enLecture: () -> Boolean,
        bascule: () -> Unit,
        libellePause: String,
        libelleLecture: String,
    ) {
        val context = LocalContext.current
        val tailleCourante by rememberUpdatedState(taille)
        val lectureCourante by rememberUpdatedState(enLecture)
        val basculeCourante by rememberUpdatedState(bascule)
        DisposableEffect(actif, context, libellePause, libelleLecture) {
            if (!actif || !disponible(context)) return@DisposableEffect onDispose {}
            source = Source(
                taille = { tailleCourante() },
                enLecture = { lectureCourante() },
                bascule = { basculeCourante() },
                libellePause = libellePause,
                libelleLecture = libelleLecture,
            )
            // Le bouton de la vignette passe par une diffusion : elle est
            // dessinée par le système, hors de notre processus, et un
            // `PendingIntent` est le seul lien qu'il accepte.
            val recepteur = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != ACTION_BASCULE) return
                    source?.bascule?.invoke()
                    // L'icône doit suivre l'état : sans ce rafraîchissement, la
                    // vignette garde le bouton « pause » sur une lecture qu'on
                    // vient justement de mettre en pause.
                    (ctx as? Activity)?.let { rafraichit(it) }
                }
            }
            ContextCompat.registerReceiver(
                context,
                recepteur,
                IntentFilter(ACTION_BASCULE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            onDispose {
                runCatching { context.unregisterReceiver(recepteur) }
                source = null
            }
        }
    }

    /** L'appareil sait-il afficher une vignette, et la version le permet-elle ? */
    fun disponible(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /**
     * Le système signale un départ : on bascule en vignette si ça joue.
     *
     * **Rien ne se passe si la lecture est en pause.** Une vignette figée sur
     * une image n'est pas un service rendu : elle occupe l'écran de ce qu'on est
     * allé faire ailleurs, et il faut la fermer à la main. Le geste de départ
     * doit alors rester ce qu'il a toujours été.
     *
     * Sur Android 12 et au-delà, [rafraichit] a déjà armé la bascule
     * automatique : redemander ici rejouerait l'animation.
     */
    fun surDepart(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        entre(activity)
    }

    /** Bascule maintenant, ou rend false si l'état ne s'y prête pas. */
    fun entre(activity: Activity): Boolean {
        val src = source ?: return false
        if (!disponible(activity) || activity.isFinishing || _actif.value) return false
        if (!src.enLecture()) return false
        // `enterPictureInPictureMode` lève quand l'utilisateur a refusé la
        // vignette à l'application dans les réglages du système. C'est un choix
        // qui le regarde, pas une panne : on repart sans rien dire.
        return runCatching {
            activity.enterPictureInPictureMode(parametres(activity, src))
        }.getOrDefault(false)
    }

    /**
     * Réaccorde la vignette à l'état courant.
     *
     * Appelé à chaque changement de lecture, et c'est ce qui fait **deux**
     * choses : l'icône du bouton suit l'état, et la bascule automatique
     * d'Android 12+ s'arme ou se désarme selon que ça joue. Sans le second
     * point, mettre en pause puis quitter ouvrirait une vignette figée.
     */
    fun rafraichit(activity: Activity) {
        val src = source ?: return
        if (!disponible(activity)) return
        runCatching { activity.setPictureInPictureParams(parametres(activity, src)) }
    }

    /** Le système a basculé, dans un sens ou dans l'autre. */
    fun modeChange(enPip: Boolean) {
        _actif.value = enPip
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parametres(activity: Activity, src: Source): PictureInPictureParams {
        val joue = src.enLecture()
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(ratio(src.taille()))
            .setActions(listOf(actionBascule(activity, src, joue)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Bascule continue depuis le plein écran, et redimensionnement sans
            // le fondu que le système applique par défaut à une vue qu'il
            // suppose non vidéo.
            builder.setAutoEnterEnabled(joue)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun actionBascule(activity: Activity, src: Source, joue: Boolean): RemoteAction {
        val icone = if (joue) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val titre = if (joue) src.libellePause else src.libelleLecture
        val intent = PendingIntent.getBroadcast(
            activity,
            0,
            Intent(ACTION_BASCULE).setPackage(activity.packageName),
            // `FLAG_IMMUTABLE` est exigé à partir d'Android 12 et disponible
            // depuis l'API 23 : inutile de le conditionner.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(Icon.createWithResource(activity, icone), titre, titre, intent)
    }

    /**
     * Ratio de l'image, borné à ce que le système accepte.
     *
     * Hors de `[1/2.39, 2.39]`, `setAspectRatio` **lève** — et une image de
     * taille encore inconnue vaut 0×0, ce qui y tombe aussi. Le repli est le
     * 16:9, faute de mieux : la première trame corrigera au rafraîchissement
     * suivant.
     */
    private fun ratio(taille: VideoSize): Rational {
        val largeur = taille.width
        val hauteur = taille.height
        if (largeur <= 0 || hauteur <= 0) return Rational(16, 9)
        val valeur = largeur.toDouble() / hauteur
        return when {
            valeur > RATIO_MAX -> Rational(239, 100)
            valeur < 1.0 / RATIO_MAX -> Rational(100, 239)
            else -> Rational(largeur, hauteur)
        }
    }

    private const val ACTION_BASCULE = "fr.moovie.tv.PIP_TOGGLE"

    /** Borne du système, des deux côtés. Au-delà, `setAspectRatio` lève. */
    private const val RATIO_MAX = 2.39
}
