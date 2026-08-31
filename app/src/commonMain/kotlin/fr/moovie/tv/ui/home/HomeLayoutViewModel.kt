package fr.moovie.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.home.HomeLayoutEntry
import fr.moovie.tv.data.home.HomeLayoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Écran de réorganisation de l'accueil (réglages).
 *
 * Aucun état local : la liste affichée est **celle du magasin**. Garder une copie
 * réordonnée en mémoire et l'écrire à la sortie aurait rendu l'écran divergent de
 * l'accueil pendant tout le temps de l'édition — et perdu les changements si on
 * quitte par la touche Retour, qui est la sortie normale sur une télécommande.
 */
class HomeLayoutViewModel : ViewModel() {

    private val repo = HomeLayoutRepository()

    val layout: StateFlow<List<HomeLayoutEntry>> = repo.layout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun moveUp(id: String) = launch { repo.move(id, -1) }

    fun moveDown(id: String) = launch { repo.move(id, +1) }

    fun setVisible(id: String, visible: Boolean) = launch { repo.setVisible(id, visible) }

    /** Retire un genre épinglé. Les rangées intégrées, elles, se masquent. */
    fun unpin(isTv: Boolean, genreId: Int) = launch { repo.unpin(isTv, genreId) }

    fun reset() = launch { repo.resetToDefault() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
