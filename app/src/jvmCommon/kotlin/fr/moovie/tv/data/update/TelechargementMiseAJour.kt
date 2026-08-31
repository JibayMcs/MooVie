package fr.moovie.tv.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Client dédié au téléchargement d'un binaire de mise à jour.
 *
 * Séparé de celui de `UpdateRepository` : celui-là interroge l'API GitHub et
 * lit des réponses de quelques kilo-octets, celui-ci transfère un APK ou un
 * installeur de plusieurs dizaines de mégaoctets. Un client sans plafond de
 * durée d'appel est nécessaire ici, et néfaste là-bas.
 */
private val clientTelechargement = OkHttpClient()

/**
 * Télécharge le binaire de mise à jour vers [dest] en publiant la progression
 * (0..1). Renvoie true si le fichier est complet.
 *
 * **Côté JVM uniquement, et c'est structurel.** iOS n'a pas de mise à jour
 * intégrée : un `.ipa` sideloadé se réinstalle par AltStore, qui gère lui-même
 * la vérification de version et le remplacement. Une app ne peut de toute façon
 * pas s'installer elle-même sur iOS. La *détection* de version, elle, reste
 * commune dans [UpdateRepository] — c'est du HTTP et de la comparaison semver,
 * utile partout.
 *
 * Extension plutôt que méthode : cela garde `UpdateRepository` commun sans
 * l'obliger à déclarer un `expect` que la moitié des plateformes
 * implémenterait par un `TODO()`.
 */
suspend fun UpdateRepository.downloadApk(
    url: String,
    dest: File,
    onProgress: (Float) -> Unit,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder().url(url).build()
        clientTelechargement.newCall(request).execute().use { resp ->
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
