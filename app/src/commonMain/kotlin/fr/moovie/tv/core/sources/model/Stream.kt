package fr.moovie.tv.core.sources.model

import kotlinx.serialization.Serializable

/** Type de flux extrait — détermine le pipeline de lecture à utiliser. */
enum class StreamFormat { HLS, DASH, MP4, UNKNOWN }

/**
 * Métadonnées d'un embed à résoudre : le lien d'hébergeur trouvé par un
 * catalogue. Sérialisable, car mis en cache sur disque.
 *
 * `hoster` se déduit toujours de l'URL, **jamais** du libellé affiché par le
 * site : cinestream sert « DdStream » depuis playmogo, « Filelions » depuis
 * minochinos et « netu » depuis waaw.to. Seul le domaine dit vrai.
 */
@Serializable
data class EmbedLink(
    val url: String,
    val hoster: String,
    /** VF / VOSTFR / VO — pilote la cascade selon la préférence des réglages. */
    val language: String? = null,
    /** Catalogue d'origine, renseigné à l'agrégation, pour la priorité. */
    val provider: String? = null,
    /**
     * Ce que la source déclare pour distinguer deux liens du **même hébergeur
     * dans la même langue** : doublage (« VF France », « VF Québec »), palier de
     * qualité (« Premium », « 1080p »)…
     *
     * Sans lui, la liste des sources affiche trois boutons « Vidzy » identiques
     * et l'utilisateur choisit à l'aveugle. C'est exactement le cas de
     * french-stream, dont les clés `vff` / `vfq` / `premium` étaient toutes
     * écrasées en « VF ». Null quand la source ne déclare rien — on n'invente
     * pas un critère qui n'existe pas.
     */
    val variant: String? = null,
)

/**
 * Un flux jouable résolu par un extracteur.
 *
 * @param url URL directe du manifeste ou du fichier (m3u8 / mpd / mp4).
 * @param headers en-têtes exigés par l'hébergeur (Referer, Origin, User-Agent).
 *        Ils ne sont pas décoratifs : ces CDN refusent toute requête sans eux,
 *        y compris la sonde de jouabilité.
 */
data class PlayableStream(
    val url: String,
    val format: StreamFormat,
    val headers: Map<String, String> = emptyMap(),
    val language: String? = null,
    val quality: String? = null,
    val subtitleUrls: Map<String, String> = emptyMap(),
)
