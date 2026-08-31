package fr.moovie.tv.ui.subtitles

import androidx.compose.runtime.Composable
import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.core.subtitles.usecase.COMMON_FPS
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.player_online_subtitles
import fr.moovie.tv.resources.player_subtitles_downloaded
import fr.moovie.tv.resources.player_subtitles_earlier
import fr.moovie.tv.resources.player_subtitles_fps
import fr.moovie.tv.resources.player_subtitles_fps_auto
import fr.moovie.tv.resources.player_subtitles_later
import fr.moovie.tv.resources.player_subtitles_network
import fr.moovie.tv.resources.player_subtitles_none_found
import fr.moovie.tv.resources.player_subtitles_offset
import fr.moovie.tv.resources.player_subtitles_quota_left
import fr.moovie.tv.resources.player_subtitles_quota_out
import fr.moovie.tv.resources.player_subtitles_searching
import fr.moovie.tv.resources.player_subtitles_sync
import fr.moovie.tv.resources.player_subtitles_sync_reset
import fr.moovie.tv.ui.player.PlayerOption
import fr.moovie.tv.ui.player.PlayerOptionSection
import org.jetbrains.compose.resources.stringResource

/**
 * Sections « sous-titres en ligne » de la modale du lecteur.
 *
 * Elles réutilisent le modèle d'options déjà en place plutôt que d'inventer une
 * fenêtre à part : c'est la même mécanique de focus, éprouvée à la télécommande,
 * et les deux lecteurs les ajoutent à l'identique.
 */

/**
 * Liste des sous-titres proposés.
 *
 * Chaque ligne affiche **la langue, la cadence et le nom de la release**, et pas
 * seulement la langue. La sonde avait montré pourquoi : les mieux classés pour
 * Fight Club étaient des sous-titres d'une autre série, mal étiquetés sous le
 * même identifiant TMDB. Aucun critère automatique ne détecte ça — seul le nom
 * de la release trahit, et l'utilisateur doit l'avoir sous les yeux **avant** de
 * dépenser un téléchargement.
 */
@Composable
fun onlineSubtitleSection(
    state: PlayerSubtitlesState,
    onPick: (SubtitleCandidate) -> Unit,
): PlayerOptionSection {
    val title = buildString {
        append(stringResource(Res.string.player_online_subtitles))
        state.quota.remaining?.let {
            append(" — ")
            append(stringResource(Res.string.player_subtitles_quota_left, it))
        }
    }

    val options = when {
        state.loading -> listOf(PlayerOption(stringResource(Res.string.player_subtitles_searching), false) {})
        state.trouble == SubtitleTrouble.QUOTA ->
            listOf(PlayerOption(stringResource(Res.string.player_subtitles_quota_out), false) {})
        state.trouble == SubtitleTrouble.NETWORK ->
            listOf(PlayerOption(stringResource(Res.string.player_subtitles_network), false) {})
        state.candidates.isEmpty() ->
            listOf(PlayerOption(stringResource(Res.string.player_subtitles_none_found), false) {})
        else -> {
            val mark = stringResource(Res.string.player_subtitles_downloaded)
            state.candidates.take(MAX_SHOWN).map { candidate ->
                val paid = candidate.fileId in state.downloaded
                PlayerOption(
                    // Le repère passe **en tête** : c'est l'information la plus
                    // actionnable de la ligne — gratuit ou non — et la seule que
                    // la troncature de la release ne doit jamais emporter.
                    label = if (paid) "$mark · ${candidate.describe()}" else candidate.describe(),
                    selected = candidate.fileId == state.active?.fileId,
                ) { onPick(candidate) }
            }
        }
    }
    return PlayerOptionSection(title, options)
}

/**
 * Réglage de synchronisation : décalage par pas, et remise à zéro.
 *
 * Des pas plutôt qu'un curseur, parce qu'à la télécommande un curseur se règle
 * mal — et parce que chaque changement réécrit le fichier, ce qui côté Media3
 * recharge le flux. Un nombre borné d'appuis vaut mieux qu'un glissement continu.
 */
@Composable
fun subtitleSyncSection(
    state: PlayerSubtitlesState,
    onNudge: (Long) -> Unit,
    onReset: () -> Unit,
): PlayerOptionSection {
    if (state.active == null) return PlayerOptionSection("", emptyList())

    val offsetSeconds = state.timing.offsetMs / 1000.0
    val title = stringResource(Res.string.player_subtitles_sync) +
        " — " + stringResource(Res.string.player_subtitles_offset, format(offsetSeconds))

    val options = buildList {
        NUDGES.forEach { step ->
            val label = if (step < 0) {
                stringResource(Res.string.player_subtitles_earlier, format(-step / 1000.0))
            } else {
                stringResource(Res.string.player_subtitles_later, format(step / 1000.0))
            }
            add(PlayerOption(label, false) { onNudge(step) })
        }
        add(PlayerOption(stringResource(Res.string.player_subtitles_sync_reset), false) { onReset() })
    }
    return PlayerOptionSection(title, options)
}

/**
 * Cadence d'origine du sous-titre, à forcer quand le catalogue ne la déclare
 * pas — c'était le cas de 14 % des résultats mesurés. Sans elle, la dérive
 * progressive ne peut pas être corrigée autrement qu'à tâtons.
 */
@Composable
fun subtitleFpsSection(
    state: PlayerSubtitlesState,
    onAssume: (Double) -> Unit,
): PlayerOptionSection {
    if (state.active == null) return PlayerOptionSection("", emptyList())

    val declared = state.active.fps
    val options = buildList {
        add(
            PlayerOption(
                stringResource(Res.string.player_subtitles_fps_auto) +
                    (declared?.let { " (${format(it)})" } ?: ""),
                !state.timing.scaled,
            ) { onAssume(0.0) },
        )
        COMMON_FPS.forEach { fps ->
            add(PlayerOption(format(fps), false) { onAssume(fps) })
        }
    }
    return PlayerOptionSection(stringResource(Res.string.player_subtitles_fps), options)
}

/** « fr · 23.976 · Fight.Club.2160p.BluRay » — tout ce qui aide à trancher. */
private fun SubtitleCandidate.describe(): String = buildString {
    append(language.uppercase())
    fps?.let { append(" · ").append(format(it)) }
    if (foreignPartsOnly) append(" · forcé")
    if (hearingImpaired) append(" · SDH")
    if (release.isNotBlank()) append(" · ").append(release.take(36))
}

/** Deux décimales au plus, sans zéro inutile : « 23.976 », « 1.5 », « 25 ». */
private fun format(value: Double): String =
    value.toString().trimEnd('0').trimEnd('.').ifEmpty { "0" }

/**
 * Pas de décalage proposés. Bornés volontairement : au-delà de deux secondes il
 * ne s'agit plus d'un décalage mais d'un mauvais sous-titre, ou d'une cadence à
 * corriger — deux problèmes que ce réglage ne résout pas.
 */
private val NUDGES = listOf(-2_000L, -500L, 500L, 2_000L)

/**
 * La liste peut compter cent entrées ; au D-pad, en descendre cinquante est
 * hors de question. Le classement fait le travail, on ne montre que sa tête.
 */
private const val MAX_SHOWN = 12
