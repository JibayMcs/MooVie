package fr.moovie.tv.core.subtitles.usecase

/**
 * Convertit un SRT en **WebVTT**, le seul format que le récepteur Cast accepte.
 *
 * ## Pourquoi cette conversion existe
 *
 * OpenSubtitles sert du SRT, et le lecteur local s'en accommode : ExoPlayer
 * comme mpv les lisent tous les deux. Le récepteur média par défaut de Google,
 * lui, **ne lit que du WebVTT** — un SRT servi tel quel est accepté au
 * chargement puis simplement ignoré, sans erreur ni piste affichée. C'est le
 * genre de panne qu'on met une soirée à comprendre, faute du moindre message.
 *
 * ## Ce qui change réellement entre les deux
 *
 * Beaucoup moins qu'il n'y paraît. WebVTT **est** un SRT avec :
 *
 * - un en-tête `WEBVTT` ;
 * - un point au lieu d'une virgule dans les millisecondes ;
 * - les numéros de réplique facultatifs (on les garde : ils sont valides et
 *   les retirer ferait diverger de l'original sans rien gagner).
 *
 * Les balises `<i>`, `<b>` et `<u>` sont communes aux deux et passent telles
 * quelles. Le reste du texte n'est pas touché : réécrire des répliques serait
 * s'exposer à casser ce que le lecteur local affichait très bien.
 *
 * ## Le nettoyage du début
 *
 * Un fichier téléchargé commence souvent par une marque d'ordre d'octets, que
 * l'analyseur du récepteur prend pour du texte avant l'en-tête — et il refuse
 * alors le fichier entier. On la retire, comme les blancs de tête.
 */
fun srtToVtt(srt: String): String {
    val corps = srt
        .removePrefix("﻿")
        .trimStart()
        // Les fichiers d'OpenSubtitles arrivent volontiers en fins de ligne
        // Windows ; WebVTT les tolère, mais les normaliser évite d'avoir à s'en
        // remettre à cette tolérance.
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    // Déjà du WebVTT : ne rien faire vaut mieux que d'empiler un second en-tête,
    // ce qui invaliderait le fichier.
    if (corps.startsWith("WEBVTT")) return corps

    return "WEBVTT\n\n" + HORODATAGE.replace(corps) { m ->
        m.value.replace(',', '.')
    }
}

/**
 * Les horodatages, et **eux seuls**.
 *
 * Remplacer toutes les virgules du fichier par des points abîmerait les
 * répliques — une phrase française en contient à chaque ligne. Le motif ne
 * reconnaît donc que la forme `HH:MM:SS,mmm`, y compris au-delà de 99 heures,
 * que les très longs métrages atteignent.
 */
private val HORODATAGE = Regex("""\d{1,3}:\d{2}:\d{2},\d{1,3}""")
