package fr.moovie.tv.ui.intro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.core.intro.SegmentKind
import fr.moovie.tv.core.intro.SegmentSubmission
import fr.moovie.tv.core.intro.SubmissionProblem
import fr.moovie.tv.core.intro.validateSubmission
import fr.moovie.tv.data.intro.IntroDbRepository
import fr.moovie.tv.data.intro.SubmitError
import fr.moovie.tv.ui.player.PlaybackId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Étapes du signalement d'un segment à TheIntroDB.
 *
 * Le parcours tient en quatre appuis, dont **un seul demande de l'attention** :
 * ouvrir, choisir le type, marquer au bon moment, confirmer. La position de
 * lecture *est* la saisie — il n'y a pas de champ à remplir, pas de curseur à
 * régler, et rien à taper sur un clavier virtuel.
 */
sealed interface ReportStep {

    data object Idle : ReportStep

    /** Choix du segment. On ne propose que ceux qui manquent réellement. */
    data class Choosing(val introMissing: Boolean, val creditsMissing: Boolean) : ReportStep

    /**
     * Capture en cours : la lecture continue, l'utilisateur valide au moment
     * voulu. [markingStart] distingue les deux invites — « quand ça commence »
     * ou « quand ça se termine ».
     *
     * [startMs] rappelle le début déjà relevé pendant qu'on vise la fin : sans
     * lui, on valide la seconde borne sans plus voir la première.
     */
    data class Marking(
        val kind: SegmentKind,
        val markingStart: Boolean,
        val startMs: Long? = null,
    ) : ReportStep

    /** Dernier regard avant l'envoi, avec l'horodatage relevé sous les yeux. */
    data class Confirming(val submission: SegmentSubmission) : ReportStep

    /** Le relevé ne tient pas debout ; on dit lequel et pourquoi. */
    data class Invalid(val kind: SegmentKind, val problem: SubmissionProblem) : ReportStep

    data object Sending : ReportStep

    /** [error] à null = envoyé. */
    data class Done(val kind: SegmentKind, val error: SubmitError?) : ReportStep
}

/**
 * Signalement d'intro ou de générique manquant, depuis le lecteur.
 *
 * Deux garde-fous, parce que la base est **communautaire** et qu'un horodatage
 * faux y reste : rien ne part sans une confirmation montrant la valeur relevée,
 * et le relevé est validé localement contre les bornes de l'API avant l'envoi —
 * un refus compréhensible vaut mieux qu'un 400 après coup.
 */
class ReportSegmentViewModel : ViewModel() {

    private val repo = IntroDbRepository()

    private val _step = MutableStateFlow<ReportStep>(ReportStep.Idle)
    val step: StateFlow<ReportStep> = _step.asStateFlow()

    /** Vrai si une clé est renseignée : sans elle, rien à proposer. */
    private val _canReport = MutableStateFlow(false)
    val canReport: StateFlow<Boolean> = _canReport.asStateFlow()

    private var playback: PlaybackId? = null
    private var durationMs: Long = 0

    /** Début d'intro déjà relevé, en attendant sa fin. */
    private var pendingStart: Long? = null

    fun refreshAvailability() = io { _canReport.value = repo.canSubmit() }

    fun bind(playback: PlaybackId?, durationMs: Long) {
        this.playback = playback
        this.durationMs = durationMs
    }

    fun open(introMissing: Boolean, creditsMissing: Boolean) {
        _step.value = ReportStep.Choosing(introMissing, creditsMissing)
    }

    /**
     * Lance la capture. On demande toujours le **début** en premier : c'est
     * l'ordre dans lequel le segment se déroule à l'écran, donc le seul qui se
     * marque sans jamais revenir en arrière.
     */
    fun startMarking(kind: SegmentKind) {
        pendingStart = null
        _step.value = ReportStep.Marking(kind, markingStart = true)
    }

    /**
     * Déclare l'absence de segment — une contribution à part entière : elle
     * évite à tout le monde d'en chercher un qui n'existe pas.
     */
    fun declareAbsent(kind: SegmentKind) {
        val submission = build(kind, startMs = 0, endMs = 0)
        _step.value = submission?.let { ReportStep.Confirming(it) } ?: ReportStep.Idle
    }

    /**
     * Enregistre la position courante.
     *
     * Le générique se referme sur un seul appui — sa fin, c'est la fin du média,
     * et l'API l'entend ainsi. L'intro en demande deux, dans l'ordre de lecture :
     * un cold open peut la décaler de plusieurs minutes, et rien ne permet de le
     * deviner.
     */
    fun mark(positionMs: Long) {
        val current = _step.value as? ReportStep.Marking ?: return

        if (current.kind == SegmentKind.CREDITS) {
            finish(build(SegmentKind.CREDITS, startMs = positionMs, endMs = null))
            return
        }

        if (current.markingStart) {
            pendingStart = positionMs
            _step.value = ReportStep.Marking(
                SegmentKind.INTRO,
                markingStart = false,
                startMs = positionMs,
            )
        } else {
            finish(build(SegmentKind.INTRO, startMs = pendingStart, endMs = positionMs))
        }
    }

    fun send() = io {
        val submission = (_step.value as? ReportStep.Confirming)?.submission ?: return@io
        _step.value = ReportStep.Sending
        _step.value = ReportStep.Done(submission.kind, repo.submit(submission))
    }

    fun cancel() {
        pendingStart = null
        _step.value = ReportStep.Idle
    }

    private fun finish(submission: SegmentSubmission?) {
        if (submission == null) {
            _step.value = ReportStep.Idle
            return
        }
        val problem = validateSubmission(submission)
        _step.value = if (problem == null) {
            ReportStep.Confirming(submission)
        } else {
            ReportStep.Invalid(submission.kind, problem)
        }
    }

    private fun build(kind: SegmentKind, startMs: Long?, endMs: Long?): SegmentSubmission? {
        val id = playback ?: return null
        return SegmentSubmission(
            tmdbId = id.tmdbId,
            isTv = id.isTv,
            kind = kind,
            season = id.season.takeIf { id.isTv },
            episode = id.episode.takeIf { id.isTv },
            startMs = startMs,
            endMs = endMs,
            // Rattache le signalement à la version regardée : sans elle, un
            // horodatage relevé sur une version longue serait servi à qui
            // regarde la version cinéma.
            videoDurationMs = durationMs.takeIf { it > 0 },
        )
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch { withContext(Dispatchers.IO) { block() } }
    }
}
