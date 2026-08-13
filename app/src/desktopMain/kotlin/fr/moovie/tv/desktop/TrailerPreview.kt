package fr.moovie.tv.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.desktop.mpv.MpvEngine
import fr.moovie.tv.desktop.mpv.MpvPlayerController
import fr.moovie.tv.desktop.mpv.MpvVideoSurface
import fr.moovie.tv.ui.player.MooviePlayerController

/**
 * Aperçu de la bande-annonce dans le fond de la fiche, sur desktop.
 *
 * Même intention que son pendant Android — recadré, hors du chemin du focus —
 * au son près : ici il peut monter, quand la fiche passe en mode cinéma.
 *
 * Le moteur est mpv, et la simplicité de ce fichier est un des dividendes du
 * changement. Son prédécesseur libVLC exigeait une fabrique unique pour le
 * processus et un fil de commandes global — deux plantages (SIGABRT sur la
 * priorité des fils, SIGSEGV dans `libvlc_media_player_new`) avaient établi
 * cette discipline. Les instances mpv ne partagent rien : chaque aperçu a la
 * sienne, née et morte avec lui. Et le volume se règle **avant** la lecture —
 * libVLC ne créait sa sortie audio qu'au démarrage et perdait tout réglage
 * antérieur, ce qui faisait sortir du son sur un aperçu censé être muet.
 *
 * @param volume 0 (muet) à 1, piloté par le mode cinéma de la fiche. Le son ne
 *   monte qu'une fois l'interface effacée : voir `DetailsScreenContent`.
 */
@Composable
fun TrailerPreview(
    stream: PlayableStream,
    volume: Float,
    onController: (MooviePlayerController?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = remember(stream.url) { MpvVideoSurface() }
    val moteur = remember(stream.url) {
        MpvEngine(
            surImage = surface::publie,
            surFin = { surface.efface() },
            surErreur = { println("[apercu bande-annonce] $it") },
        )
    }

    LaunchedEffect(moteur, volume) {
        moteur.regleVolume(volume.coerceIn(0f, 1f))
    }

    DisposableEffect(moteur) {
        val controleur = MpvPlayerController(moteur)
        // googlevideo refuse toute requête qui ne demande pas un **morceau**
        // borné, et aucun lecteur n'en demande spontanément : mesuré, une
        // requête sans plage comme un `bytes=0-` rendent 403 sur une URL
        // neuve. Le relais découpe donc, sans que le moteur ait à le savoir.
        val relais = LocalStreamProxy(stream.headers, borneLesPlages = true)
        // L'ouverture touche le réseau : hors du fil d'interface, sans quoi la
        // fenêtre se fige le temps de la résolution DNS et du premier segment.
        val ouverture = Thread({
            // Les pistes séparées ne servent plus qu'en dernier recours :
            // l'extracteur privilégie désormais les formes que googlevideo sert
            // en entier (HLS, ou progressif image+son). Quand il n'a trouvé
            // qu'un manifeste, on ouvre quand même ses deux pistes — le
            // démuxeur DASH de FFmpeg, celui de mpv, ne sait pas lire nos
            // `BaseURL` googlevideo — en sachant que la lecture butera sur le
            // bridage vers 38 % du fichier, dont le relais fera une fin propre.
            val image = stream.videoOnlyUrl
            val son = stream.audioOnlyUrl
            val ouvert = if (image != null && son != null) {
                moteur.ouvre(relais.localUrl(image), stream.headers, urlAudio = relais.localUrl(son))
            } else {
                moteur.ouvre(relais.localUrl(stream.url), stream.headers)
            }
            println("[apercu bande-annonce] ouverture ${if (ouvert) "réussie" else "échouée"} : ${image ?: stream.url}")
            if (ouvert) {
                moteur.regleVolume(volume.coerceIn(0f, 1f))
                onController(controleur)
            }
        }, "moovie-apercu-ouverture").apply { isDaemon = true }
        ouverture.start()

        onDispose {
            onController(null)
            // La fermeture attend la fin des fils du moteur : pas sur le fil
            // d'interface. Elle est sûre pendant qu'une ouverture est en vol —
            // l'API mpv est une file de commandes.
            Thread({
                runCatching { ouverture.join(FERMETURE_MS) }
                moteur.ferme()
                relais.shutdown()
            }, "moovie-apercu-fermeture").apply { isDaemon = true }.start()
        }
    }

    Box(modifier) {
        // `frameTick` via key : la recomposition suit la production d'images —
        // sans lui, Compose ne voit qu'un `ImageBitmap` qui change d'identité
        // et redessine de façon erratique.
        key(surface.frameTick) {
            surface.image?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    // Recadré comme l'affiche qu'il remplace : des bandes noires
                    // trahiraient une vidéo posée là au lieu d'un décor.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

private const val FERMETURE_MS = 2_000L
