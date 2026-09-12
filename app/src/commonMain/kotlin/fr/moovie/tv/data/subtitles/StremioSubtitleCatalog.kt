package fr.moovie.tv.data.subtitles

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.subtitles.model.DownloadedSubtitle
import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.core.subtitles.model.SubtitleQuota
import fr.moovie.tv.core.subtitles.port.SubtitleCatalog
import fr.moovie.tv.data.net.clientRest
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.tmdb.TmdbRepository
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Catalogue de sous-titres servi par un **addon Stremio**, sans aucune clé.
 *
 * ## Pourquoi il existe
 *
 * L'adaptateur OpenSubtitles a besoin d'une clé qui identifie *l'application* et
 * s'injecte à la compilation. Une construction depuis les sources n'en a pas :
 * `available` est alors faux, la recherche rend une liste vide sans appeler le
 * réseau, et le lecteur affiche « aucun sous-titre trouvé pour ce titre » —
 * y compris sur un film qui en compte des milliers. Le message est exact du
 * point de vue du code et trompeur du point de vue de l'utilisateur.
 *
 * L'addon `opensubtitles-v3.strem.io` sert **le même fonds** en accès libre :
 * pas de clé, pas de compte, pas de quota de cinq par jour. Sur un fork qui
 * parle déjà le protocole des addons et qui sait traduire un identifiant TMDB en
 * IMDB, c'est une pièce de plus, pas une architecture de plus.
 *
 * ## Ce qu'il ne sait pas faire, et qu'il faut savoir
 *
 * **L'appariement se fait sur le seul identifiant IMDB.** Mesuré par
 * `stremio-mpv` : le hash du fichier est ignoré par cet addon — soixante
 * sous-titres identiques avec et sans. Un sous-titre peut donc être calé pour
 * une autre version que celle qu'on regarde, et décaler de plusieurs secondes.
 * Le réglage de décalage du lecteur reste la parade, et c'est la raison de
 * rendre **plusieurs candidats par langue** plutôt que le meilleur.
 *
 * Aucun quota : [quota] rend toujours `Unknown`, ce qui est la vérité et non un
 * aveu d'ignorance.
 */
