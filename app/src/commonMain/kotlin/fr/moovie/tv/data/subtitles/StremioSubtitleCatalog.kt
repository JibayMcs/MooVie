package fr.moovie.tv.data.subtitles

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.subtitles.model.DownloadedSubtitle
import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.core.subtitles.port.SubtitleCatalog
import fr.moovie.tv.data.net.clientRest
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.tmdb.TmdbRepository
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Catalogue de sous-titres servi par un **addon Stremio**, sans aucune clé.
 *
 * ## Pourquoi il existe
 *
 * L'addon `opensubtitles-v3.strem.io` sert un très large fonds en accès libre :
 * pas de clé, pas de compte, pas de quota. C'est le seul catalogue de
 * l'application. Il parle le protocole des addons et traduit un identifiant
 * TMDB en IMDB.
 *
 * ## Ce qu'il ne sait pas faire, et qu'il faut savoir
 *
 * **L'appariement se fait sur le seul identifiant IMDB.** Mesuré par
 * `stremio-mpv` : le hash du fichier est ignoré par cet addon — soixante
 * sous-titres identiques avec et sans. Un sous-titre peut donc être calé pour
 * une autre version que celle qu'on regarde, et décaler de plusieurs secondes.
 * Le réglage de décalage du lecteur reste la parade, et c'est la raison de
 * rendre **plusieurs candidats par langue** plutôt que le meilleur.
 */
