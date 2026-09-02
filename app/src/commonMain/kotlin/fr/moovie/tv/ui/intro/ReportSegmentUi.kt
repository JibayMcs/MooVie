package fr.moovie.tv.ui.intro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.moovie.tv.core.intro.SegmentKind
import fr.moovie.tv.core.intro.SubmissionProblem
import fr.moovie.tv.data.intro.SubmitError
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.report_cancel_hint
import fr.moovie.tv.resources.report_confirm_credits
import fr.moovie.tv.resources.report_confirm_intro_range
import fr.moovie.tv.resources.report_credits
import fr.moovie.tv.resources.report_error_already
import fr.moovie.tv.resources.report_error_auth
import fr.moovie.tv.resources.report_error_key
import fr.moovie.tv.resources.report_error_network
import fr.moovie.tv.resources.report_error_rate
import fr.moovie.tv.resources.report_error_rejected
import fr.moovie.tv.resources.report_intro
import fr.moovie.tv.resources.report_mark_credits_start
import fr.moovie.tv.resources.report_mark_now
import fr.moovie.tv.resources.report_mark_intro_end
import fr.moovie.tv.resources.report_mark_intro_start
import fr.moovie.tv.resources.report_marked_start
import fr.moovie.tv.resources.report_problem_generic
import fr.moovie.tv.resources.report_problem_long
import fr.moovie.tv.resources.report_problem_range
import fr.moovie.tv.resources.report_problem_reversed
import fr.moovie.tv.resources.report_problem_short
import fr.moovie.tv.resources.report_redo
import fr.moovie.tv.resources.report_seek_hint
import fr.moovie.tv.resources.report_segment
import fr.moovie.tv.resources.report_send
import fr.moovie.tv.resources.report_sending
import fr.moovie.tv.resources.report_sent
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.player.PlayerOption
import fr.moovie.tv.ui.player.PlayerOptionSection
import org.jetbrains.compose.resources.stringResource
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM

/**
 * Modale de signalement, montée sur le même modèle d'options que le reste du
 * lecteur — donc la même mécanique de focus, déjà éprouvée à la télécommande.
 *
 * Elle ne propose **que ce qui manque** : signaler une intro déjà connue n'a
 * aucun intérêt, et une option inerte est une cible de plus à traverser.
 */
@Composable
fun reportSegmentSection(
    step: ReportStep,
    onMark: (SegmentKind) -> Unit,
    onSend: () -> Unit,
    onRedo: () -> Unit,
): PlayerOptionSection = when (step) {

    is ReportStep.Choosing -> PlayerOptionSection(
        stringResource(Res.string.report_segment),
        buildList {
            if (step.introMissing) {
                add(PlayerOption(stringResource(Res.string.report_intro), false) {
                    onMark(SegmentKind.INTRO)
                })
            }
            if (step.creditsMissing) {
                add(PlayerOption(stringResource(Res.string.report_credits), false) {
                    onMark(SegmentKind.CREDITS)
                })
            }
        },
    )

    is ReportStep.Confirming -> PlayerOptionSection(
        step.submission.describe(),
        listOf(
            PlayerOption(stringResource(Res.string.report_send), true) { onSend() },
            PlayerOption(stringResource(Res.string.report_redo), false) { onRedo() },
        ),
    )

    is ReportStep.Invalid -> PlayerOptionSection(
        step.problem.message(),
        listOf(PlayerOption(stringResource(Res.string.report_redo), false) { onRedo() }),
    )

    ReportStep.Sending -> PlayerOptionSection(
        stringResource(Res.string.report_sending),
        emptyList(),
    )

    is ReportStep.Done -> PlayerOptionSection(
        step.error?.message() ?: stringResource(Res.string.report_sent),
        emptyList(),
    )

    else -> PlayerOptionSection("", emptyList())
}

/**
 * Invite affichée **par-dessus la vidéo** pendant la capture.
 *
 * Volontairement discrète et sans bouton : le seul geste attendu est un appui
 * sur OK au bon moment, et l'utilisateur doit garder l'image en vue pour viser
 * juste. Lui masquer la scène qu'il est en train de chronométrer serait absurde.
 */
