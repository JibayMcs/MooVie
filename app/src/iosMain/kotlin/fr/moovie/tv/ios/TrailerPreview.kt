package fr.moovie.tv.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.ui.player.AvPlayerController
import fr.moovie.tv.ui.player.MooviePlayerController
import fr.moovie.tv.ui.player.SurfaceVideo
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.volume

/**
 * L'aperçu de bande-annonce dans le fond de la fiche, sur iOS.
 *
 * ## Ce qu'il corrige
 *
 * Sans lui, `trailerPreview` valait null et le bouton « Bande-annonce » ne
 * faisait **rien** : `TrailerButton` ne s'affiche que sur `TrailerState.Ready`,
 * jamais selon la présence d'un aperçu. On avait donc exactement ce que ce code
 * refuse partout ailleurs — une cible qu'on vise et qui ne répond pas.
 *
 * L'argument qui l'avait écarté — « deux AVPlayer vivants sur le même écran » —
 * était faux. La fiche et le lecteur sont deux entrées de navigation : le `when`
 * de la racine en affiche une, jamais les deux.
 *
 * ## Pourquoi il ne joue parfois rien, et pourquoi c'est correct
 *
 * AVPlayer lit le HLS nativement et le progressif sans peine, mais **ignore
 * DASH**. Quand l'extracteur n'a trouvé qu'un manifeste — image et son sur deux
 * URL distinctes, le dernier recours de YouTube — il n'y a rien à lui donner :
 * les réunir demanderait une `AVMutableComposition` et le chargement asynchrone
 * de deux pistes distantes, pour un décor.
 *
 * Ce cas ne rend donc rien du tout, et c'est délibéré : l'affiche floutée reste
 * **sous** l'aperçu, jamais à sa place — `DetailsScreenContent` le dit dans son
 * propre commentaire. Ne rien dessiner la laisse voir, là où un lecteur en échec
 * aurait posé un rectangle noir par-dessus.
 *
 * ## Pas de relais local
 *
 * Le desktop passe par `LocalStreamProxy` pour borner ses plages d'octets :
 * googlevideo répond 403 aux requêtes qui n'en demandent pas, et mpv n'en
 * demande pas. NSURLSession, elle, découpe d'elle-même — c'est ainsi qu'AVPlayer
 * lit tout média distant. Le relais n'aurait rien à corriger, et il n'existe de
 * toute façon que sur les cibles JVM.
 *
 * @param volume 0 (muet) à 1, piloté par le mode cinéma de la fiche. Le son ne
 *   monte qu'une fois l'interface effacée — voir `DetailsScreenContent`.
 */
@Composable
internal fun TrailerPreviewIos(
    stream: PlayableStream,
    volume: Float,
    onController: (MooviePlayerController?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pistes séparées = manifeste DASH. Voir le KDoc : on laisse la place à
    // l'affiche plutôt que de tenter une lecture qu'AVPlayer ne sait pas faire.
    if (stream.videoOnlyUrl != null && stream.audioOnlyUrl != null) {
        // Le contrat de `onController` est « voici le lecteur, ou null s'il n'y
        // en a pas ». La fiche s'en sert pour ses commandes de bande-annonce ;
        // sans ce null elle en garderait un qui n'existe plus.
        DisposableEffect(stream.url) {
            onController(null)
            onDispose { }
        }
        return
    }

    val controleur = remember(stream.url) {
        AvPlayerController(stream.url, stream.headers)
    }

    // Le volume suit le mode cinéma. Réglé sur le lecteur natif : le contrat
    // commun `MooviePlayerController` n'expose pas le volume, parce que sur TV
    // et sur téléphone c'est le système qui le tient.
    LaunchedEffect(controleur, volume) {
        controleur.player.volume = volume.coerceIn(0f, 1f)
    }

    DisposableEffect(controleur) {
        onController(controleur)
        onDispose {
            onController(null)
            controleur.liberer()
        }
    }

    SurfaceVideo(
        controleur = controleur,
        modifier = modifier,
        // Recadré comme l'affiche qu'il remplace : des bandes noires
        // trahiraient une vidéo posée là au lieu d'un décor. Même choix que le
        // `ContentScale.Crop` de l'aperçu desktop.
        gravite = AVLayerVideoGravityResizeAspectFill,
    )
}
