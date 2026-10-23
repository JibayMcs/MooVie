package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.SourceProvider
import fr.moovie.tv.core.sources.port.getBody
import fr.moovie.tv.shared.supportsAudioTrackSelection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Provider PurStream - un master HLS qui porte **VF et VO en pistes audio**.
 *
 * ### La chaîne
 *
 * ```
 * /api/v1/search-bar/search/{titre}               -> candidats { id, type }
 * /api/v1/stream/{id}                             -> URL du master (film)
 * /api/v1/stream/{id}/episode?season=S&episode=E  -> idem (série)
 * master.m3u8                                     -> EXT-X-MEDIA TYPE=AUDIO fr / en
 * ```
 *
 * ### Pourquoi la confirmation par l'id TMDB
 *
 * La recherche se fait **par titre** : deux films homonymes, ou un remake, et le
 * premier résultat est le mauvais. Le chemin du master contient l'id TMDB suivi
 * d'un suffixe aléatoire (`/movies/603-h0ts7/`, `/tv/1396-yh3cz/`) : on ne s'en
 * sert pas pour construire l'URL, seulement pour **confirmer** qu'on tient le
 * bon titre. Sans confirmation, liste vide - mieux vaut aucune source qu'un autre
 * film.
 *
 * ### Une langue par lien
 *
 * Le modèle n'a qu'une langue par lien, alors que ce master en réunit deux. On
 * émet donc un lien **par piste réellement déclarée**, avec un fragment
 * (`#audio=fr` / `#audio=en`) : la sélection exclut par URL, et deux liens
 * identiques seraient pris pour des doublons. [PurStreamExtractor] retire le
 * fragment et passe la langue au lecteur. Aucun VOSTFR n'est déduit.
 *
 * `en` n'est pas « VO » par principe : une piste n'est la VO que si elle est
 * dans la langue originale TMDB du titre ([MediaRef.originalLanguage]).
 * L'anglais d'un film français est un doublage ; origine inconnue, on ne
 * l'émet pas.
 *
 * Sans sélection de piste côté lecteur ([audioTrackSelection] faux, iOS), le
 * fragment n'aurait aucun effet : on n'émet alors que le lien de la piste
 * `DEFAULT` du master, sans fragment. Dans les deux cas, un master sans piste
 * étiquetable rend un lien nu, sans langue, plutôt que rien.
 */
