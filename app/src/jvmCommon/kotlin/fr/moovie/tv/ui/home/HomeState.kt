package fr.moovie.tv.ui.home

import fr.moovie.tv.data.home.HomeRowKind
import fr.moovie.tv.data.tmdb.TmdbItem
import fr.moovie.tv.ui.catalog.CatalogSelection

/**
 * Rangée d'affiches de l'accueil.
 *
 * [open] est le genre que rouvre la carte « En voir plus » posée en fin de
 * rangée. **Null quand il n'y a rien de comparable à ouvrir** : les tendances et
 * les recommandations ne correspondent à aucune page du catalogue, qui est rangé
 * par genre — y renvoyer serait mentir sur la destination. Seule une rangée
 * épinglée sait exactement où elle mène, puisqu'elle *vient* de là.
 */
data class HomeRow(
    /** Celui de l'entrée de disposition dont elle est le rendu. */
    val id: String,
    val title: String,
    val items: List<TmdbItem>,
    val open: CatalogSelection? = null,
)

/**
 * Un créneau de l'accueil, dans l'ordre voulu par l'utilisateur.
 *
 * « Reprendre » et « Ma liste » ne sont pas des rangées d'affiches comme les
 * autres — elles ont leurs propres cartes, leurs propres menus — mais elles
 * occupent **le même ordre**. Sans ça, « avant / après telle catégorie » ne
 * pourrait pas les désigner, et elles resteraient clouées en tête.
 *
 * Leur contenu ne passe pas par ici : il vient de flux à part, que l'écran
 * observe déjà. Le créneau ne dit que « la voilà, à cette place ».
 */
sealed interface HomeSlot {
    val id: String

    data object Resume : HomeSlot {
        override val id get() = HomeRowKind.RESUME.name
    }

    data object Watchlist : HomeSlot {
        override val id get() = HomeRowKind.WATCHLIST.name
    }

    data class Catalog(val row: HomeRow) : HomeSlot {
        override val id get() = row.id
    }
}

sealed interface HomeState {
    data object Loading : HomeState
    data class Ready(val slots: List<HomeSlot>) : HomeState
    data class NeedsApiKey(val reason: String) : HomeState
}
