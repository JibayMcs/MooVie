package fr.moovie.tv.data.download

import fr.moovie.tv.core.sources.model.PlayableStream
import java.io.File

/**
 * Port de récupération d'octets.
 *
 * Distinct de `HttpGateway`, qui rend du texte : un segment est binaire et se
 * compte en mégaoctets, il doit aller au fichier sans passer par une `String`.
 * Étroit exprès — le moteur s'éprouve alors avec un faux qui écrit des octets
 * fabriqués, sans réseau ni hébergeur.
 */
fun interface ByteFetcher {
    /** Écrit le contenu dans [target] et rend le nombre d'octets écrits. */
    suspend fun fetch(url: String, headers: Map<String, String>, target: File): Long
}

/**
 * Retrouve une source fraîche pour une clé média.
 *
 * **Indispensable, pas optionnel.** Une URL de flux expire en moins de deux
 * heures ; un film de plusieurs gigaoctets sur une ligne modeste dépasse ce
 * délai. Sans re-résolution, la reprise repartirait de zéro à chaque expiration
 * et un fichier assez gros ne finirait jamais.
 */
fun interface StreamResolver {
    suspend fun resolve(key: String, language: String): PlayableStream?
}

/**
 * Où l'avancement est publié.
 *
 * Un port plutôt que le dépôt : sans lui, éprouver le moteur exigerait un
 * DataStore, donc un appareil. Le dépôt réel s'y branche en une ligne.
 */
fun interface DownloadProgress {
    suspend fun publish(download: Download)
}

/** Ce qu'un téléchargement a produit. */
sealed interface DownloadOutcome {
    data class Done(val bytes: Long) : DownloadOutcome
    data class Failed(val reason: String) : DownloadOutcome
}

/**
 * Télécharge un titre pour la lecture hors ligne.
 *
 * Le résultat est un dossier que **les deux lecteurs ouvrent tels quels** :
 * segments bruts et `stream.m3u8` réécrit en chemins relatifs. C'est ce qui
 * évite d'écrire un téléchargeur par plateforme — le `DownloadManager` de
 * media3 rangerait les segments dans un cache que VLC ne sait pas lire, et un
 * fichier récupéré sur la TV serait inexploitable sur le PC.
 *
 * **Reprenable par construction** : un fichier déjà présent et non vide est
 * sauté. Une coupure ne coûte donc que le segment en cours, et la reprise ne
 * demande aucun état sauvegardé — le disque *est* l'état.
 */
