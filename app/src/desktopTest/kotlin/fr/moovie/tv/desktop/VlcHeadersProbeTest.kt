package fr.moovie.tv.desktop

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
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
 * Certains CDN exigent **Referer et User-Agent sur chaque requête**, segments
 * compris — vérifié à la main sur vidzy : la playlist et les segments rendent
 * 403 avec l'un des deux seulement, 200 avec les deux. Or une playlist se lit
 * par l'accès HTTP principal, et les segments par ceux que le démultiplexeur
 * adaptatif ouvre lui-même. Si ces derniers n'héritent pas de l'en-tête, la
 * lecture échoue **après** que la source a été déclarée jouable :
 *
 *     adaptive demux error: Failed reading …/seg-1-v1-a1.ts: HTTP/1.1 403
 *
 * À l'écran : 0:00 / 0:00, aucune image, et l'enchaînement vers l'épisode
 * suivant puisque libVLC signale la fin. Rien qui ressemble à un refus.
 *
 * Cette sonde compare les deux façons de donner l'en-tête à libVLC : en option
 * de média (`:http-referrer`, ce que fait le lecteur) et en option d'instance
 * (`--http-referrer`, valable pour tout accès HTTP créé par la suite).
 *
 *     ./gradlew :app:desktopTest --tests '*VlcHeadersProbeTest*' -Dmoovie.probe=1 \
 *         -Dmoovie.stream='https://…/master.m3u8' \
 *         -Dmoovie.referer='https://vidzy.cc/'
 */
class VlcHeadersProbeTest {

    private val ua = System.getProperty("moovie.ua")
        ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    @Test
    fun probeHeaders() {
        if (System.getProperty("moovie.probe") == null) {
            println("[sonde en-têtes] ignorée (relancer avec -Dmoovie.probe=1)")
            return
        }
        val url = System.getProperty("moovie.stream") ?: run {
            println("[sonde en-têtes] -Dmoovie.stream requis")
            return
        }
        val referer = System.getProperty("moovie.referer") ?: ""
        NativeDiscovery().discover()

        println("\n════ ${url.take(110)}")
        play("option de média (état actuel)", url, factoryArgs = emptyList(), mediaReferer = referer)
        play("option d'instance", url, factoryArgs = listOf("--http-referrer=$referer"), mediaReferer = referer)
        play("les deux", url, factoryArgs = listOf("--http-referrer=$referer"), mediaReferer = referer, both = true)
        // Le relais local : les en-têtes sont réinjectés sur chaque requête,
        // segments compris. C'est la seule voie qui reste.
        val relay = LocalStreamProxy(mapOf("Referer" to referer, "User-Agent" to ua))
        try {
            play("relais local", relay.localUrl(url), factoryArgs = emptyList(), mediaReferer = referer)
        } finally {
            relay.shutdown()
        }
        play(
            "UA + Referer en instance",
            url,
            factoryArgs = listOf("--http-referrer=$referer", "--http-user-agent=$ua"),
            mediaReferer = referer,
            both = true,
        )
    }

    /**
     * Joue quelques secondes et dit si le flux a réellement coulé.
     *
     * Le critère est l'horloge : une longueur connue ne prouve rien, elle vient
     * de la playlist. Seule une position qui avance prouve qu'un segment a été
     * décodé.
     */
    private fun play(
        label: String,
        url: String,
        factoryArgs: List<String>,
        mediaReferer: String,
        both: Boolean = false,
    ) {
        val factory = runCatching { MediaPlayerFactory(*factoryArgs.toTypedArray()) }.getOrElse {
            println("   [$label] libVLC indisponible : $it")
            return
        }
        val player = factory.mediaPlayers().newEmbeddedMediaPlayer()
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
        try {
            val options = buildList {
                add(":network-caching=1500")
                add(":http-user-agent=$ua")
                if (both || factoryArgs.isEmpty()) add(":http-referrer=$mediaReferer")
            }
            player.media().play(url, *options.toTypedArray())
            Thread.sleep(8000)
            val length = player.status().length()
            val t1 = player.status().time()
            Thread.sleep(3000)
            val t2 = player.status().time()
            val verdict = if (t2 > t1 && t1 > 0) "LIT" else "NE LIT PAS"
            println("   [$label] longueur=$length  t=$t1 → $t2  $verdict")
        } finally {
            runCatching { player.controls().stop() }
            runCatching { player.release() }
            runCatching { factory.release() }
        }
    }
}