class StremioSubtitleCatalog(
    private val addonUrl: String = ADDON_PAR_DEFAUT,
    private val settings: SettingsRepository = SettingsRepository(),
    private val tmdb: TmdbRepository = TmdbRepository(),
    /** Clé TMDB imposée. Couture pour les sondes ; null en fonctionnement normal. */
    private val cleTmdb: String? = null,
    /** Lecture HTTP (statut, octets) ; remplaçable pour se tester sans réseau. */
    private val lecteur: suspend (String) -> Pair<Int, ByteArray> = ::lireReseau,
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

    /** Protège [liens] : recherche et téléchargement peuvent tourner en même temps. */
    private val liensVerrou = Mutex()

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

        return decode(body, voulues)
    }

    /**
     * Réponse de l'addon vers candidats, filtrés sur les langues voulues.
     * Séparé de [search] pour se tester sans réseau.
     */
    internal suspend fun decode(body: String, voulues: Set<String>): List<SubtitleCandidate> {
        val entrees = runCatching {
            json.parseToJsonElement(body).jsonObject["subtitles"]?.jsonArray
        }.getOrNull() ?: return emptyList()

        val vus = mutableSetOf<String>()
        // Construits à part, puis publiés d'un bloc : une recherche concurrente
        // ne voit jamais la table à moitié écrite.
        val nouveauxLiens = mutableMapOf<String, String>()
        val candidats = mutableListOf<SubtitleCandidate>()
        for (element in entrees) {
            val o = runCatching { element.jsonObject }.getOrNull() ?: continue
            val url = o["url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: continue
            if (!vus.add(url)) continue

            val langue = codeCourt(o["lang"]?.jsonPrimitive?.content.orEmpty())
            if (langue !in voulues) continue

            // L'identifiant de l'addon plutôt que l'URL, et l'URL gardée ici.
            // Le port autorise n'importe quelle forme, mais le magasin de
            // fichiers en fait un nom de fichier : une URL y devenait un chemin
            // impossible. Un identifiant court est aussi ce qui s'affiche à
            // l'écran à côté de la langue.
            //
            // Sans `id`, l'identifiant de repli porte un préfixe que ne peut pas
            // avoir un `id` numérique. Deux URL peuvent partager un hash : la
            // seconde est alors écartée plutôt que de reprendre le fichier de la
            // première.
            val idSousTitre = o["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: "url-${url.hashCode().toUInt()}"
            val deja = nouveauxLiens[idSousTitre]
            if (deja != null && deja != url) continue
            nouveauxLiens[idSousTitre] = url

            candidats += SubtitleCandidate(
                fileId = idSousTitre,
                language = langue,
                release = nomDeRelease(o),
                fps = fpsDe(o),
            )
        }
        liensVerrou.withLock {
            liens.clear()
            liens.putAll(nouveauxLiens)
        }
        return candidats
    }

    override suspend fun download(candidate: SubtitleCandidate): DownloadedSubtitle? {
        val url = liensVerrou.withLock { liens[candidate.fileId] } ?: return null
        // HTTPS seulement : l'URL vient de l'addon, pas de nous. Un lien en clair
        // ou d'un autre schéma n'est pas suivi.
        if (!url.startsWith("https://")) return null

        // clientRest ne lève pas sur un statut d'erreur : sans ce contrôle, une page
        // 404 ou 5xx serait enregistrée comme sous-titre. Une réponse vide ou qui
        // dépasse la borne n'est pas un sous-titre non plus.
        val (statut, octets) = try {
            lecteur(url)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        if (statut !in 200..299) return null
        if (octets.isEmpty() || octets.size > TAILLE_MAX) return null

        // Le décodage ne doit jamais remonter : une exception ici tombait jusqu'au
        // ViewModel du lecteur et fermait l'application.
        val texte = runCatching { enUtf8(octets) }.getOrNull() ?: return null

        return DownloadedSubtitle(
            candidate = candidate,
            content = texte,
            fileName = url.substringAfterLast('/').substringBefore('?'),
        )
    }

    internal companion object {

        /**
         * Addon public d'OpenSubtitles pour Stremio. **Aucune clé, aucun compte,
         * aucun quota** — c'est ce qui permet de le donner en valeur par défaut
         * plutôt que d'en faire un réglage de plus.
         */
        const val ADDON_PAR_DEFAUT = "https://opensubtitles-v3.strem.io"

        const val UA = "Moo-vie"

        /** Plafond d'un fichier de sous-titres lu depuis l'addon : 2 Mo. */
        const val TAILLE_MAX = 2L * 1024 * 1024

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
            val sb = StringBuilder(octets.size)
            for (b in octets) {
                val v = b.toInt() and 0xFF
                sb.append(if (v in 0x80..0x9F) CP1252_HAUTS[v - 0x80] else v.toChar())
            }
            return sb.toString()
        }

        /**
         * Les 32 positions 0x80-0x9F de Windows-1252, dans l'ordre des octets.
         *
         * Les cinq positions indéfinies (0x81, 0x8D, 0x8F, 0x90, 0x9D) gardent
         * leur valeur brute, comme le fait un navigateur : elles ne doivent ni
         * disparaître ni faire lever l'index. Écrit en échappements Unicode pour
         * que les guillemets et l'apostrophe typographiques soient explicites.
         */
        internal val CP1252_HAUTS: String = intArrayOf(
            0x20AC, 0x0081, 0x201A, 0x0192, 0x201E, 0x2026, 0x2020, 0x2021,
            0x02C6, 0x2030, 0x0160, 0x2039, 0x0152, 0x008D, 0x017D, 0x008F,
            0x0090, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2013, 0x2014,
            0x02DC, 0x2122, 0x0161, 0x203A, 0x0153, 0x009D, 0x017E, 0x0178,
        ).map { it.toChar() }.joinToString("")

        /**
         * Nom de release lisible : le fichier vidéo d'origine, sans son dossier
         * (chemin Windows ou POSIX) ni son extension. À défaut, le nom du fichier
         * de sous-titres. Chaîne vide quand l'addon ne donne ni l'un ni l'autre.
         */
        fun nomDeRelease(entree: JsonObject): String {
            val video = chaine(entree, "movieReleaseName")
                ?.let { dernierSegment(it).sansExtension(EXTENSIONS_VIDEO) }
            if (!video.isNullOrEmpty()) return video
            val sousTitre = chaine(entree, "subtitleFileName")
                ?.let { dernierSegment(it).sansExtension(EXTENSIONS_SOUS_TITRE) }
            return sousTitre.orEmpty()
        }

        /** Cadence en images par seconde, depuis `fpsMilli` (23976 vers 23.976). Null si absente ou nulle. */
        fun fpsDe(entree: JsonObject): Double? =
            (entree["fpsMilli"] as? JsonPrimitive)?.content
                ?.toDoubleOrNull()
                ?.takeIf { it > 0 }
                ?.div(1000.0)

        private fun chaine(entree: JsonObject, cle: String): String? =
            (entree[cle] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

        /** Dernier segment d'un chemin, séparateurs `/` et `\` confondus. */
        private fun dernierSegment(chemin: String): String =
            chemin.substringAfterLast('\\').substringAfterLast('/').trim()

        private fun String.sansExtension(extensions: List<String>): String {
            val extension = extensions.firstOrNull { endsWith(it, ignoreCase = true) } ?: return this
            return dropLast(extension.length)
        }

        private val EXTENSIONS_VIDEO = listOf(".avi", ".mkv", ".mp4")
        private val EXTENSIONS_SOUS_TITRE = listOf(".srt", ".sub", ".ass")

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

/**
 * GET borné : le statut est rendu tel quel (clientRest ne lève pas sur une
 * erreur) et le corps n'est lu que sur un 2xx, une unité au-delà de la borne
 * pour la voir sans charger la réponse entière.
 */
private suspend fun lireReseau(url: String): Pair<Int, ByteArray> {
    val reponse = clientRest.get(url) { header("User-Agent", StremioSubtitleCatalog.UA) }
    val statut = reponse.status
    if (!statut.isSuccess()) return statut.value to ByteArray(0)
    val octets = reponse.bodyAsChannel()
        .readRemaining(StremioSubtitleCatalog.TAILLE_MAX + 1)
        .readBytes()
    return statut.value to octets
}
