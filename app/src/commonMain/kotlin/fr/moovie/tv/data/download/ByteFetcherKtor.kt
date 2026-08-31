package fr.moovie.tv.data.download

import fr.moovie.tv.shared.dispatcherEs
import fr.moovie.tv.shared.systemeFichiers
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

/**
 * L'adaptateur réel : Ktor, **en flux** vers le fichier.
 *
 * Le flux n'est pas une élégance : un segment fait quelques mégaoctets, mais un
 * MP4 direct fait plusieurs gigaoctets et n'entrerait pas en mémoire sur une
 * box. C'est pour cette raison que le corps est lu par morceaux plutôt que
 * matérialisé — `prepareGet` puis `bodyAsChannel` donnent le canal sans le
 * charger, là où OkHttp donnait un `byteStream()`.
 *
 * **Écriture par fichier temporaire, puis renommage.** C'est ce qui rend la
 * reprise sûre : sans lui, une coupure au milieu d'un segment laisserait un
 * fichier de taille non nulle, que la reprise sauterait en le croyant complet —
 * et la lecture s'arrêterait net à cet endroit, des semaines plus tard.
 */
class ByteFetcherKtor(
    private val client: HttpClient = clientTelechargements(),
    private val fs: FileSystem = systemeFichiers,
) : ByteFetcher {

    override suspend fun fetch(
        url: String,
        headers: Map<String, String>,
        target: Path,
        onProgress: suspend (recus: Long, total: Long) -> Unit,
    ): Long = withContext(dispatcherEs) {
        client.prepareGet(url) {
            headers.forEach { (k, v) -> header(k, v) }
        }.execute { reponse ->
            if (!reponse.status.isSuccess()) {
                error("HTTP ${reponse.status.value} sur $url")
            }
            val partiel = target.parent?.let { it / (target.name + ".part") }
                ?: error("Cible sans répertoire : $target")
            target.parent?.let(fs::createDirectories)

            // Une boucle et non une recopie d'un trait : c'est ce qui laissait
            // la barre à 0 % pendant qu'un film de plusieurs gigaoctets
            // arrivait. Le transfert est le même, à ceci près qu'il rend compte
            // au passage.
            val total = reponse.contentLength()?.takeIf { it > 0 } ?: 0L
            var recus = 0L
            val canal = reponse.bodyAsChannel()
            fs.write(partiel) {
                val tampon = ByteArray(TAILLE_TAMPON)
                while (true) {
                    val lus = canal.readAvailable(tampon, 0, tampon.size)
                    if (lus < 0) break
                    if (lus > 0) {
                        write(tampon, 0, lus)
                        recus += lus
                        onProgress(recus, total)
                    }
                }
            }
            // `atomicMove` échoue si la cible existe sur certains systèmes ;
            // okio l'écrase, ce que faisait déjà `renameTo` ici — une reprise
            // qui réécrit un segment doit pouvoir remplacer le précédent.
            runCatching { fs.atomicMove(partiel, target) }.getOrElse {
                runCatching { fs.delete(partiel) }
                error("Écriture impossible : ${target.name}")
            }
            fs.metadataOrNull(target)?.size ?: 0L
        }
    }
}

/**
 * Client des transferts de média, sans plafond de durée d'appel : un film de
 * plusieurs gigaoctets met légitimement des minutes. Seuls la connexion et le
 * silence entre deux octets sont bornés.
 */
/** 8 Kio, la valeur de `DEFAULT_BUFFER_SIZE` de la JVM, absent du commun. */
private const val TAILLE_TAMPON = 8 * 1024

private fun clientTelechargements() = HttpClient {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 20_000
        socketTimeoutMillis = 60_000
    }
}