@Composable
fun ReportMarkingBanner(
    step: ReportStep,
    /**
     * Position courante, affichée en grand.
     *
     * Sans elle, avancer dans la vidéo pour viser une intro qu'on connaît déjà
     * reviendrait à chercher à l'aveugle : c'est ce chiffre qui sera envoyé, il
     * doit être lisible au moment où on valide.
     */
    positionMs: Long = 0,
    modifier: Modifier = Modifier,
    /**
     * Marquage au clic, pour le desktop. Null sur TV, où le geste attendu est
     * un appui sur OK — y ajouter un bouton donnerait une cible à viser au
     * D-pad pendant qu'on chronomètre, exactement ce qu'on veut éviter.
     */
    onMark: (() -> Unit)? = null,
) {
    val marking = step as? ReportStep.Marking ?: return
    val prompt = when {
        marking.kind == SegmentKind.CREDITS -> stringResource(Res.string.report_mark_credits_start)
        marking.markingStart -> stringResource(Res.string.report_mark_intro_start)
        else -> stringResource(Res.string.report_mark_intro_end)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .padding(top = 48.dp)
                .background(Color(0xCC101010))
                .padding(horizontal = 24.dp, vertical = 14.dp)
                .widthIn(max = 720.dp),
        ) {
            Text(
                prompt,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(clock(positionMs), style = MaterialTheme.typography.headlineMedium)
            // Le début déjà relevé reste sous les yeux pendant qu'on vise la
            // fin : c'est la seule façon de voir le segment qu'on est en train
            // de délimiter, plutôt que deux chiffres sans rapport apparent.
            marking.startMs?.let { start ->
                Text(
                    stringResource(Res.string.report_marked_start, clock(start)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9AD5A0),
                )
            }
            onMark?.let {
                MoovieButton(onClick = it) { Text(stringResource(Res.string.report_mark_now)) }
            }
            Text(
                stringResource(Res.string.report_seek_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MOOVIE_TEXT_DIM,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(Res.string.report_cancel_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MOOVIE_TEXT_DIM,
            )
        }
    }
}

/** « Intro jusqu'à 1:25 » — l'utilisateur voit ce qu'il envoie avant d'envoyer. */
@Composable
private fun fr.moovie.tv.core.intro.SegmentSubmission.describe(): String {
    return when {
        kind == SegmentKind.CREDITS ->
            stringResource(Res.string.report_confirm_credits, clock(startMs ?: 0))
        // Une intro porte toujours ses deux bornes : elle se marque en deux
        // appuis, du début vers la fin.
        else -> stringResource(
            Res.string.report_confirm_intro_range,
            clock(startMs ?: 0),
            clock(endMs ?: 0),
        )
    }
}

@Composable
private fun SubmissionProblem.message(): String = when (this) {
    SubmissionProblem.TOO_SHORT -> stringResource(Res.string.report_problem_short)
    SubmissionProblem.TOO_LONG -> stringResource(Res.string.report_problem_long)
    SubmissionProblem.REVERSED -> stringResource(Res.string.report_problem_reversed)
    SubmissionProblem.OUT_OF_RANGE -> stringResource(Res.string.report_problem_range)
    else -> stringResource(Res.string.report_problem_generic)
}

@Composable
private fun SubmitError.message(): String = when (this) {
    SubmitError.NO_KEY -> stringResource(Res.string.report_error_key)
    SubmitError.UNAUTHORIZED -> stringResource(Res.string.report_error_auth)
    SubmitError.ALREADY_SUBMITTED -> stringResource(Res.string.report_error_already)
    SubmitError.RATE_LIMITED -> stringResource(Res.string.report_error_rate)
    SubmitError.REJECTED -> stringResource(Res.string.report_error_rejected)
    SubmitError.NETWORK -> stringResource(Res.string.report_error_network)
}

/** `h:mm:ss` au-delà d'une heure, `m:ss` en dessous. */
private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val s = total % 60
    val m = (total / 60) % 60
    val h = total / 3600
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "$m:${s.toString().padStart(2, '0')}"
    }
}
