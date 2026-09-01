package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.getBody
import fr.moovie.tv.core.sources.port.SourceProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Provider vidapi — **le premier catalogue en version originale** de l'app.
 *
 * Tous les autres sont francophones : deux d'entre eux (cinestream, animesama)
 * ne savent produire aucun « VO » par construction, et choisir VO dans les
 * réglages revenait donc à désactiver la lecture. Un public anglophone n'avait
 * tout simplement rien à regarder.
 *
 * Chaîne, lue dans le bundle du lecteur de vidsrc.pm (jamais exécuté), qui la
 * construit dans `buildStreamApiUrl()` :
 *
 *   `https://streamdata.vaplayer.ru/api.php?tmdb={id}&type=movie`
 *   `…?tmdb={id}&type=tv&season={S}&episode={E}`
 *       → `{ data: { file_name, stream_urls[] } }`
 *
 * Trois raisons d'avoir retenu celui-ci parmi les agrégateurs anglophones
 * essayés (2embed, vidsrc.to, embed.su…) :
 *
 *  1. **Indexé par ID TMDB**, donc aucun rapprochement par titre — insensible à
 *     la langue de l'interface, contrairement aux catalogues FR.
 *  2. **Sans jeton ni CAPTCHA** : les autres finissent sur un Turnstile
 *     Cloudflare (vidsrc → cloudnestra) ou sur une page vide que seul du JS
 *     remplit (2embed).
 *  3. Il rend des **m3u8 directs**, déjà en plusieurs miroirs : pas d'hébergeur
 *     à extraire, et la cascade a de quoi basculer si l'un tombe.
 */
class VidapiProvider(private val http: HttpGateway) : SourceProvider {

    override val name = "vidapi"

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun sourcesFor(media: MediaRef): List<EmbedLink> {
        val query = when (media) {
            is MediaRef.Movie -> "tmdb=${media.tmdbId}&type=movie"
            is MediaRef.Episode ->
                "tmdb=${media.tmdbId}&type=tv&season=${media.season}&episode=${media.episode}"
        }

        val body = http.getBody("$API?$query", headers()) ?: return emptyList()
        val data = runCatching {
            json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
        }.getOrNull() ?: return emptyList()

        val urls = runCatching {
            data["stream_urls"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
        }.getOrNull().orEmpty().distinct()
        if (urls.isEmpty()) return emptyList()

        return urls.mapIndexed { index, url ->
            EmbedLink(
                url = url,
                // Exception assumée à la règle « l'hébergeur se déduit de l'URL » :
                // ces miroirs tournent sur des domaines jetables et sans rapport
                // (« remoteconsultinggroup.site »). Les afficher tels quels ne
                // dirait rien à personne ; le catalogue, lui, est stable.
                hoster = name,
                language = ORIGINAL,
                // Miroirs équivalents : sans rang, la liste montrerait trois
                // lignes identiques et le choix se ferait à l'aveugle. On s'en
                // tient au rang — la qualité, l'app la mesure elle-même sur la
                // playlist et l'affiche déjà à côté.
                variant = mirrorLabel(index, urls.size),
            )
        }
    }

    private fun headers() = mapOf(
        "User-Agent" to Ua.BROWSER,
        "Accept" to "application/json,text/plain,*/*",
        "Referer" to "$PLAYER_ORIGIN/",
    )

    companion object {
        const val API = "https://streamdata.vaplayer.ru/api.php"

        /** Origine du lecteur qui appelle l'API ; sert de Referer. */
        const val PLAYER_ORIGIN = "https://nextgencloudfabric.com"

        /**
         * Ce catalogue ne sert que la piste d'origine — les noms de release le
         * confirment, et le lecteur amont s'annonce en `subDub=sub`. On étiquette
         * donc en VO, ce qui le rend éligible au repli VO/VOSTFR de la cascade.
         */
        private const val ORIGINAL = "VO"

        /** Null quand il n'y a rien à départager : un rang seul n'apprend rien. */
        fun mirrorLabel(index: Int, total: Int): String? =
            if (total <= 1) null else "miroir ${index + 1}"
    }
}