class DownloadEngine(
    private val fetcher: ByteFetcher,
    private val progress: DownloadProgress,
    private val resolver: StreamResolver,
    /**
     * Où poser les fichiers. Injecté pour que les tests n'écrivent pas dans le
     * dossier de données de la personne qui lance la suite.
     */
    private val dirFor: (String) -> File = ::downloadDir,
) {

    suspend fun run(download: Download, stream: PlayableStream): DownloadOutcome {
        val dir = dirFor(download.key).also { it.mkdirs() }
        return try {
            if (isHls(stream.url)) hls(download, stream, dir) else direct(download, stream, dir)
        } catch (e: Exception) {
            DownloadOutcome.Failed(e.message ?: e.toString())
        }
    }

    /** Les trois sources qui livrent un MP4 : un seul fichier, rien à réécrire. */
    private suspend fun direct(
        download: Download,
        stream: PlayableStream,
        dir: File,
    ): DownloadOutcome {
        val target = File(dir, "video.mp4")
        publish(download.copy(state = DownloadState.RUNNING, totalSegments = 1, doneSegments = 0))
        val bytes = fetchResilient(download, stream, stream.url, target)
        publish(download.copy(state = DownloadState.DONE, totalSegments = 1, doneSegments = 1, bytes = bytes))
        return DownloadOutcome.Done(bytes)
    }

    private suspend fun hls(
        download: Download,
        stream: PlayableStream,
        dir: File,
    ): DownloadOutcome {
        var source = stream
        var playlistText = fetchText(source, source.url)

        // Une master playlist ne contient aucun segment : il faut redescendre
        // d'un cran avant de pouvoir localiser quoi que ce soit.
        var playlistUrl = source.url
        if (HlsPlaylist.isMaster(playlistText)) {
            playlistUrl = HlsPlaylist.pickVariant(playlistText, playlistUrl)
                ?: return DownloadOutcome.Failed("Aucune variante lisible dans la playlist")
            playlistText = fetchText(source, playlistUrl)
        }

        val local = HlsPlaylist.localize(playlistText, playlistUrl)
        if (local.resources.isEmpty()) return DownloadOutcome.Failed("Playlist sans segment")

        File(dir, PLAYLIST_NAME).writeText(local.text)

        var done = 0
        var bytes = 0L
        publish(
            download.copy(
                state = DownloadState.RUNNING,
                totalSegments = local.resources.size,
                doneSegments = 0,
            ),
        )

        for (resource in local.resources) {
            val target = File(dir, resource.localName)
            // Le disque est l'état : ce qui est déjà là ne se retélécharge pas.
            bytes += if (target.length() > 0) {
                target.length()
            } else {
                fetchResilient(download, source, resource.url, target)
            }
            done++
            // On ne publie pas à chaque segment : une série en compte des
            // milliers, et chaque publication est une écriture DataStore.
            if (done % PROGRESS_EVERY == 0 || done == local.resources.size) {
                publish(
                    download.copy(
                        state = DownloadState.RUNNING,
                        totalSegments = local.resources.size,
                        doneSegments = done,
                        bytes = bytes,
                    ),
                )
            }
        }

        publish(
            download.copy(
                state = DownloadState.DONE,
                totalSegments = local.resources.size,
                doneSegments = done,
                bytes = bytes,
            ),
        )
        return DownloadOutcome.Done(bytes)
    }

    /**
     * Récupère, et **re-résout une fois** si l'hébergeur refuse.
     *
     * Un refus en cours de route veut presque toujours dire que le jeton de
     * l'URL a expiré, pas que le fichier a disparu. Redemander une source
     * fraîche coûte une requête ; ne pas le faire coûte tout ce qui a déjà été
     * téléchargé.
     */
    private suspend fun fetchResilient(
        download: Download,
        stream: PlayableStream,
        url: String,
        target: File,
    ): Long = try {
        fetcher.fetch(url, stream.headers, target)
    } catch (first: Exception) {
        val fresh = resolver.resolve(download.key, download.language)
            ?: throw first
        // L'URL du segment vient de l'ancienne playlist : on ne peut pas la
        // rejouer telle quelle sur la nouvelle source. Seuls les *en-têtes*
        // fraîchement obtenus servent — c'est eux que l'hébergeur vérifie.
        fetcher.fetch(url, fresh.headers, target)
    }

    private suspend fun fetchText(stream: PlayableStream, url: String): String {
        val temp = File.createTempFile("moovie-playlist", ".m3u8")
        return try {
            fetcher.fetch(url, stream.headers, temp)
            temp.readText()
        } finally {
            temp.delete()
        }
    }

    private suspend fun publish(download: Download) = progress.publish(download)

    private fun isHls(url: String) = ".m3u8" in url.substringBefore('?').lowercase()

    private companion object {
        const val PROGRESS_EVERY = 10
    }
}

/** Nom de la playlist locale, celle que les lecteurs ouvriront. */
const val PLAYLIST_NAME = "stream.m3u8"

/** Le fichier à lire pour un titre téléchargé, ou null s'il n'est pas complet. */
fun playableFile(key: String): File? {
    val dir = downloadDir(key)
    val playlist = File(dir, PLAYLIST_NAME)
    if (playlist.exists()) return playlist
    val mp4 = File(dir, "video.mp4")
    return mp4.takeIf { it.length() > 0 }
}
