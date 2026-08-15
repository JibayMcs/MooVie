package fr.moovie.tv.ui.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.discovery.DiscoveryCard
import fr.moovie.tv.data.discovery.DiscoveryRepository
import fr.moovie.tv.data.discovery.DiscoveryState
import fr.moovie.tv.data.discovery.MoodAnswers
import fr.moovie.tv.data.discovery.MoodOption
import fr.moovie.tv.data.discovery.MoodRepository
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.currentTmdbLanguage
import fr.moovie.tv.data.tmdb.TmdbRepository
import fr.moovie.tv.data.watch.TitleMeta
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.data.watch.WatchlistEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * L'état de la page Découverte, et ce qu'on peut y faire.
 *
 * La page se recalcule quand la clé TMDB ou les réponses au questionnaire
 * changent, mais **pas** à chaque récolte : marquer « déjà vu » retire la carte
 * localement plutôt que de relancer une dizaine de requêtes. Les groupes sont
 * assez chers à bâtir pour que redistribuer la main à chaque appui soit
 * perceptible, et assez stables pour que ce ne soit pas nécessaire.
 */
class DiscoveryViewModel : ViewModel() {

    private val settings = SettingsRepository()
    private val watch = WatchProgressRepository()
    private val moodRepo = MoodRepository()
    private val repo = DiscoveryRepository(TmdbRepository(currentTmdbLanguage()), watch)

    private val _state = MutableStateFlow<DiscoveryState>(DiscoveryState.Loading)
    val state: StateFlow<DiscoveryState> = _state

    /** Cartes retirées depuis l'ouverture de la page, sans reconstruire les groupes. */
    private val _retirees = MutableStateFlow<Set<String>>(emptySet())
    val retirees: StateFlow<Set<String>> = _retirees

    val mood: StateFlow<MoodAnswers> = moodRepo.answers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MoodAnswers.EMPTY)

    val watchlistKeys: StateFlow<Set<String>> = watch.watchlist
        .map { list -> list.map { it.key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        viewModelScope.launch {
            combine(settings.tmdbApiKey, moodRepo.answers, ::Pair).collectLatest { (key, mood) ->
                _state.value = DiscoveryState.Loading
                _retirees.value = emptySet()
                _state.value = runCatching { repo.build(key, mood) }
                    .getOrDefault(DiscoveryState.ColdStart)
            }
        }
    }

    /**
     * Nombre de fois qu'on a demandé à rebattre les cartes.
     *
     * Il ne compte pas les chargements automatiques : ceux-là doivent rendre la
     * page telle qu'on l'a laissée. Seul un appui explicite sur « Redistribuer »
     * l'incrémente, et c'est lui qui fait changer ce que servent les recettes.
     */
    private var tirage = 0

    /** Redistribue : relit l'historique et redemande à TMDB, autrement. */
    fun reload() {
        viewModelScope.launch {
            _state.value = DiscoveryState.Loading
            _retirees.value = emptySet()
            tirage++
            val key = settings.tmdbApiKey.first()
            _state.value = runCatching { repo.build(key, mood.value, tirage) }
                .getOrDefault(DiscoveryState.ColdStart)
        }
    }

    fun answer(option: MoodOption) {
        viewModelScope.launch { moodRepo.save(mood.value.with(option)) }
    }

    fun clearMood() {
        viewModelScope.launch { moodRepo.clear() }
    }

    /**
     * « Déjà vu ».
     *
     * C'est par là que la page apprend ce qui a été regardé **avant**
     * l'application : l'historique ne connaît que ce qui a été lu dans Moo-vie,
     * et sans ce geste la découverte proposerait indéfiniment des films vus il
     * y a dix ans.
     *
     * Le titre est mémorisé **avant** d'être marqué : `setWatched` écrit une
     * ligne d'historique à partir des métadonnées connues, et sans elles la
     * ligne serait sans nom ni affiche.
     */
    fun markSeen(card: DiscoveryCard) {
        viewModelScope.launch {
            when (card) {
                is DiscoveryCard.Title -> {
                    watch.rememberTitle(card.key, TitleMeta(card.title, card.posterUrl))
                    watch.setWatched(card.key, true)
                }
                // Une saga n'a pas de clé de visionnage : marquer ses films un
                // par un inventerait un historique que personne n'a produit.
                // On la masque, ce qui est exactement ce que le geste demande.
                is DiscoveryCard.Saga -> repo.hide(card.key)
            }
            _retirees.value = _retirees.value + card.key
        }
    }

    /** « À voir » : la carte reste dans la main, marquée. */
    fun toggleWatchlist(card: DiscoveryCard) {
        viewModelScope.launch {
            val key = when (card) {
                is DiscoveryCard.Title -> card.key
                is DiscoveryCard.Saga -> card.next?.let { "movie:${it.id}" } ?: return@launch
            }
            if (key in watchlistKeys.value) {
                watch.removeFromWatchlist(key)
            } else {
                val (id, isTv, titre, affiche) = when (card) {
                    is DiscoveryCard.Title ->
                        Quad(card.tmdbId, card.isTv, card.title, card.posterUrl)
                    is DiscoveryCard.Saga ->
                        Quad(card.next!!.id, false, card.next.displayTitle, card.next.posterUrl())
                }
                watch.addToWatchlist(
                    WatchlistEntry(
                        key = key,
                        tmdbId = id,
                        isTv = isTv,
                        title = titre,
                        imageUrl = affiche,
                    ),
                )
            }
        }
    }

    private data class Quad(
        val id: Int,
        val isTv: Boolean,
        val titre: String,
        val affiche: String?,
    )
}
