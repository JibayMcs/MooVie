package fr.moovie.tv.ui.subtitles

import fr.moovie.tv.shared.dispatcherEs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.core.subtitles.usecase.SubtitleTiming
import fr.moovie.tv.core.subtitles.usecase.rankSubtitles
import fr.moovie.tv.core.subtitles.usecase.timingFor
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.tmdb.TmdbRepository
import fr.moovie.tv.data.subtitles.StremioSubtitleCatalog
import fr.moovie.tv.data.subtitles.SubtitleFileStore
import fr.moovie.tv.ui.player.PlaybackId
import fr.moovie.tv.ui.player.parseMediaKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path

/**
 * Ce qui a empêché d'aboutir, en termes affichables.
 *
 * [DOWNLOAD_FAILED] est l'échec d'un téléchargement : un fichier vide, une URL
 * refusée, une réponse trop lourde.
 */
enum class SubtitleTrouble { NOTHING_FOUND, DOWNLOAD_FAILED }

data class PlayerSubtitlesState(
    val available: Boolean = false,
    val loading: Boolean = false,
    val candidates: List<SubtitleCandidate> = emptyList(),
    val active: SubtitleCandidate? = null,
    /** Identifiants déjà sur disque : les rejouer ne retélécharge rien. */
    val downloaded: Set<String> = emptySet(),
    val timing: SubtitleTiming = SubtitleTiming.None,
    val trouble: SubtitleTrouble? = null,
)

/**
 * Sous-titres en ligne, pendant la lecture.
 *
 * Deux principes gouvernent cette classe :
 *
 * **La recherche est automatique, le téléchargement jamais.** Sans liste il n'y
 * a rien à choisir ; télécharger n'arrive que sur une validation explicite.
 *
 * **Un sous-titre déjà téléchargé ne se retélécharge pas.** Le fichier d'origine est gardé
 * sur disque : changer de décalage, corriger la cadence ou revenir à ce
 * sous-titre après en avoir essayé un autre ne redéclenche aucun appel réseau.
 */
class PlayerSubtitlesViewModel : ViewModel() {

    private val settings = SettingsRepository()
    /** L'addon Stremio public : aucune clé, aucun compte. */
    private val catalog = StremioSubtitleCatalog()
    private val files = SubtitleFileStore()

    private val _state = MutableStateFlow(
        PlayerSubtitlesState(available = catalog.available),
    )
    val state: StateFlow<PlayerSubtitlesState> = _state.asStateFlow()

    /**
     * Fichier à donner au lecteur, déjà recalé. Null = aucun sous-titre externe.
     * L'écran l'observe et le transmet au contrôleur : le ViewModel n'a pas à
     * connaître le lecteur.
     */
    private val _file = MutableStateFlow<String?>(null)
    val file: StateFlow<String?> = _file.asStateFlow()

    private var mediaKey: String = ""
    private var original: Path? = null
    private var streamFps: Double? = null

    /**
     * Cherche les sous-titres du média en cours. [title] sert de repli quand la
     * clé de lecture ne porte pas d'identifiant TMDB exploitable. [episodeTitle]
     * est le titre de l'épisode tel qu'affiché par le lecteur (vide pour un
     * film) : il sert au classement, voir [rankSubtitles].
     */
    fun load(mediaKey: String, title: String, streamFps: Double, episodeTitle: String = "") = io {
        if (!_state.value.available) return@io
        this.mediaKey = mediaKey
        this.streamFps = streamFps.takeIf { it > 0 }

        val id = parseMediaKey(mediaKey) ?: return@io
        val media = if (id.isTv) {
            MediaRef.Episode(id.tmdbId, title, season = id.season, episode = id.episode)
        } else {
            MediaRef.Movie(id.tmdbId, title)
        }

        _state.value = _state.value.copy(loading = true, trouble = null)

        val languages = settings.subtitleLanguages.first()
        val found = catalog.search(media, languages)
        val titresEpisode = listOfNotNull(episodeTitle.takeIf { it.isNotBlank() }) +
            listOfNotNull(englishEpisodeTitle(id.takeIf { it.isTv }))
        _state.value = _state.value.copy(
            loading = false,
            candidates = rankSubtitles(
                candidates = found,
                preferredLanguages = languages,
                streamFps = this.streamFps,
                episodeTitles = titresEpisode,
            ),
            trouble = if (found.isEmpty()) SubtitleTrouble.NOTHING_FOUND else null,
            downloaded = files.storedIds(mediaKey, found),
        )
    }

