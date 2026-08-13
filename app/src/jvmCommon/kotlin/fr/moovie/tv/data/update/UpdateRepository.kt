package fr.moovie.tv.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GithubAsset> = emptyList(),
    val prerelease: Boolean = false,
    val draft: Boolean = false,
)

/**
 * Mises à jour via les GitHub Releases publiques du dépôt : la CI publie un APK
 * signé par tag `vX.Y.Z`, l'app compare la dernière release à sa propre
 * version — `releases/latest` par défaut, la liste complète pour qui a demandé
 * à recevoir les préversions.
 */
class UpdateRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    /**
     * Dernière release à proposer, ou null (réseau HS, aucune release).
     *
     * @param prereleases inclure les préversions. Deux endpoints, et non un
     *   filtre : `releases/latest` **est** la définition GitHub de « la
     *   dernière version stable » — elle écarte les préversions et les
     *   brouillons pour nous. Le canal de test a besoin de l'autre, la liste
     *   complète, où il faut faire ce tri soi-même.
     */
    suspend fun latestRelease(prereleases: Boolean = false): GithubRelease? =
        withContext(Dispatchers.IO) {
            if (prereleases) latestIncludingPrereleases() else latestStable()
        }

    /**
     * Cette release doit-elle être proposée sur ce canal ?
     *
     * **Partagée par les deux plateformes, et c'est le point.** La règle vivait
     * en double dans les deux ViewModels, et le canal « préversions » livré en
     * 1.18.0 n'en a corrigé aucun : ils demandaient la liste complète à GitHub
     * puis rejetaient toute préversion, si bien que le réglage n'avait
     * littéralement aucun effet — sur Android comme sur desktop. Une règle
     * dupliquée est une règle qu'on oublie de moitié.
     *
     * Un brouillon n'est publié pour personne, quel que soit le canal.
     */
    fun isEligible(release: GithubRelease, prereleases: Boolean): Boolean =
        !release.draft && (prereleases || !release.prerelease)

    private fun latestStable(): GithubRelease? = runCatching {
        val request = Request.Builder()
            .url("https://api.github.com/repos/JibayMcs/MooVie/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            json.decodeFromString<GithubRelease>(resp.body!!.string())
        }
    }.getOrNull()

    /**
     * La plus récente des releases, préversions comprises.
     *
     * GitHub rend la liste **antéchronologiquement**, mais on ne se contente
     * pas de prendre la première : elle peut être un brouillon, et surtout
     * l'ordre de publication n'est pas l'ordre des versions — republier un
     * correctif sur une ancienne branche mettrait une version inférieure en
     * tête. On les compare donc, avec la même règle que partout ailleurs.
     *
     * Une seule page : trente entrées couvrent largement l'historique utile, et
     * paginer pour retrouver des versions que plus personne n'utilise coûterait
     * des requêtes à chaque vérification.
     */
    private fun latestIncludingPrereleases(): GithubRelease? = runCatching {
        val request = Request.Builder()
            .url("https://api.github.com/repos/JibayMcs/MooVie/releases?per_page=30")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            json.decodeFromString<List<GithubRelease>>(resp.body!!.string())
                .filter { !it.draft }
                .maxWithOrNull { a, b ->
                    when {
                        a.tagName == b.tagName -> 0
                        isNewer(a.tagName, b.tagName.removePrefix("v")) -> 1
                        else -> -1
                    }
                }
        }
    }.getOrNull()

    /**
     * Compare deux versions « semver » (tag `v1.2.3` vs `1.2.0`).
     *
     * À numéros égaux, une **préversion vaut moins que sa version finale** :
     * `1.15.0-rc.1` est antérieure à `1.15.0`. Sans cette règle, celui qui a
     * installé une release candidate à la main resterait dessus pour toujours —
     * l'app comparait les seuls numéros, les trouvait identiques, et ne lui
     * proposait jamais la version définitive qu'il avait aidé à éprouver.
     *
     * **Deux préversions se départagent aussi** — `rc.5` vient après `rc.4`.
     * Ce n'était pas nécessaire tant que l'app ne lisait que
     * `releases/latest`, qui les exclut ; depuis que le canal « préversions »
     * existe, ne pas les ordonner laisserait le testeur bloqué sur celle qu'il
     * a installée, l'app comparant deux fois le même numéro sans y voir de
     * différence. Une panne parfaitement silencieuse.
     */
    fun isNewer(tag: String, current: String): Boolean {
        fun core(v: String) = v.removePrefix("v").substringBefore('-')
            .split('.').mapNotNull { it.toIntOrNull() }
        fun suffix(v: String) = v.removePrefix("v").substringAfter('-', "")

        val a = core(tag)
        val b = core(current)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return comparePrerelease(suffix(tag), suffix(current)) > 0
    }

    /**
     * Ordonne deux suffixes de préversion, à la façon de semver.
     *
     * Positif si [a] est postérieur à [b]. Trois règles, et chacune corrige une
     * erreur qu'on ferait naturellement :
     *
     * - **l'absence de suffixe gagne** : `1.18.0` est postérieure à
     *   `1.18.0-rc.5`, jamais l'inverse ;
     * - **les identifiants numériques se comparent en nombres** : comparés
     *   comme du texte, `rc.10` passerait avant `rc.2`, et le testeur resterait
     *   sur la rc.2 sans jamais rien recevoir ;
     * - **le plus court est antérieur** à identifiants égaux, `rc` venant avant
     *   `rc.1`.
     */
    private fun comparePrerelease(a: String, b: String): Int {
        if (a == b) return 0
        // Une version finale n'a pas de suffixe et l'emporte sur toute préversion.
        if (a.isEmpty()) return 1
        if (b.isEmpty()) return -1

        val x = a.split('.')
        val y = b.split('.')
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrNull(i) ?: return -1
            val q = y.getOrNull(i) ?: return 1
            val pn = p.toIntOrNull()
            val qn = q.toIntOrNull()
            val verdict = when {
                pn != null && qn != null -> pn.compareTo(qn)
                // Semver : un identifiant numérique précède un alphanumérique.
                pn != null -> -1
                qn != null -> 1
                else -> p.compareTo(q)
            }
            if (verdict != 0) return verdict
        }
        return 0
    }

    /**
     * Télécharge l'APK vers [dest] en publiant la progression (0..1).
     * Renvoie true si le fichier est complet.
     */
    suspend fun downloadApk(url: String, dest: File, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use false
                    val body = resp.body ?: return@use false
                    val total = body.contentLength()
                    dest.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                out.write(buffer, 0, read)
                                copied += read
                                if (total > 0) onProgress(copied.toFloat() / total)
                            }
                        }
                    }
                    true
                }
            }.getOrDefault(false)
        }
}
