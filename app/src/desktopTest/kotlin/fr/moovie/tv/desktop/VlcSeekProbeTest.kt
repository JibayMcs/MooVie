package fr.moovie.tv.desktop

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.model.StreamFormat
import fr.moovie.tv.data.sources.CinestreamProvider
import fr.moovie.tv.data.sources.ExtractorRegistry
import fr.moovie.tv.data.sources.isStreamPlayable
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire (réseau + libVLC requis).
 *
 * Répond à une question que la lecture à l'œil ne tranche pas : quand le seek
 * « revient en arrière » sur desktop, est-ce libVLC, notre code, ou le flux ?
 * Les trois causes produisent le même symptôme à l'écran et demandent trois
 * correctifs différents.
 *
 * Elle déroule donc la chaîne réelle — résolution d'une source, inspection de
 * la playlist HLS, puis pilotage de libVLC dans exactement la configuration du
 * lecteur — et imprime ce que chacun répond.
 *
 *     ./gradlew :app:desktopTest --tests '*VlcSeekProbeTest*' -Dmoovie.probe=1
 *     ./gradlew :app:desktopTest --tests '*VlcSeekProbeTest*' -Dmoovie.probe=1 \
 *         -Dmoovie.stream='https://…/master.m3u8'
 */
class VlcSeekProbeTest {

    @Test
    fun probeSeek() = runBlocking {
        if (System.getProperty("moovie.probe") == null) {
            println("[sonde seek] ignorée (relancer avec -Dmoovie.probe=1)")
            return@runBlocking
        }

        val stream = System.getProperty("moovie.stream")
            ?.let { PlayableStream(it, StreamFormat.UNKNOWN) }
            ?: resolveOne()
        if (stream == null) {
            println("[sonde seek] aucune source résolue, rien à sonder")
            return@runBlocking
        }
        println("\n════ flux : ${stream.url.take(120)}")
        println("   en-têtes : ${stream.headers.keys}")

        inspectPlaylist(stream.url, stream.headers)
        drivePlayer(stream)
    }

    /** Première source jouable d'un film connu, via la chaîne de l'application. */
    private suspend fun resolveOne(): PlayableStream? {
        val provider = CinestreamProvider(ExtractorRegistry.http)
        val links = provider.sourcesFor(MediaRef.Movie(438631, "Dune", "2021"))
        for (link in links) {
            val stream = ExtractorRegistry.resolve(link) ?: continue
            if (isStreamPlayable(stream)) {
                println("   source retenue : ${link.hoster} (${stream.format})")
                return stream
            }
        }
        return null
    }

