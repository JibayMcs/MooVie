package fr.moovie.tv.data.sources

/**
 * Un provider trouve, pour un titre (résolu depuis TMDB), la liste des liens
 * d'embed d'hébergeurs (voe, uqload…), groupés implicitement par langue via
 * EmbedLink.language. Ces liens sont ensuite résolus en flux jouable par les
 * SourceExtractor. Portage des routes de API/Mainapi/routes.
 */
interface SourceProvider {
    val name: String

    /**
     * Liens d'embed pour un film.
     *
     * `tmdbId` est l'identifiant TMDB du titre. Les providers qui scrapent un
     * site par son moteur de recherche l'ignorent et travaillent sur le titre ;
     * ceux qui indexent par TMDB (cinestream, frembed, j1f, cpasmal…) le
     * prennent, ce qui supprime le rapprochement par titre — et avec lui la
     * confusion « Dune » / « Dune Dreams ».
     */
    suspend fun movieSources(tmdbId: Int, title: String, year: String?): List<EmbedLink>

    /** Liens d'embed pour un épisode précis d'une série. */
    suspend fun tvSources(
        tmdbId: Int,
        title: String,
        year: String?,
        season: Int,
        episode: Int,
    ): List<EmbedLink>
}
