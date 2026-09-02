package fr.moovie.tv

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import fr.moovie.tv.data.settings.LocaleManager
import fr.moovie.tv.ui.adaptive.AdaptiveRoot
import fr.moovie.tv.ui.adaptive.UiFlavor
import fr.moovie.tv.ui.player.Pip
import fr.moovie.tv.ui.player.PlayerHost
import fr.moovie.tv.ui.player.PlayerScreen
import fr.moovie.tv.ui.theme.MooVieTheme
import fr.moovie.tv.ui.theme.MooVieTvMaterialTheme

/**
 * Le lecteur du téléphone, dans sa propre fenêtre.
 *
 * ### Ce qu'elle apporte, et rien d'autre
 *
 * Sa seule raison d'être est la vignette : une Activity qui part en
 * picture-in-picture laisse celle du dessous revenir au premier plan, si bien
 * qu'on continue de parcourir Moo-vie pendant qu'un épisode joue dans un coin.
 * Avec le lecteur rendu à l'intérieur de [MainActivity], c'était impossible —
 * Android déplace l'Activity dans la vignette, il n'en fabrique pas une copie.
 *
 * **Le téléviseur ne passe jamais par ici.** Voir [PlayerHost] : `MainActivity`
 * continue d'y rendre `PlayerScreen` en interne, et rien du focus D-pad, de
 * l'anti-veille ou de l'enchaînement n'a bougé de ce côté.
 *
 * ### Ce qu'elle ne sait pas faire seule
 *
 * Trois gestes du lecteur passent par le `DetailsViewModel`, qui a le scope de
 * `MainActivity` : préparer les sources de l'épisode suivant, reprendre la
 * cascade après un flux mort, enchaîner l'épisode. Cette Activity ne les exécute
 * pas, elle les **demande** ([PlayerHost.Demande]) et se referme. Une seconde
 * instance de ce ViewModel serait vide de la série en cours.
 *
 * La pastille de mise à jour n'est pas reprise : elle appartient au parcours de
 * `MainActivity`, et proposer une mise à jour à quelqu'un qui regarde un film
 * sur son téléphone n'a jamais été le but.
 */
class PlayerActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Le processus a été tué et l'Activity recréée sans ce qu'elle jouait :
        // il n'y a rien à reprendre, et une fenêtre noire vaudrait moins que
        // pas de fenêtre. Voir PlayerHost.
        val ecran = PlayerHost.preleve()
        if (ecran == null) {
            finish()
            return
        }

        setContent {
            MooVieTvMaterialTheme {
                // Toujours TOUCH : cette Activity n'existe pas sur téléviseur, et
                // le lecteur lit ce drapeau pour choisir ses gestes tactiles.
                //
                // Au-dessus du thème : `moovieTypography()` lit la classe de
                // hauteur que cette racine est seule à fournir. Voir MainActivity.
                AdaptiveRoot(flavor = UiFlavor.TOUCH, modifier = Modifier.fillMaxSize()) {
                MooVieTheme {
                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                            // Paysage et plein écran immersif, comme le faisait
                            // MainActivity autour de ce même écran. Posé une fois :
                            // ici, il n'y a que le lecteur, donc rien à restaurer.
                            LaunchedEffect(Unit) { pleinEcran() }

                            PlayerScreen(
                                streamUrl = ecran.streamUrl,
                                headers = ecran.headers,
                                mediaKey = ecran.mediaKey,
                                sourceUrl = ecran.sourceUrl,
                                hoster = ecran.hoster,
                                language = ecran.language,
                                alternatives = ecran.alternatives,
                                subtitles = ecran.subtitles,
                                title = ecran.title,
                                subtitle = ecran.subtitle,
                                nextSeason = ecran.nextSeason,
                                nextEpisode = ecran.nextEpisode,
                                posterUrl = ecran.posterUrl,
                                startAtMs = ecran.startAtMs,
                                expectedMinutes = ecran.expectedMinutes,
                                onPrefetchNext = {
                                    PlayerHost.demande(
                                        PlayerHost.Demande.Prefetch(
                                            ecran.nextSeason,
                                            ecran.nextEpisode,
                                        ),
                                    )
                                },
                                onBack = { finish() },
                                // La diffusion a pris : cette fenêtre n'a plus
                                // rien à montrer, mais la refermer sèchement
                                // ramènerait à la fiche sans dire que le film
                                // joue sur la télé. On demande l'écran de
                                // diffusion à MainActivity, puis on s'efface.
                                onCastStarted = {
                                    PlayerHost.demande(PlayerHost.Demande.Diffusion)
                                    finish()
                                },
                                onPlaybackFailed = {
                                    PlayerHost.demande(PlayerHost.Demande.Echec)
                                    finish()
                                },
                                onNextEpisode = { tmdbId, saison, episode ->
                                    PlayerHost.demande(
                                        PlayerHost.Demande.Episode(tmdbId, saison, episode),
                                    )
                                    finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * L'utilisateur s'en va : la lecture le suit dans une vignette.
     *
     * C'est ici que ces deux rappels vivent désormais, et non plus dans
     * `MainActivity` : c'est cette fenêtre-ci qui part en vignette, et elle seule.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        Pip.surDepart(this)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Pip.modeChange(isInPictureInPictureMode)
        // Quitter la vignette par sa croix ferme la lecture : le système détruit
        // l'Activity, et sans ce garde-fou l'écran plein reviendrait une fraction
        // de seconde avant de disparaître.
        if (!isInPictureInPictureMode && isFinishing) Pip.modeChange(false)
    }

    override fun onDestroy() {
        // La composition part avec l'Activity, mais l'état de vignette est
        // global : le laisser à vrai effacerait la chrome de la lecture suivante.
        Pip.modeChange(false)
        super.onDestroy()
    }

    /**
     * Paysage, barres cachées, et dessin jusque dans la découpe.
     *
     * Repris tel quel de `MainActivity`, y compris ses deux pièges : `ALWAYS`
     * plutôt que `SHORT_EDGES` — ce dernier n'ouvre les bords qu'en portrait, et
     * la découpe est sur un côté en paysage — et `setDecorFitsSystemWindows` à
     * false, sans quoi le décor continue d'insérer la largeur de la découpe en
     * marge et décale l'image.
     */
    private fun pleinEcran() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val barres = WindowInsetsControllerCompat(window, window.decorView)
        barres.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        barres.hide(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
            }
        }
    }
}
