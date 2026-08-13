package fr.moovie.tv.ui.player

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import fr.moovie.tv.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Les commandes de lecture dans le volet et sur l'écran verrouillé.
 *
 * ### Ce qu'elle ajoute à la vignette
 *
 * La vignette ne se voit que sur l'écran d'accueil et pendant qu'on regarde
 * ailleurs. Elle disparaît dès que l'écran s'éteint, et elle n'existe pas sur le
 * verrouillage. La notification, elle, survit aux deux : c'est le seul endroit
 * d'où l'on peut mettre en pause **sans rallumer l'application**.
 *
 * ### Une notification, pas un service
 *
 * On ne passe **pas** par un `MediaSessionService`. Ce serait le chemin canonique
 * pour une lecture qui survit à l'application, mais il exige de sortir
 * l'ExoPlayer de la composition pour le confier à un service — un remaniement du
 * lecteur entier, alors que ce qui manque ici tient dans une notification. Le
 * prix assumé : la lecture s'arrête si le système tue le processus, comme
 * aujourd'hui.
 *
 * ### La session existait déjà
 *
 * [MediaSession] est construite par le lecteur depuis toujours, pour que les
 * touches média des télécommandes lui parviennent. La lier ici par son jeton est
 * ce qui donne le style média — la jaquette en fond, la barre de progression
 * qu'Android 13 dessine lui-même — au lieu d'une notification de texte avec des
 * boutons.
 */
@Composable
fun PlayerMediaNotification(
    actif: Boolean,
    player: Player,
    session: MediaSession,
    titre: String,
    soustitre: String,
    posterUrl: String,
    nomCanal: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val titreCourant by rememberUpdatedState(titre)
    val soustitreCourant by rememberUpdatedState(soustitre)
    val posterCourant by rememberUpdatedState(posterUrl)

    // Android 13 masque toute notification non autorisée, sans rien dire à
    // l'application : sans cette demande, le code ci-dessous s'exécuterait
    // parfaitement et personne ne verrait jamais les commandes. On la pose ici,
    // au moment où elle se justifie — une lecture vient de commencer — plutôt
    // qu'au premier lancement, où elle n'expliquerait rien.
    val demande = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(actif) {
        if (!actif || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val accordee = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!accordee) demande.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    DisposableEffect(actif, player, session, nomCanal) {
        if (!actif) return@DisposableEffect onDispose {}
        creeCanal(context, nomCanal)

        val gestionnaire = PlayerNotificationManager.Builder(context, NOTIFICATION_ID, CANAL_ID)
            .setMediaDescriptionAdapter(
                object : PlayerNotificationManager.MediaDescriptionAdapter {
                    override fun getCurrentContentTitle(player: Player) = titreCourant

                    override fun getCurrentContentText(player: Player) = soustitreCourant

                    override fun createCurrentContentIntent(player: Player): PendingIntent =
                        PendingIntent.getActivity(
                            context,
                            0,
                            Intent(context, MainActivity::class.java),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )

                    /**
                     * La jaquette arrive **après coup**, par le rappel : la
                     * charger ici bloquerait le fil principal sur le réseau, et
                     * une notification qui attend une image est une notification
                     * qui n'apparaît pas.
                     */
                    override fun getCurrentLargeIcon(
                        player: Player,
                        callback: PlayerNotificationManager.BitmapCallback,
                    ): Bitmap? {
                        val url = posterCourant
                        if (url.isBlank()) return null
                        scope.launch {
                            jaquette(context, url)?.let(callback::onBitmap)
                        }
                        return null
                    }
                },
            )
            .build()

        gestionnaire.setPlayer(player)
        // Le jeton, donc le style média : jaquette en fond et barre de
        // progression dessinée par le système. Sans lui, les mêmes boutons
        // apparaissent dans une notification de texte ordinaire.
        //
        // Le jeton **de plateforme**, et non son équivalent `MediaSessionCompat` :
        // ce dernier appartient à `androidx.media`, que media3 n'expose pas à la
        // compilation. Ajouter la bibliothèque de support pour un seul type
        // serait payer cher une conversion que le système sait faire.
        gestionnaire.setMediaSessionToken(session.platformToken)
        // Les flèches d'épisode. Elles ne marchent que parce que le lecteur
        // confié ici est un [EpisodePlayer] : il déclare les commandes
        // « précédent » et « suivant », que l'ExoPlayer nu refusait faute de
        // playlist. Et en vue compacte, sans quoi la notification repliée — la
        // seule qu'on voie sans la dérouler — n'aurait que lecture/pause.
        gestionnaire.setUsePreviousAction(true)
        gestionnaire.setUseNextAction(true)
        gestionnaire.setUsePreviousActionInCompactView(true)
        gestionnaire.setUseNextActionInCompactView(true)
        // Reculer et avancer de 15 s : le lecteur déclare déjà ce pas
        // (`setSeekBackIncrementMs`), la notification le reprend tel quel.
        gestionnaire.setUseRewindAction(true)
        gestionnaire.setUseFastForwardAction(true)
        // Une lecture ne se balaie pas : la retirer du volet ne l'arrêterait
        // pas, et on se retrouverait avec du son sans aucune commande.
        gestionnaire.setUseStopAction(false)

        onDispose {
            // Détacher le lecteur retire la notification. C'est ce qui garantit
            // qu'aucune commande ne survit à la lecture qu'elle pilote.
            gestionnaire.setPlayer(null)
        }
    }
}

/**
 * Télécharge la jaquette et la rend en bitmap, ou null.
 *
 * Par le chargeur de l'application : l'image est déjà dans son cache, la fiche
 * et l'accueil venant de l'afficher. La retélécharger pour une notification
 * serait payer deux fois la même vignette.
 */
private suspend fun jaquette(context: Context, url: String): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val requete = ImageRequest.Builder(context).data(url).build()
            val resultat = SingletonImageLoader.get(context).execute(requete)
            (resultat as? SuccessResult)?.image?.let { it as? BitmapImage }?.bitmap
        }.getOrNull()
    }

private fun creeCanal(context: Context, nom: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val gestionnaire =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    // `IMPORTANCE_LOW` : la lecture est en cours sous les yeux de qui l'a
    // lancée. Un son ou un bandeau par-dessus le film serait absurde.
    gestionnaire.createNotificationChannel(
        NotificationChannel(CANAL_ID, nom, NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        },
    )
}

private const val CANAL_ID = "playback"

/** Distinct de celui des téléchargements (4201) : les deux peuvent coexister. */
private const val NOTIFICATION_ID = 4202

