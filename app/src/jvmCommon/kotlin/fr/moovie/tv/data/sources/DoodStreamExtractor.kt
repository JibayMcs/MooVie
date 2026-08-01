package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.getBody
import fr.moovie.tv.core.sources.port.SourceExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracteur DoodStream (dood.* / d000d.com…) — port de doodstream_extract_handler
 * (API/proxiesembed/server.py). Récupère la page d'embed, extrait le lien
 * `/pass_md5/…/token`, appelle ce endpoint pour obtenir l'URL de base, puis
 * construit l'URL finale du mp4 (base + 10 chars aléatoires + token + expiry).
 */
class DoodStreamExtractor(private val http: HttpGateway) : SourceExtractor {

    override val hoster = "dood"

    override fun canHandle(url: String): Boolean =
        DOOD_HOST.containsMatchIn(url)

    override suspend fun extract(link: EmbedLink): PlayableStream? = withContext(Dispatchers.IO) {
        runCatching {
            val domain = DOMAIN.find(link.url)?.groupValues?.get(1) ?: return@runCatching null

            val page = get(link.url, referer = "https://d0000d.com/") ?: return@runCatching null
            val match = PASS_MD5.find(page) ?: return@runCatching null
            val passPath = match.value                 // /pass_md5/xxx/token
            val token = match.groupValues[1]           // token

            val base = get("$domain$passPath", referer = domain)?.trim().orEmpty()
            if (base.isBlank()) return@runCatching null

            val rnd = (1..10).map { ALPHANUM.random() }.joinToString("")
            val expiry = System.currentTimeMillis()
            val videoUrl = "$base$rnd?token=$token&expiry=$expiry"

            PlayableStream(
                url = videoUrl,
                format = StreamFormat.MP4,
                headers = mapOf("Referer" to domain, "User-Agent" to Ua.BROWSER),
                language = link.language,
            )
        }.getOrNull()
    }

    private suspend fun get(url: String, referer: String): String? =
        http.getBody(url, mapOf("User-Agent" to Ua.BROWSER, "Referer" to referer))

    companion object {
        /**
         * DoodStream sert aussi ses embeds depuis des domaines qui ne portent pas
         * son nom. `playmogo.com` et `dsvplay.com` (qui y redirige) rendent une
         * page titrée « … - DoodStream » : sans eux dans ce motif, 11 des 109
         * embeds relevés restaient sans extracteur alors que le code existait.
         *
         * Ces deux-là répondent 403 à curl mais 200 à notre client : leur absence
         * n'était pas un blocage anti-bot, seulement un domaine non reconnu.
         *
         * ⚠️ Ils ne lisent pourtant pas, et **ce n'est pas un bug d'ici** :
         * l'algorithme ci-dessus est conforme au `makePlay()` de leur propre page
         * (10 caractères aléatoires + `?token=…&expiry=` + `Date.now()`), et cinq
         * variantes de construction d'URL ont été essayées. Leur endpoint
         * `/pass_md5/` renvoie une base sur `cloudatacdn.com`, **domaine parqué**
         * qui sert une page d'annonces. Rien à corriger tant qu'ils n'ont pas
         * remis un CDN en face ; le filtre de type de contenu de la sonde écarte
         * proprement le lien en attendant. Les motifs restent en place pour que
         * ça reparte tout seul le jour où leur CDN revit.
         */
        private val DOOD_HOST = Regex(
            """dood|d0{3,4}d|dooood|ds2play|doods|dsvplay|playmogo""",
            RegexOption.IGNORE_CASE,
        )
        private val DOMAIN = Regex("""^(https?://[^/]+)""")
        private val PASS_MD5 = Regex("""/pass_md5/[\w-]+/([\w-]+)""")
        private val ALPHANUM = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    }
}