class StremioSubtitleCatalog(
    private val addonUrl: String = ADDON_PAR_DEFAUT,
    private val settings: SettingsRepository = SettingsRepository(),
    private val tmdb: TmdbRepository = TmdbRepository(),
    /** Clé TMDB imposée. Couture pour les sondes ; null en fonctionnement normal. */
    private val cleTmdb: String? = null,
) : SubtitleCatalog {

    override val name: String = "Stremio"

    override val available: Boolean get() = addonUrl.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Identifiant du candidat vers l'URL de son fichier, rempli à la recherche.
     * Le téléchargement ne peut pas la redécouvrir : l'addon ne rend ses URL
     * qu'en bloc, pour un titre entier.
     */
    private val liens = mutableMapOf<String, String>()

    override suspend fun search(
        media: MediaRef,
        languages: List<String>,
    ): List<SubtitleCandidate> {
        if (!available || languages.isEmpty()) return emptyList()

        val apiKey = cleTmdb ?: settings.tmdbApiKey.first()
        val isTv = media is MediaRef.Episode
        val imdb = tmdb.imdbId(apiKey, isTv, media.tmdbId) ?: return emptyList()
        val (type, id) = when (media) {
            is MediaRef.Movie -> "movie" to imdb
            is MediaRef.Episode -> "series" to "$imdb:${media.season}:${media.episode}"
        }

        val voulues = languages.map { it.lowercase() }.toSet()
        val body = runCatching {
            clientRest.get("${addonUrl.trimEnd('/')}/subtitles/$type/$id.json") {
                header("User-Agent", UA)
            }.body<String>()
        }.getOrNull() ?: return emptyList()

        val entrees = runCatching {
            json.parseToJsonElement(body).jsonObject["subtitles"]?.jsonArray
        }.getOrNull() ?: return emptyList()

        val vus = mutableSetOf<String>()
        liens.clear()
        return entrees.mapNotNull { element ->
            val o = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val url = o["url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            if (!vus.add(url)) return@mapNotNull null

            val langue = codeCourt(o["lang"]?.jsonPrimitive?.content.orEmpty())
            if (langue !in voulues) return@mapNotNull null

            // L'identifiant de l'addon plutôt que l'URL, et l'URL gardée ici.
            // Le port autorise n'importe quelle forme, mais le magasin de
            // fichiers en fait un nom de fichier : une URL y devenait un chemin
            // impossible. Un identifiant court est aussi ce qui s'affiche à
            // l'écran à côté de la langue.
            val idSousTitre = o["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: url.hashCode().toUInt().toString()
            liens[idSousTitre] = url

            SubtitleCandidate(
                fileId = idSousTitre,
                language = langue,
            )
        }
    }

    override suspend fun download(candidate: SubtitleCandidate): DownloadedSubtitle? {
        val url = liens[candidate.fileId] ?: return null
        val octets = runCatching {
            clientRest.get(url) { header("User-Agent", UA) }.body<ByteArray>()
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null

        return DownloadedSubtitle(
            candidate = candidate,
            content = enUtf8(octets),
            fileName = url.substringAfterLast('/').substringBefore('?'),
        )
    }

    /** Pas de quota du tout chez cet addon : l'inconnu serait un mensonge poli. */
    override suspend fun quota(): SubtitleQuota = SubtitleQuota.Unknown

    internal companion object {

        /**
         * Addon public d'OpenSubtitles pour Stremio. **Aucune clé, aucun compte,
         * aucun quota** — c'est ce qui permet de le donner en valeur par défaut
         * plutôt que d'en faire un réglage de plus.
         */
        const val ADDON_PAR_DEFAUT = "https://opensubtitles-v3.strem.io"

        const val UA = "Moo-vie"

        /**
         * Décodage **en connaissance de cause**, et non `toString(UTF_8)`.
         *
         * Ces fichiers viennent de contributeurs et d'époques diverses : une
         * partie est en cp1252. Les lire en UTF-8 ne lève pas, ça remplace
         * silencieusement chaque accent par un losange — le défaut se voit à
         * l'écran et jamais dans les journaux. On essaie donc les encodages dans
         * l'ordre et on garde le premier qui tient.
         */
        fun enUtf8(octets: ByteArray): String {
            val sansBom =
                if (octets.size >= 3 && octets[0] == 0xEF.toByte() &&
                    octets[1] == 0xBB.toByte() && octets[2] == 0xBF.toByte()
                ) {
                    octets.copyOfRange(3, octets.size)
                } else {
                    octets
                }
            val utf8 = sansBom.decodeToString(throwOnInvalidSequence = false)
            // U+FFFD signale que le décodage a substitué : le texte n'était pas
            // de l'UTF-8, et cp1252 est le candidat suivant le plus probable.
            return if ('�' !in utf8) utf8 else cp1252(sansBom)
        }

        /**
         * cp1252 octet par octet. La plage 0x80-0x9F est ce qui le distingue de
         * latin-1, et c'est précisément là que vivent l'apostrophe typographique
         * et les guillemets français — les caractères qui cassent en premier.
         */
        fun cp1252(octets: ByteArray): String {
            val hauts = "€‚ƒ„…†‡ˆ‰Š‹ŒŽ''\"\"•–—˜™š›œžŸ"
            val sb = StringBuilder(octets.size)
            for (b in octets) {
                val v = b.toInt() and 0xFF
                sb.append(if (v in 0x80..0x9F) hauts[v - 0x80] else v.toChar())
            }
            return sb.toString()
        }

        /**
         * ISO-639-2 vers ISO-639-1, parce que les deux mondes ne parlent pas le
         * même : l'addon annonce « fre », les réglages de l'app disent « fr ».
         * Un code déjà court traverse sans modification, un code inconnu aussi —
         * on ne le laissera simplement pas passer le filtre des langues voulues.
         */
        fun codeCourt(lang: String): String {
            val l = lang.lowercase().trim()
            return when (l) {
                "fre", "fra" -> "fr"
                "eng" -> "en"
                "spa" -> "es"
                "ger", "deu" -> "de"
                "ita" -> "it"
                "por" -> "pt"
                "dut", "nld" -> "nl"
                "rus" -> "ru"
                "ara" -> "ar"
                "jpn" -> "ja"
                "chi", "zho" -> "zh"
                else -> l
            }
        }
    }
}
