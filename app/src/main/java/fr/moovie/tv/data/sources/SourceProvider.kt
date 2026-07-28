package fr.moovie.tv.data.sources

/**
 * Un provider trouve, pour un titre (résolu depuis TMDB), la liste des liens
 * d'embed d'hébergeurs (voe, uqload…), groupés implicitement par langue via
 * EmbedLink.language. Ces liens sont ensuite résolus en flux jouable par les
 * SourceExtractor. Portage des routes de API/Mainapi/routes.
 */
interface SourceProvider {
    val name: String

    /** Liens d'embed pour un film. */
    suspend fun movieSources(title: String, year: String?): List<EmbedLink>

    /** Liens d'embed pour un épisode précis d'une série. */
    suspend fun tvSources(title: String, year: String?, season: Int, episode: Int): List<EmbedLink>
}
