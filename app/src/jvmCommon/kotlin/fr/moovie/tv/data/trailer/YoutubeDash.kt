package fr.moovie.tv.data.trailer

/**
 * Une piste rendue par YouTube : soit l'image, soit le son, jamais les deux.
 *
 * Volontairement détaché du modèle de désérialisation : c'est ce qui permet de
 * tester la fabrication du manifeste sans réseau ni JSON, à la façon de
 * `PackedJsTest`.
 */
data class YtTrack(
    val itag: Int,
    val url: String,
    /** Type MIME complet, `codecs` compris, tel que YouTube l'écrit. */
    val mimeType: String,
    val bitrate: Long,
    val initRange: IntRange?,
    val indexRange: IntRange?,
    val durationMs: Long,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
    val audioSampleRate: Int = 0,
    val audioChannels: Int = 0,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")

    /** `video/mp4; codecs="avc1.640028"` → `avc1.640028`. */
    val codecs: String
        get() = CODECS.find(mimeType)?.groupValues?.get(1).orEmpty()

    /** `video/mp4; codecs="…"` → `video/mp4`. */
    val container: String
        get() = mimeType.substringBefore(';').trim()

    private companion object {
        val CODECS = Regex("""codecs="([^"]+)"""")
    }
}

/**
 * Fabrique un manifeste DASH à partir des pistes séparées de YouTube.
 *
 * ## Pourquoi il faut en passer par là
 *
 * YouTube ne sert plus de flux « progressif » (image et son dans un même
 * fichier) au seul client qui nous répond encore. Il ne rend que des pistes
 * séparées — dix-huit vidéos, six audios — que le lecteur doit recoller.
 * DASH est précisément le format qui décrit ça, et les deux lecteurs le lisent
 * déjà : `media3-exoplayer-dash` côté Android, le démultiplexeur adaptatif de
 * libVLC côté desktop. Fabriquer le manifeste, c'est donc la seule pièce
 * manquante — pas un détour.
 *
 * On n'écrit **que** du H.264 et de l'AAC, alors que YouTube propose aussi VP9
 * et AV1 : la Mi Box 4 est une puce de 2017 qui décode le premier en matériel et
 * pas les seconds. Un manifeste « complet » y donnerait une bande-annonce qui
 * saccade, ou une image noire — le genre de panne qui ressemble à une source
 * cassée alors que c'est un choix de codec.
 *
 * @return le XML, ou null si l'une des deux pistes manque : un manifeste sans
 *         son, ou sans image, n'est pas une bande-annonce dégradée, c'est une
 *         panne qui a l'air de marcher.
 */
fun buildYoutubeDashManifest(tracks: List<YtTrack>): String? {
    val videos = tracks
        .filter { it.isVideo && it.container == "video/mp4" && it.codecs.startsWith("avc1") }
        .filter { it.initRange != null && it.indexRange != null }
        .sortedByDescending { it.height }
        // Deux barreaux, pas dix-huit. Mesuré : avec l'échelle complète, VLC
        // démarre sur le plus bas — 144p — et une bande-annonce de deux minutes
        // se termine avant que l'adaptation ait fini de monter. On regarde donc
        // un timbre-poste pendant tout le film qu'on envisageait de voir.
        // En garder deux laisse un repli aux connexions faibles tout en faisant
        // du deuxième meilleur format le pire cas.
        .take(2)
    val audios = tracks
        .filter { it.isAudio && it.container == "audio/mp4" && it.codecs.startsWith("mp4a") }
        .filter { it.initRange != null && it.indexRange != null }
        .sortedByDescending { it.bitrate }

    if (videos.isEmpty() || audios.isEmpty()) return null

    // La durée de référence est celle de la vidéo : l'audio dépasse souvent de
    // quelques dizaines de millisecondes, et annoncer la plus longue laisse le
    // lecteur attendre des segments d'image qui n'existent pas.
    val durationMs = videos.first().durationMs

    return buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append(
            """<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" """ +
                """profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" type="static" """ +
                """mediaPresentationDuration="${iso8601(durationMs)}" minBufferTime="PT1.5S">""",
        ).append('\n')
        append("  <Period>\n")
        appendAdaptationSet("video/mp4", videos)
        // Une seule représentation audio : le débit le plus élevé. Laisser le
        // lecteur adapter le son n'apporte rien sur deux minutes, et la liste
        // de YouTube contient des doublons (variantes à compression dynamique)
        // que rien ne distingue à l'écran.
        appendAdaptationSet("audio/mp4", audios.take(1))
        append("  </Period>\n")
        append("</MPD>\n")
    }
}

private fun StringBuilder.appendAdaptationSet(mimeType: String, tracks: List<YtTrack>) {
    append("""    <AdaptationSet mimeType="$mimeType" subsegmentAlignment="true">""").append('\n')
    tracks.forEach { appendRepresentation(it) }
    append("    </AdaptationSet>\n")
}

private fun StringBuilder.appendRepresentation(t: YtTrack) {
    append("""      <Representation id="${t.itag}" codecs="${t.codecs}" bandwidth="${t.bitrate}"""")
    if (t.isVideo) {
        append(""" width="${t.width}" height="${t.height}"""")
        if (t.fps > 0) append(""" frameRate="${t.fps}"""")
    } else if (t.audioSampleRate > 0) {
        append(""" audioSamplingRate="${t.audioSampleRate}"""")
    }
    append(">\n")
    if (t.isAudio && t.audioChannels > 0) {
        append(
            """        <AudioChannelConfiguration """ +
                """schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" """ +
                """value="${t.audioChannels}"/>""",
        ).append('\n')
    }
    // Les URLs googlevideo sont bourrées de `&` : sans échappement le manifeste
    // n'est pas du XML valide, et les deux lecteurs le rejettent en bloc.
    append("        <BaseURL>${escapeXml(t.url)}</BaseURL>\n")
    append("""        <SegmentBase indexRange="${t.indexRange!!.first}-${t.indexRange.last}">""")
        .append('\n')
    append("""          <Initialization range="${t.initRange!!.first}-${t.initRange.last}"/>""")
        .append('\n')
    append("        </SegmentBase>\n")
    append("      </Representation>\n")
}

/** Durée DASH : `PT183.975S`. */
private fun iso8601(ms: Long): String = "PT${ms / 1000}.${(ms % 1000).toString().padStart(3, '0')}S"

private fun escapeXml(s: String): String = s
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