    /**
     * Ce que la playlist déclare d'elle-même.
     *
     * `#EXT-X-ENDLIST` est le point qui décide de tout : sans lui, libVLC tient
     * le flux pour un direct, donc non déplaçable. Le lecteur accepte alors le
     * `setTime` sans erreur et se replace à la position réelle du flux — ce qui,
     * vu de l'écran, est exactement un seek qui « revient en arrière ».
     */
    private fun inspectPlaylist(url: String, headers: Map<String, String>) {
        val body = runCatching {
            // Avec les en-têtes de l'hébergeur : sans eux le CDN rend une page
            // d'erreur, et la sonde conclurait « pas du HLS » sur un flux sain.
            val request = Request.Builder().url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            ExtractorRegistry.http.newCall(request).execute().use {
                it.body?.string().orEmpty()
            }
        }.getOrElse {
            println("   playlist illisible : $it")
            return
        }
        if (!body.startsWith("#EXTM3U")) {
            println("   pas une playlist HLS (${body.length} octets) — flux progressif")
            return
        }
        val lines = body.lines()
        val variants = lines.filter { it.startsWith("#EXT-X-STREAM-INF") }
        if (variants.isNotEmpty()) {
            println("   master : ${variants.size} variantes — j'inspecte la première")
            val media = lines.dropWhile { !it.startsWith("#EXT-X-STREAM-INF") }
                .drop(1)
                .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            val absolute = media?.let { if (it.startsWith("http")) it else url.substringBeforeLast('/') + "/" + it }
            if (absolute != null) return inspectPlaylist(absolute, headers)
            return
        }
        val segments = lines.count { it.startsWith("#EXTINF") }
        println("   playlist média : $segments segments")
        println("   EXT-X-PLAYLIST-TYPE : ${lines.firstOrNull { it.startsWith("#EXT-X-PLAYLIST-TYPE") } ?: "absent"}")
        println("   EXT-X-ENDLIST      : ${if (lines.any { it.startsWith("#EXT-X-ENDLIST") }) "présent" else "ABSENT → libVLC le tiendra pour un direct"}")
    }

    /** libVLC dans la configuration exacte du lecteur, puis un seek mesuré. */
    private fun drivePlayer(stream: PlayableStream) {
        NativeDiscovery().discover()
        val factory = runCatching { MediaPlayerFactory() }.getOrElse {
            println("   libVLC indisponible : $it")
            return
        }
        val player = factory.mediaPlayers().newEmbeddedMediaPlayer()
        // Le lecteur rend la vidéo dans un tampon, pas dans une fenêtre native.
        // C'est la seule différence de fond avec un VLC ordinaire, donc le
        // premier suspect quand le symptôme n'apparaît que dans l'application.
        player.videoSurface().set(
            CallbackVideoSurface(
                object : BufferFormatCallback {
                    override fun getBufferFormat(w: Int, h: Int): BufferFormat = RV32BufferFormat(w, h)
                    override fun allocatedBuffers(buffers: Array<ByteBuffer>) = Unit
                },
                RenderCallback { _, _, _ -> },
                true,
                VideoSurfaceAdapters.getVideoSurfaceAdapter(),
            ),
        )
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                println("      [event] buffering ${newCache}%")
            }
        })
        try {
            // Exactement les options du lecteur, en-têtes compris.
            fun header(name: String) =
                stream.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
            val options = buildList {
                add(":network-caching=1500")
                System.getProperty("moovie.vlcopts")?.split(' ')?.filter { it.isNotBlank() }?.forEach { add(it) }
                header("User-Agent")?.let { add(":http-user-agent=" + it) }
                header("Referer")?.let { add(":http-referrer=" + it) }
            }
            player.media().play(stream.url, *options.toTypedArray())
            // Longueur connue = média ouvert. 20 s suffisent largement.
            repeat(40) {
                if (player.status().length() > 0) return@repeat
                Thread.sleep(500)
            }
            val length = player.status().length()
            println("   longueur   : $length ms")
            println("   isSeekable : ${player.status().isSeekable}")
            println("   isPlaying  : ${player.status().isPlaying}")
            if (length <= 0) {
                println("   → longueur inconnue : aucun seek absolu possible")
                return
            }

            /**
             * Position réelle une fois libVLC replacé.
             *
             * Il faut l'attendre : pendant ~1,5 s après un `setTime`, libVLC
             * rend la valeur **demandée**, pas celle où il est. Lire trop tôt
             * fait conclure à un seek parfait alors qu'il n'a pas encore eu
             * lieu — c'est ce qui a faussé le premier passage de cette sonde.
             */
            fun settle(target: Long): Long {
                player.controls().setTime(target)
                Thread.sleep(4000)
                val a = player.status().time()
                Thread.sleep(1000)
                val b = player.status().time()
                // b > a prouve que le flux coule à nouveau ; a est alors la
                // position réelle du replacement, à une seconde près.
                return if (b > a) a else b
            }

            println("   options : " + options.joinToString(" "))
            // Le contrôleur réel, celui que l'écran utilise : c'est sa
            // compensation qu'on mesure, pas une réimplémentation.
            val controller = VlcjPlayerController(player)
            controller.onMediaChanged()
            Thread.sleep(3000)
            for (fraction in listOf(0.25, 0.5, 0.7)) {
                val target = (length * fraction).toLong()
                controller.seekTo(target)
                // Au-delà des deux paliers de rattrapage du contrôleur.
                Thread.sleep(11_000)
                val landed = player.status().time()
                // Se poser *après* la cible est correct : en HLS on ne choisit
                // pas mieux qu'un début de segment. Se poser avant ne l'est
                // pas — c'est ce qui ramenait dans l'intro qu'on venait de
                // demander à passer.
                val verdict = if (landed >= target) "OK" else "TROP TOT"
                println("   cible $target -> pose $landed (ecart ${landed - target} ms) $verdict")
                Thread.sleep(1500)
            }

            // Le cas des flèches : trois avances de 10 s doivent faire +30 s,
            // et non un seul pas comme lorsque chacune repartait de la position
            // que libVLC annonçait avant de s'être replacé.
            val from = controller.positionMs()
            repeat(3) { controller.seekBy(10_000) }
            Thread.sleep(13_000)
            val after = player.status().time()
            println("   3 x +10 s depuis $from -> $after (delta ${after - from} ms, attendu >= 30000)")
            controller.shutdown()

        } finally {
            runCatching { player.controls().stop() }
            runCatching { player.release() }
            runCatching { factory.release() }
        }
    }
}
