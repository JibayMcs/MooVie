package fr.moovie.tv.ui.player

import fr.moovie.tv.core.sources.model.EmbedLink
import fr.moovie.tv.core.sources.model.PlayableStream
import fr.moovie.tv.core.sources.usecase.qualityLabel
import fr.moovie.tv.data.sources.ExtractorRegistry
import fr.moovie.tv.ui.navigation.AltSource

/**
 * Ce que désigne une entrée du menu « Qualité ».
 *
 * Un identifiant textuel plutôt qu'un type scellé porté jusqu'à l'écran : la
 * chrome du lecteur ne manipule que des `PlayerTrack` (identifiant, libellé,
 * sélection), comme pour les sous-titres et l'audio. Le lecteur retraduit
 * l'identifiant en action, et chacun le fait à sa façon — c'est précisément ce
 * qui diffère entre ExoPlayer et libVLC.
 */
sealed interface QualityChoice {
    /** Laisser le lecteur adapter la définition au débit disponible. */
    data object Auto : QualityChoice

    /** Plafonner le flux **courant** à cette hauteur. */
    data class Height(val height: Int) : QualityChoice

    /** Basculer sur une autre source, qui sert une meilleure définition. */
    data class Source(val url: String) : QualityChoice

    companion object {
        fun parse(id: String): QualityChoice? = when {
            id == AUTO_ID -> Auto
            id.startsWith(HEIGHT_PREFIX) ->
                id.removePrefix(HEIGHT_PREFIX).toIntOrNull()?.let { Height(it) }
            id.startsWith(SOURCE_PREFIX) -> Source(id.removePrefix(SOURCE_PREFIX))
            else -> null
        }
    }
}

private const val AUTO_ID = "auto"
private const val HEIGHT_PREFIX = "h:"
private const val SOURCE_PREFIX = "src:"

/**
 * Les entrées du menu « Qualité », de la plus haute définition à la plus basse.
 *
 * Trois familles y cohabitent, et c'est voulu : ce qui intéresse celui qui
 * ouvre ce menu est **une définition**, pas la façon dont on l'obtient.
 *
 * 1. « Automatique », toujours en tête et sélectionnée par défaut. C'est le
 *    comportement normal d'un flux adaptatif, et le seul qui s'accommode d'une
 *    connexion qui varie.
 * 2. Les définitions du **flux courant**, quand il en annonce plusieurs.
 * 3. Les **autres sources**, avec leur nom, lorsqu'elles font mieux que ce que
 *    le flux courant sait servir. C'est le « en croisant les sources » : un
 *    hébergeur plafonné à 480p n'a rien à proposer, un autre a du 1080p.
 *
 * Une source de rechange n'est proposée que si elle **dépasse** le meilleur du
 * flux courant. En lister une moins bonne reviendrait à offrir de dégrader
 * l'image au prix d'une interruption, ce que personne ne cherche — et le menu
 * doublerait de longueur sans rien apporter.
 *
 * @param currentHeights définitions annoncées par le flux en cours, quelconque ordre.
 * @param selected choix actif, pour la coche.
 */
fun qualityOptions(
    currentHeights: List<Int>,
    alternatives: List<AltSource>,
    selected: QualityChoice,
): List<PlayerTrack> {
    val propres = currentHeights.filter { it > 0 }.distinct().sortedDescending()
    val meilleurPropre = propres.firstOrNull() ?: 0

    val ailleurs = alternatives
        .filter { it.height > meilleurPropre }
        .distinctBy { it.url }
        .sortedByDescending { it.height }

    // Rien à choisir : ni variante, ni source qui fasse mieux. La chrome écarte
    // les sections d'une seule entrée, mais autant ne pas la construire.
    if (propres.size < 2 && ailleurs.isEmpty()) return emptyList()

    return buildList {
        add(PlayerTrack(AUTO_ID, AUTO_LABEL, selected is QualityChoice.Auto))
        ailleurs.forEach { alt ->
            val etiquette = qualityLabel(alt.height) ?: "${alt.height}p"
            add(
                PlayerTrack(
                    id = SOURCE_PREFIX + alt.url,
                    // Le nom de la source n'est là que sur ces entrées : c'est
                    // ce qui explique qu'elles coupent brièvement la lecture.
                    label = "$etiquette · ${alt.hoster}",
                    selected = selected is QualityChoice.Source && selected.url == alt.url,
                ),
            )
        }
        // Les variantes du flux courant seulement s'il y en a **plusieurs** :
        // avec une seule, l'entrée ferait exactement ce que fait déjà
        // « Automatique », et deux entrées pour un même effet est le défaut
        // qu'on évite déjà en masquant les sections d'un seul choix.
        if (propres.size >= 2) {
            propres.forEach { h ->
                add(
                    PlayerTrack(
                        id = HEIGHT_PREFIX + h,
                        label = qualityLabel(h) ?: "${h}p",
                        selected = selected is QualityChoice.Height && selected.height == h,
                    ),
                )
            }
        }
    }
}

/**
 * Remplacé à l'affichage par la chaîne traduite : cette fonction est pure et ne
 * connaît pas les ressources. Voir `qualitySection` côté chrome.
 */
const val AUTO_LABEL = "@auto"

/**
 * Résout une source de rechange en flux jouable.
 *
 * Le lecteur ne connaît ni les catalogues ni TMDB — il reçoit une URL d'embed et
 * un hébergeur, et c'est tout ce qu'il faut : la résolution est exactement celle
 * de la cascade, réutilisée telle quelle plutôt que réécrite.
 *
 * Rend null si l'hébergeur ne répond pas ; l'appelant garde alors le flux en
 * cours, ce qui est le bon repli — on ne casse pas une lecture qui marche pour
 * une qualité qu'on n'a pas obtenue.
 */
suspend fun resolveAlternative(url: String, hoster: String, language: String): PlayableStream? =
    runCatching {
        ExtractorRegistry.resolve(EmbedLink(url = url, hoster = hoster, language = language))
    }.getOrNull()