class PurStreamProvider(
    private val http: HttpGateway,
    private val audioTrackSelection: Boolean = supportsAudioTrackSelection,
) : SourceProvider {

    override val name = "purstream"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> {
        val base = DomainBook.base(http, name, API_BASE)
        val wantedType = if (media is MediaRef.Movie) "movie" else "tv"

        val search = http.getBody(
            "$base/api/v1/search-bar/search/${encode(media.title)}",
            headers(),
        )?.let(::parse) ?: return emptyList()

        for (id in candidatesOf(search, wantedType).take(MAX_CANDIDATES)) {
            val route = when (media) {
                is MediaRef.Movie -> "$base/api/v1/stream/$id"
                is MediaRef.Episode ->
                    "$base/api/v1/stream/$id/episode?season=${media.season}&episode=${media.episode}"
            }
            val stream = http.getBody(route, headers())?.let(::parse) ?: continue
            val master = masterUrlsOf(stream).firstOrNull { confirms(it, media) } ?: continue

            val manifest = http.getBody(master, mapOf("User-Agent" to Ua.BROWSER)) ?: continue
            val original = media.originalLanguage
            // Aucune piste étiquetable (audio multiplexé, ou seulement des
            // langues qu'on ne sait pas nommer) : le flux se joue tel quel, sans
            // langue inventée ni fragment. Même règle avec ou sans sélection.
            val nu = listOf(EmbedLink(url = master, hoster = HOSTER, language = null))
            if (!audioTrackSelection) {
                val label = defaultAudioLanguageOf(manifest)?.let { labelOf(it, original) } ?: return nu
                return listOf(EmbedLink(url = master, hoster = HOSTER, language = label))
            }
            val links = audioLanguagesOf(manifest).mapNotNull { lang ->
                val label = labelOf(lang, original) ?: return@mapNotNull null
                EmbedLink(url = "$master#audio=$lang", hoster = HOSTER, language = label)
            }
            return links.ifEmpty { nu }
        }
        return emptyList()
    }

    private fun parse(body: String): JsonElement? =
        runCatching { json.parseToJsonElement(body) }.getOrNull()

    private fun headers() = mapOf(
        "User-Agent" to Ua.BROWSER,
        "Accept" to "application/json,text/plain,*/*",
    )

    companion object {
        const val API_BASE = "https://api.purstream.id"
        const val HOSTER = "purstream"
        private const val MAX_CANDIDATES = 5
        private val LANGUAGE = Regex("LANGUAGE=\"([^\"]+)\"")

        /**
         * Étiquette d'une piste : `fr` est la VF ; une piste n'est la VO que si
         * elle est dans la langue originale TMDB du titre. Toute autre piste
         * (l'anglais d'un film français, ou tout ce qui n'est pas `fr` quand
         * l'origine est inconnue) est un doublage qu'on ne sait pas nommer : null.
         */
        internal fun labelOf(lang: String, originalLanguage: String?): String? {
            val code = lang.lowercase().take(2)
            val original = originalLanguage?.trim()?.lowercase()?.take(2)?.takeIf { it.isNotEmpty() }
            return when {
                code == "fr" -> "VF"
                code == original -> "VO"
                else -> null
            }
        }

        /**
         * Vrai si le master appartient au titre TMDB demandé : `/movies/603-`
         * ou `/tv/1396-`. Le tiret final évite que 60 valide `/movies/603-`.
         */
        fun confirms(masterUrl: String, media: MediaRef): Boolean {
            val dir = if (media is MediaRef.Movie) "movies" else "tv"
            return masterUrl.contains("/$dir/${media.tmdbId}-")
        }

        /** Ids des résultats du bon type, dans l'ordre. Tolère l'imbrication. */
        internal fun candidatesOf(root: JsonElement, type: String): List<Long> {
            val out = LinkedHashSet<Long>()
            walk(root) { o ->
                val id = (o["id"] as? JsonPrimitive)?.content?.toLongOrNull()
                val t = (o["type"] as? JsonPrimitive)?.content
                if (id != null && t.equals(type, ignoreCase = true)) out += id
            }
            return out.toList()
        }

        /** Toute URL de master dans la réponse, où qu'elle soit nichée. */
        internal fun masterUrlsOf(root: JsonElement): List<String> {
            val out = LinkedHashSet<String>()
            fun visit(e: JsonElement) {
                when (e) {
                    is JsonPrimitive ->
                        if (e.isString && e.content.startsWith("http") &&
                            e.content.substringBefore('?').endsWith(".m3u8")
                        ) out += e.content
                    is JsonObject -> e.values.forEach(::visit)
                    is JsonArray -> e.forEach(::visit)
                }
            }
            visit(root)
            return out.toList()
        }

        /** Codes de langue (2 lettres) des `EXT-X-MEDIA:TYPE=AUDIO` du master. */
        internal fun audioLanguagesOf(master: String): List<String> =
            master.lineSequence()
                .filter { it.startsWith("#EXT-X-MEDIA:") && it.contains("TYPE=AUDIO") }
                .mapNotNull { LANGUAGE.find(it)?.groupValues?.get(1) }
                .map { it.lowercase().take(2) }
                .distinct()
                .toList()

        /**
         * Langue de la piste `DEFAULT=YES` du master, celle que le lecteur joue
         * sans qu'on lui demande rien. Sans piste marquée, la première déclarée.
         */
        internal fun defaultAudioLanguageOf(master: String): String? {
            val pistes = master.lineSequence()
                .filter { it.startsWith("#EXT-X-MEDIA:") && it.contains("TYPE=AUDIO") }
                .toList()
            val piste = pistes.firstOrNull { it.contains("DEFAULT=YES") } ?: pistes.firstOrNull()
            return piste?.let { LANGUAGE.find(it)?.groupValues?.get(1) }?.lowercase()?.take(2)
        }

        private fun walk(e: JsonElement, onObject: (JsonObject) -> Unit) {
            when (e) {
                is JsonObject -> { onObject(e); e.values.forEach { walk(it, onObject) } }
                is JsonArray -> e.forEach { walk(it, onObject) }
                else -> Unit
            }
        }

        private fun encode(s: String): String = buildString {
            for (b in s.encodeToByteArray()) {
                val c = (b.toInt() and 0xFF).toChar()
                if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-_.~") append(c)
                else append('%').append(c.code.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}
