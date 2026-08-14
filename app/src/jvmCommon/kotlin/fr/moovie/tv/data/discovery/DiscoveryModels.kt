package fr.moovie.tv.data.discovery

import fr.moovie.tv.data.tmdb.TmdbItem

/**
 * Une carte de la page Découverte : un titre, ou une saga entière.
 *
 * Deux formes seulement, et c'est voulu. Une saga n'est pas un titre avec un
 * champ en plus : elle se dessine autrement (une pile), elle s'ouvre autrement
 * (elle enchaîne comme une série) et elle porte une progression. La distinguer
 * dans le modèle évite les `if (estUneSaga)` disséminés dans l'écran.
 */
sealed interface DiscoveryCard {

    /** Ce qui identifie la carte dans les listes « déjà vu » et « à voir ». */
    val key: String

    val title: String
    val posterUrl: String?

    /**
     * Un titre, avec ce qu'il faut pour le dessiner et rien de plus.
     *
     * Volontairement **pas** un [TmdbItem] enveloppé : la moitié de ces cartes
     * ne vient pas de TMDB mais de l'historique local, qui connaît déjà le nom
     * et l'affiche. Les obliger à passer par une requête pour se dessiner
     * aurait été payer un aller-retour réseau pour une donnée qu'on possède.
     */
    data class Title(
        val tmdbId: Int,
        val isTv: Boolean,
        override val title: String,
        override val posterUrl: String?,
        val year: String? = null,
        val rating: Double = 0.0,
        val votes: Int = 0,
    ) : DiscoveryCard {
        override val key: String get() = if (isTv) "tv:$tmdbId" else "movie:$tmdbId"

        companion object {
            fun of(item: TmdbItem) = Title(
                tmdbId = item.id,
                isTv = item.isTv,
                title = item.displayTitle,
                posterUrl = item.posterUrl(),
                year = item.year,
                rating = item.voteAverage,
                votes = item.voteCount,
            )
        }
    }

    /**
     * Une saga TMDB, avec ce qui en a déjà été vu.
     *
     * [next] est le premier film non vu dans l'ordre de sortie : c'est lui
     * qu'ouvre la carte. Sans lui, une pile ne mènerait nulle part et il
     * faudrait un écran intermédiaire pour choisir — alors que la question
     * « lequel ensuite » a une seule réponse raisonnable.
     */
    data class Saga(
        val collectionId: Int,
        val name: String,
        val poster: String?,
        val total: Int,
        val seen: Int,
        val next: TmdbItem?,
    ) : DiscoveryCard {
        override val key: String get() = "collection:$collectionId"
        override val title: String get() = name
        override val posterUrl: String? get() = poster
        val progress: Float get() = if (total <= 0) 0f else seen.toFloat() / total
    }
}

/**
 * Les recettes de groupes.
 *
 * **Chacune se calcule pour n'importe qui**, et disparaît quand elle n'a rien à
 * dire. C'est la contrainte qui a fait jeter les premières idées : « le
 * réalisateur de deux films que vous avez notés » n'était pas une catégorie
 * mais une trouvaille, impossible à anticiper, donc une page où l'on ne revient
 * pas.
 */
enum class DiscoveryKind {
    /**
     * Ce que plusieurs de vos titres terminés désignent en commun.
     *
     * Le classement se fait au **recoupement** : un film que trois de vos films
     * recommandent n'est pas de même nature qu'un film recommandé par un seul.
     * L'ancienne rangée de l'accueil ne partait que du dernier titre vu, si
     * bien qu'une comédie un soir faisait basculer toute la page.
     */
    RECOUPEMENT,

    /** Vu il y a longtemps, jamais rouvert depuis. */
    REVOIR,

    /** Vos sagas commencées, et le film qui vient ensuite. */
    SAGAS,

    /**
     * Note haute, peu de votes.
     *
     * C'est le seul groupe qu'un tri par popularité ne peut pas produire, et
     * c'est celui qui justifie le mot « pépite ».
     */
    PEPITES,

    /** Ce que le questionnaire a demandé, quand il a été répondu. */
    HUMEUR,
}

/**
 * Un groupe affiché : une phrase, une teinte, une main de cartes.
 *
 * [seeds] porte les titres qui ont produit le groupe, pour que la phrase puisse
 * les nommer (« parce que vous avez vu X, Y et Z »). C'est ce qui fait la
 * différence entre une recommandation et une recommandation qu'on comprend.
 */
data class DiscoveryGroup(
    val kind: DiscoveryKind,
    val cards: List<DiscoveryCard>,
    val seeds: List<String> = emptyList(),
)

/** L'état de la page, du chargement au contenu. */
sealed interface DiscoveryState {
    data object Loading : DiscoveryState
    data object NeedsKey : DiscoveryState

    /**
     * Rien à proposer : ni historique, ni réponse au questionnaire.
     *
     * Un profil neuf tombe ici, et c'est le seul endroit où le questionnaire
     * n'est pas facultatif — sans lui la page n'aurait littéralement rien à
     * montrer.
     */
    data object ColdStart : DiscoveryState

    data class Ready(val groups: List<DiscoveryGroup>) : DiscoveryState
}