    /**
     * Le titre de l'épisode en anglais, ou null (film, pas de clé TMDB, réseau).
     *
     * Le lecteur affiche le titre dans la langue de l'application, alors que les
     * releases sont nommées en anglais : « Le Corps des sacrifiés » ne se trouve
     * dans aucun nom de release, « Body of the Sanctioned » dans tous les bons.
     * Un échec ne coûte que le gain de classement, jamais la recherche.
     */
    private suspend fun englishEpisodeTitle(id: PlaybackId?): String? {
        if (id == null) return null
        val apiKey = settings.tmdbApiKey.first()
        if (apiKey.isBlank()) return null
        return try {
            TmdbRepository("en-US").season(apiKey, id.tmdbId, id.season)
                .episodes.firstOrNull { it.episodeNumber == id.episode }?.name?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Active un sous-titre. Ne télécharge que si le fichier n'a pas déjà été
     * récupéré pour ce média.
     */
    fun pick(candidate: SubtitleCandidate) = io {
        val cached = files.existing(mediaKey, candidate)
        val source = cached ?: run {
            _state.value = _state.value.copy(loading = true, trouble = null)
            val downloaded = catalog.download(candidate)
            if (downloaded == null) {
                _state.value = _state.value.copy(
                    loading = false,
                    trouble = SubtitleTrouble.DOWNLOAD_FAILED,
                )
                return@io
            }
            files.storeOriginal(mediaKey, candidate, downloaded.content)
        }

        original = source
        // La correction de cadence s'applique d'emblée : c'est la dérive qu'un
        // curseur de décalage ne rattrape pas, autant partir juste.
        val timing = timingFor(candidate.fps, streamFps)
        _state.value = _state.value.copy(
            active = candidate,
            timing = timing,
            loading = false,
            downloaded = files.storedIds(mediaKey, _state.value.candidates),
        )
        publish(timing)
    }

    /** Décale le sous-titre, en relatif. */
    fun nudge(deltaMs: Long) {
        val timing = _state.value.timing
        apply(timing.copy(offsetMs = timing.offsetMs + deltaMs))
    }

    /** Force la cadence d'origine du sous-titre, quand elle n'est pas déclarée. */
    fun assumeSubtitleFps(fps: Double) {
        apply(timingFor(fps, streamFps, _state.value.timing.offsetMs))
    }

    fun resetTiming() = apply(SubtitleTiming(offsetMs = 0, scale = 1.0))

    /** Retire le sous-titre externe sans oublier ce qui a été téléchargé. */
    fun clear() {
        original = null
        _state.value = _state.value.copy(active = null, timing = SubtitleTiming.None)
        _file.value = null
    }

    /** À la sortie du lecteur : les versions dérivées n'ont plus d'utilité. */
    fun onLeave() {
        files.clearDerived(mediaKey)
    }

    /**
     * Change de média **sans** quitter le lecteur.
     *
     * L'enchaînement d'épisodes garde désormais le lecteur monté — c'est tout
     * l'objet du remplacement sur place — et donc ce ViewModel avec lui. Sans
     * cette remise à zéro, l'épisode suivant héritait de trois choses du
     * précédent : les candidats déjà listés (le menu ne cherchait donc plus,
     * `load` ne s'exécutant que sur une liste vide), le fichier recalé publié
     * dans [file], et la clé de média sous laquelle les téléchargements se
     * rangent — un sous-titre téléchargé pour l'épisode 3 aurait été classé sous
     * l'épisode 2, et retéléchargé à la prochaine écoute.
     *
     * Les fichiers d'origine restent sur disque : c'est [onLeave] qui ne
     * balaie que les versions dérivées, et un sous-titre déjà téléchargé ne doit pas
     * disparaître parce qu'on a changé d'épisode.
     */
    fun reset() {
        onLeave()
        original = null
        mediaKey = ""
        streamFps = null
        // Même source que l'init. Non testé : le ViewModel construit ses dépôts réels.
        _state.value = PlayerSubtitlesState(available = catalog.available)
        _file.value = null
    }

    private fun apply(timing: SubtitleTiming) = io {
        if (original == null) return@io
        _state.value = _state.value.copy(timing = timing)
        publish(timing)
    }

    private fun publish(timing: SubtitleTiming) {
        val source = original ?: return
        // `okio.Path.toString()` rend le chemin natif de la plateforme, ce
        // que `File.absolutePath` rendait déjà : le lecteur reçoit la même
        // chaîne qu'avant.
        _file.value = files.retimed(source, timing).toString()
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch { withContext(dispatcherEs) { block() } }
    }
}
