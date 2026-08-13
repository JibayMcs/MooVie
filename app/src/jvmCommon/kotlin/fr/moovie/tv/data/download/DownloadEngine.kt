package fr.moovie.tv.data.download

import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import java.io.File
import java.net.URI

/**
 * Port de récupération d'octets.
 *
 * Distinct de `HttpGateway`, qui rend du texte : un segment est binaire et se
 * compte en mégaoctets, il doit aller au fichier sans passer par une `String`.
 * Étroit exprès — le moteur s'éprouve alors avec un faux qui écrit des octets
 * fabriqués, sans réseau ni hébergeur.
 */
// Interface ordinaire et non `fun interface` : la conversion SAM interdit les
// valeurs par défaut, et le rappel de progression en a une — la plupart des
// appels (playlists, segments) n'ont rien à raconter. Aucune lambda n'était
// convertie de toute façon, les deux implémentations sont des classes.
interface ByteFetcher {
    /**
     * Écrit le contenu dans [target] et rend le nombre d'octets écrits.
     *
     * [onProgress] reçoit, pendant le transfert, les octets déjà écrits et la
     * taille annoncée (0 si l'hébergeur ne la dit pas). Sans lui, un fichier
     * unique de plusieurs gigaoctets ne donne aucun signe de vie avant sa
     * dernière seconde : l'écran affichait 0 % pendant une heure pendant que
     * les données arrivaient — l'utilisateur voyait son forfait fondre en face
     * d'une barre immobile.
     */
    suspend fun fetch(
        url: String,
        headers: Map<String, String>,
        target: File,
        onProgress: suspend (recus: Long, total: Long) -> Unit = { _, _ -> },
    ): Long
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
    /**
     * Espace libre du volume qui reçoit les fichiers. Injecté pour la même
     * raison que [dirFor] : un test ne peut pas remplir un vrai disque.
     */
    private val freeSpace: (File) -> Long = { it.usableSpace },
) {

    suspend fun run(download: Download, stream: PlayableStream): DownloadOutcome {
        val dir = dirFor(download.key).also { it.mkdirs() }
        // Avant d'ouvrir quoi que ce soit : commencer pour s'arrêter à mi-course
        // laisse des octets qui ne serviront jamais, sur un disque qui manque
        // déjà de place.
        if (diskFull(dir)) return DownloadOutcome.Failed(DISK_FULL)
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

        // Rendu compte pendant le transfert, et **espacé dans le temps** : la
        // publication écrit dans DataStore, et un fichier de deux gigaoctets
        // passe par ce rappel des dizaines de milliers de fois. Une seconde
        // entre deux écritures suffit largement à une barre de progression, et
        // laisse le magasin tranquille.
        var dernierePublication = 0L
        val bytes = fetchResilient(download, stream, stream.url, target) { recus, total ->
            val maintenant = System.currentTimeMillis()
            if (maintenant - dernierePublication >= PROGRESS_INTERVAL_MS) {
                dernierePublication = maintenant
                publish(
                    download.copy(
                        state = DownloadState.RUNNING,
                        totalSegments = 1,
                        doneSegments = 0,
                        bytes = recus,
                        totalBytes = total,
                    ),
                )
            }
        }
        publish(
            download.copy(
                state = DownloadState.DONE,
                totalSegments = 1,
                doneSegments = 1,
                bytes = bytes,
                totalBytes = bytes,
            ),
        )
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
                // Contrôlé à la cadence des publications, pas à chaque segment :
                // interroger le système de fichiers des milliers de fois coûte
                // plus que ce que ça protège. On s'arrête proprement, et les
                // segments déjà là restent — de la place libérée, et la reprise
                // repart d'où elle en était.
                if (diskFull(dir)) {
                    publish(
                        download.copy(
                            state = DownloadState.RUNNING,
                            totalSegments = local.resources.size,
                            doneSegments = done,
                            bytes = bytes,
                        ),
                    )
                    return DownloadOutcome.Failed(DISK_FULL)
                }
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
        onProgress: suspend (recus: Long, total: Long) -> Unit = { _, _ -> },
    ): Long = try {
        fetcher.fetch(url, stream.headers, target, onProgress)
    } catch (first: Exception) {
        val fresh = resolver.resolve(download.key, download.language)
            ?: throw first
        // L'URL du segment vient de l'ancienne playlist : on ne peut pas la
        // rejouer telle quelle sur la nouvelle source. Seuls les *en-têtes*
        // fraîchement obtenus servent — c'est eux que l'hébergeur vérifie.
        fetcher.fetch(url, fresh.headers, target, onProgress)
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

    /**
     * Reste-t-il trop peu de place pour continuer ?
     *
     * On garde une réserve plutôt que d'écrire jusqu'au dernier octet : un
     * volume plein ne gêne pas que le téléchargement, il empêche le système
     * d'écrire ses propres fichiers, et la reprise elle-même a besoin d'un peu
     * d'air. Sans cette garde, remplir le disque arrêtait le téléchargement
     * sans un mot — le symptôme rapporté était « ça s'est arrêté tout seul ».
     *
     * Une erreur de lecture ne bloque pas : mieux vaut tenter et échouer sur
     * l'écriture que refuser un téléchargement parfaitement possible.
     */
    private fun diskFull(dir: File): Boolean {
        val free = runCatching { freeSpace(dir) }.getOrDefault(Long.MAX_VALUE)
        return free < MIN_FREE_BYTES
    }

    private companion object {
        const val PROGRESS_EVERY = 10

        /**
         * Écart minimal entre deux publications de progression, sur un fichier
         * unique. Chaque publication est une écriture DataStore, et le rappel
         * arrive à chaque bloc lu — des dizaines de milliers de fois sur un
         * film. Une seconde suffit à une barre qui avance ; en dessous, on
         * userait le magasin pour une différence que personne ne voit.
         */
        const val PROGRESS_INTERVAL_MS = 1_000L

        /**
         * Réserve à ne pas entamer, 500 Mo. Assez pour que le système respire
         * et que la reprise ait de quoi écrire, assez peu pour ne pas rendre
         * inutilisable un appareil qui n'a que quelques giga-octets.
         */
        const val MIN_FREE_BYTES = 500L * 1024 * 1024

        const val DISK_FULL = "Espace de stockage insuffisant : libérez de la place, le téléchargement reprendra où il en est."
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

/**
 * La copie locale sous forme de flux, ou null.
 *
 * Rendre un [PlayableStream] plutôt qu'un chemin permet à toute la chaîne de
 * lecture de rester identique : le lecteur ne sait pas qu'il ouvre un fichier,
 * et rien de ce qui suit — reprise, sous-titres, épisode suivant — n'a besoin
 * d'un cas particulier.
 *
 * **Aucun en-tête** : c'est ce qui rend la lecture réellement hors ligne. Un
 * `Referer` sur un `file://` n'aurait aucun sens, et sa présence trahirait que
 * quelque chose part encore sur le réseau.
 */
fun localStream(key: String): PlayableStream? {
    val file = playableFile(key) ?: return null
    return PlayableStream(
        url = fileUrl(file),
        format = if (file.name == PLAYLIST_NAME) StreamFormat.HLS else StreamFormat.MP4,
    )
}

/**
 * L'URL `file://` d'un fichier local, avec **trois** barres obliques.
 *
 * `File.toURI()` rend la forme dégénérée `file:/home/…`, à une seule barre.
 * Android la tolère, libVLC non : il n'y reconnaît aucun schéma, traite la
 * chaîne comme un chemin relatif et la résout contre le répertoire de travail.
 * D'où l'erreur observée, où le chemin du dépôt se retrouvait collé devant
 * celui du téléchargement :
 *
 *     .../Moo-vie/app/file:/home/…/downloads/tv_108978_s2e3/stream.m3u8
 *
 * On reconstruit donc l'URI avec une autorité vide, ce qui donne
 * `file:///home/…`. Passer par `uri.path` plutôt que par `absolutePath` garde
 * l'échappement et le cas Windows, où le chemin devient `/C:/…`.
 */
internal fun fileUrl(file: File): String {
    val uri = file.toURI()
    return URI(uri.scheme, "", uri.path, null).toString()
}
