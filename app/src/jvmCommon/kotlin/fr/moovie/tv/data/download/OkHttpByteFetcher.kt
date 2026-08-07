package fr.moovie.tv.data.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * L'adaptateur réel : OkHttp, en flux vers le fichier.
 *
 * `source().use { target.sink() }` plutôt qu'un `bytes()` : un segment fait
 * quelques mégaoctets, mais un MP4 direct fait plusieurs gigaoctets et
 * n'entrerait pas en mémoire sur une box.
 *
 * **Écriture par fichier temporaire, puis renommage.** C'est ce qui rend la
 * reprise sûre : sans lui, une coupure au milieu d'un segment laisserait un
 * fichier de taille non nulle, que la reprise sauterait en le croyant complet —
 * et la lecture s'arrêterait net à cet endroit, des semaines plus tard.
 */
class OkHttpByteFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) : ByteFetcher {

    override suspend fun fetch(
        url: String,
        headers: Map<String, String>,
        target: File,
    ): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} sur $url")
            }
            val body = response.body ?: throw IOException("Réponse vide sur $url")
            val partial = File(target.parentFile, target.name + ".part")
            body.byteStream().use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }
            if (!partial.renameTo(target)) {
                partial.delete()
                throw IOException("Écriture impossible : ${target.name}")
            }
            target.length()
        }
    }
}
