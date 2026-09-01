package fr.moovie.tv.data.home

import kotlinx.serialization.Serializable

/**
 * Nature d'une rangée de l'accueil.
 *
 * **L'ordre de déclaration est la disposition par défaut** : c'est lui que voit
 * une installation qui n'a jamais rien réorganisé, et lui qu'on complète quand
 * une version ajoute une rangée. Le dire ici plutôt que dans une liste séparée
 * évite les deux sources de vérité qui divergent au premier ajout.
 *
 * [GENRE] est à part : elle ne décrit pas *une* rangée mais une famille, une par
 * genre épinglé, et n'a donc pas sa place dans la disposition par défaut.
 */
@Serializable
enum class HomeRowKind {
    /** Reprendre la lecture. */
    RESUME,

    /** À regarder plus tard. */
    WATCHLIST,

    /** « Parce que tu as regardé X ». */
    RECOMMENDATIONS,
    TRENDING_MOVIES,
    TRENDING_TV,
    TOP_MOVIES,

    /** Genre épinglé depuis le catalogue. Porte alors un [HomeLayoutEntry.genre]. */
    GENRE,
    ;

    companion object {
        /** Les rangées fournies par l'app, dans leur ordre d'origine. */
        val builtIn: List<HomeRowKind> get() = entries.filterNot { it == GENRE }
    }
}

/**
 * Genre épinglé sur l'accueil.
 *
 * [name] est stocké avec la clé, et non retrouvé à l'affichage : sans lui, le
 * titre de la rangée dépendrait d'un appel TMDB, et l'accueil afficherait une
 * rangée sans nom le temps de la réponse — voire rien du tout hors ligne.
 * C'est le même parti que [fr.moovie.tv.data.watch.WatchlistEntry], qui embarque
 * titre et affiche pour se dessiner dès la première image.
 */
@Serializable
data class PinnedGenre(
    val isTv: Boolean,
    val genreId: Int,
    val name: String,
) {
    /** Identifiant stable, indépendant du nom qui peut changer de langue. */
    val key: String get() = pinnedGenreKey(isTv, genreId)
}

/** La clé d'un genre, sans avoir à en fabriquer un pour la lire. */
fun pinnedGenreKey(isTv: Boolean, genreId: Int): String =
    "${if (isTv) "tv" else "movie"}:$genreId"

/**
 * Une entrée de la disposition de l'accueil.
 *
 * Le rang n'est **pas** un champ : c'est la place dans la liste. Un champ
 * `position` obligerait à renuméroter tout le monde à chaque déplacement, et
 * finirait par porter des trous et des doublons qu'il faudrait retrier au rendu.
 *
 * [visible] plutôt qu'une suppression pour les rangées intégrées : une rangée
 * retirée doit pouvoir revenir, et une rangée simplement absente du fichier est
 * indiscernable d'une rangée qu'une version ultérieure vient d'ajouter.
 */
@Serializable
data class HomeLayoutEntry(
    val kind: HomeRowKind,
    val genre: PinnedGenre? = null,
    val visible: Boolean = true,
) {
    /** Identifiant d'une entrée dans la disposition — sert d'ancre et de cible. */
    val id: String get() = genre?.let { "${HomeRowKind.GENRE.name}:${it.key}" } ?: kind.name

    companion object {
        fun of(genre: PinnedGenre) = HomeLayoutEntry(HomeRowKind.GENRE, genre)
    }
}

/** Ce qui est écrit dans le magasin. Enveloppé pour pouvoir gagner un champ. */
@Serializable
internal data class StoredLayout(val entries: List<HomeLayoutEntry> = emptyList())

/** Disposition d'une installation qui n'a jamais rien réorganisé. */
val defaultHomeLayout: List<HomeLayoutEntry>
    get() = HomeRowKind.builtIn.map { HomeLayoutEntry(it) }

/**
 * Complète une disposition lue du magasin.
 *
 * Deux cas, et un seul comportement raisonnable pour chacun :
 * - rien de stocké → la disposition par défaut, sinon l'accueil d'une
 *   installation existante se viderait à la mise à jour ;
 * - une rangée intégrée absente du fichier → **ajoutée en fin**. Elle ne peut
 *   venir que d'une version plus récente que celle qui a écrit le fichier : une
 *   rangée que l'utilisateur a retirée, elle, y figure avec `visible = false`.
 *
 * Les entrées de genre sans genre sont écartées : c'est un fichier abîmé, et une
 * rangée sans titre ni contenu ne vaut mieux que rien.
 */
fun mergeHomeLayout(stored: List<HomeLayoutEntry>?): List<HomeLayoutEntry> {
    if (stored.isNullOrEmpty()) return defaultHomeLayout

    val clean = stored
        .filterNot { it.kind == HomeRowKind.GENRE && it.genre == null }
        .distinctBy { it.id }
    val known = clean.mapTo(mutableSetOf()) { it.kind }
    val added = HomeRowKind.builtIn.filterNot { it in known }.map { HomeLayoutEntry(it) }

    return clean + added
}

/**
 * Fusionne la disposition d'une sauvegarde avec celle de l'appareil.
 *
 * L'ordre ne se fusionne pas : deux dispositions rangent les mêmes rangées
 * différemment, et il n'existe aucun ordre « moyen » qui ait du sens. Celui du
 * fichier l'emporte donc, comme pour les réglages.
 *
 * Ce qui se fusionne, ce sont les **genres épinglés** : ce sont du contenu, pas
 * une mise en page. Ceux que l'appareil a en propre se rajoutent en fin plutôt
 * que de disparaître — le mode fusion promet que rien n'est perdu.
 */
fun mergeHomeLayouts(
    current: List<HomeLayoutEntry>,
    incoming: List<HomeLayoutEntry>,
): List<HomeLayoutEntry> {
    val known = incoming.mapTo(mutableSetOf()) { it.id }
    val kept = current.filter { it.kind == HomeRowKind.GENRE && it.id !in known }

    return mergeHomeLayout(incoming + kept)
}

/**
 * Insère [entry] **avant** ou **après** l'ancre [anchorId].
 *
 * Ancre inconnue ou absente : on ajoute en fin. C'est le cas d'un premier
 * épinglage sans choix de position, et celui d'une ancre retirée entre
 * l'ouverture de la modale et la validation — mieux vaut en fin qu'échouer.
 *
 * Une entrée déjà présente est **déplacée**, pas dupliquée : l'utilisateur qui
 * réépingle un genre depuis le catalogue exprime un choix de position, pas une
 * envie de le voir deux fois.
 */
fun insertHomeEntry(
    layout: List<HomeLayoutEntry>,
    entry: HomeLayoutEntry,
    anchorId: String? = null,
    after: Boolean = true,
): List<HomeLayoutEntry> {
    val without = layout.filterNot { it.id == entry.id }
    val anchor = without.indexOfFirst { it.id == anchorId }
    if (anchorId == null || anchor < 0) return without + entry

    return without.toMutableList().apply { add(if (after) anchor + 1 else anchor, entry) }
}
