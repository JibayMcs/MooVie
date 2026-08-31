package fr.moovie.tv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import fr.moovie.tv.core.sources.usecase.isDurationAcceptable
import kotlinx.coroutines.delay

/**
 * Deuxième filet contre les sources qui servent autre chose que le média
 * demandé : un logo, une bande-annonce, un message d'indisponibilité.
 *
 * La cascade mesure déjà la durée avant d'ouvrir le lecteur, mais elle ne sait
 * le faire que sur du **HLS**, en sommant les `#EXTINF` de la playlist. Un MP4
 * passe donc au travers : sa durée n'est lisible qu'en téléchargeant son en-tête
 * `moov`. Ici, c'est le lecteur lui-même qui l'annonce, quel que soit le format
 * — d'où ce contrôle en second rideau plutôt qu'à la place du premier.
 *
 * Partagé entre les deux plateformes à dessein : Media3 côté Android TV et
 * libVLC côté desktop exposent tous deux leur durée via
 * [MooviePlayerController.durationMs], et la règle de décision est la même que
 * celle de la cascade. L'écrire deux fois, c'est se garantir qu'elles
 * divergeront.
 *
 * En cas de refus, on emprunte le chemin d'échec de lecture déjà en place
 * ([onTooShort] = `onPlaybackFailed`) : la cascade écarte le lien et enchaîne
 * sur la source suivante, exactement comme pour un flux mort.
 *
 * @param mediaId change à chaque nouveau flux, pour rearmer le contrôle.
 * @param expectedMinutes durée annoncée par TMDB ; 0 ou moins désactive tout.
 */
@Composable
fun PlayerDurationGuard(
    controller: MooviePlayerController,
    mediaId: String,
    expectedMinutes: Int,
    onTooShort: () -> Unit,
) {
    val reportTooShort by rememberUpdatedState(onTooShort)

    LaunchedEffect(mediaId, expectedMinutes) {
        if (expectedMinutes <= 0) return@LaunchedEffect

        // La durée n'est pas connue à l'ouverture : le lecteur doit avoir lu
        // assez du média pour l'annoncer. On l'interroge jusqu'à ce qu'elle
        // arrive, sans jamais bloquer la lecture.
        var waited = 0L
        while (waited < DURATION_WAIT_MS) {
            val durationMs = controller.durationMs()
            if (durationMs > 0) {
                if (!isDurationAcceptable(durationMs / 1000.0, expectedMinutes)) {
                    reportTooShort()
                }
                return@LaunchedEffect
            }
            delay(DURATION_POLL_MS)
            waited += DURATION_POLL_MS
        }
        // Durée toujours inconnue : flux live, ou lecteur qui ne l'expose pas.
        // On laisse lire — même principe que la cascade, on n'écarte que ce
        // qu'on a mesuré.
    }
}

/** Intervalle d'interrogation de la durée, tant qu'elle est inconnue. */
private const val DURATION_POLL_MS = 500L

/**
 * Au-delà, on renonce à connaître la durée. Assez large pour couvrir une
 * ouverture lente sur un hébergeur poussif, assez court pour que le contrôle
 * serve à quelque chose.
 */
private const val DURATION_WAIT_MS = 20_000L
