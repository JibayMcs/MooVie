package fr.moovie.tv.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.tmdb.MovieDetails
import fr.moovie.tv.data.tmdb.TvDetails
import fr.moovie.tv.data.tmdb.forCountry
import fr.moovie.tv.data.tmdb.formatDate
import fr.moovie.tv.data.tmdb.formatMoney
import fr.moovie.tv.data.tmdb.formatRuntime
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.info_certification
import fr.moovie.tv.resources.info_country
import fr.moovie.tv.resources.info_creator
import fr.moovie.tv.resources.info_director
import fr.moovie.tv.resources.info_episodes
import fr.moovie.tv.resources.info_first_air
import fr.moovie.tv.resources.info_language
import fr.moovie.tv.resources.info_last_air
import fr.moovie.tv.resources.info_music
import fr.moovie.tv.resources.info_network
import fr.moovie.tv.resources.info_next_episode
import fr.moovie.tv.resources.info_original_title
import fr.moovie.tv.resources.info_rating_votes
import fr.moovie.tv.resources.info_release
import fr.moovie.tv.resources.info_revenue
import fr.moovie.tv.resources.info_runtime
import fr.moovie.tv.resources.info_seasons_count
import fr.moovie.tv.resources.info_status
import fr.moovie.tv.resources.info_studios
import fr.moovie.tv.resources.info_writers
import fr.moovie.tv.resources.info_budget
import fr.moovie.tv.resources.info_rating
import fr.moovie.tv.resources.status_canceled
import fr.moovie.tv.resources.status_ended
import fr.moovie.tv.resources.status_in_production
import fr.moovie.tv.resources.status_planned
import fr.moovie.tv.resources.status_post_production
import fr.moovie.tv.resources.status_released
import fr.moovie.tv.resources.status_returning
import fr.moovie.tv.ui.adaptive.useBottomNav
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM

/**
 * Le panneau « En savoir plus » : ce que la fiche sait du titre et n'affiche
 * pas, faute de place, dans son en-tête.
 *
 * Il **remplace** le contenu sous le hero au lieu de s'y ajouter — le casting
 * sur un film, la liste des épisodes sur une série. C'est ce qui permet d'y
 * aller et d'en revenir d'un seul appui : sur une fiche de série, on consulte
 * une date de diffusion puis on veut immédiatement retrouver ses épisodes, et
 * une section de plus en bas de page aurait imposé un aller-retour au défilement.
 *
 * **Une ligne vide n'est pas affichée.** TMDB laisse énormément de champs à
 * blanc — budget d'un film indépendant, classification hors États-Unis, chaîne
 * d'une série ancienne — et une colonne de « Non renseigné » ne renseigne
 * personne. Le panneau se contente donc de ce qui existe, quitte à être court.
 */
@Composable
fun MovieInfoPanel(
    details: MovieDetails,
    country: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    val credits = details.credits
    InfoList(
        modifier = modifier,
        scrollable = scrollable,
        entries = listOf(
            // Le titre original n'est montré que s'il diffère : le répéter à
            // l'identique sous le titre affiché n'apprend rien.
            Res.string.info_original_title to
                details.originalTitle.takeIf { it.isNotBlank() && it != details.title },
            Res.string.info_director to credits?.directors?.joinDisplay(),
            Res.string.info_writers to credits?.writers?.joinDisplay(),
            Res.string.info_music to credits?.composers?.joinDisplay(),
            Res.string.info_release to formatDate(details.releaseDate),
            Res.string.info_runtime to formatRuntime(details.runtime),
            Res.string.info_certification to details.releaseDates.forCountry(country),
            Res.string.info_status to details.status.translatedStatus(),
            Res.string.info_rating to details.ratingLine(),
            Res.string.info_budget to formatMoney(details.budget),
            Res.string.info_revenue to formatMoney(details.revenue),
            Res.string.info_studios to details.companies.map { it.name }.joinDisplay(),
            Res.string.info_country to details.countries.map { it.name }.joinDisplay(),
            Res.string.info_language to details.spokenLanguages.map { it.name }.joinDisplay(),
        ),
    )
}

@Composable
fun TvInfoPanel(
    details: TvDetails,
    country: String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    val credits = details.credits
    val prochain = details.nextEpisode?.let { ep ->
        val date = formatDate(ep.airDate) ?: return@let null
        // « S2 · E4 — 12/09/2026 » : le numéro d'abord, c'est ce qu'on cherche.
        buildString {
            append("S${ep.seasonNumber} · E${ep.episodeNumber}")
            if (ep.name.isNotBlank()) append(" — ${ep.name}")
            append("  ·  $date")
        }
    }
    InfoList(
        modifier = modifier,
        scrollable = scrollable,
        entries = listOf(
            Res.string.info_original_title to
                details.originalName.takeIf { it.isNotBlank() && it != details.name },
            Res.string.info_creator to details.createdBy.map { it.name }.joinDisplay(),
            Res.string.info_music to credits?.composers?.joinDisplay(),
            Res.string.info_status to details.status.translatedStatus(),
            // En tête des dates : sur une série en cours, c'est l'information
            // qu'on vient chercher.
            Res.string.info_next_episode to prochain,
            Res.string.info_first_air to formatDate(details.firstAirDate),
            Res.string.info_last_air to formatDate(details.lastAirDate),
            Res.string.info_seasons_count to details.numberOfSeasons.takeIf { it > 0 }?.toString(),
            Res.string.info_episodes to details.numberOfEpisodes.takeIf { it > 0 }?.toString(),
            Res.string.info_runtime to formatRuntime(details.episodeRunTime.firstOrNull()),
            Res.string.info_certification to details.contentRatings.forCountry(country),
            Res.string.info_rating to details.ratingLine(),
            Res.string.info_network to details.networks.map { it.name }.joinDisplay(),
            Res.string.info_studios to details.companies.map { it.name }.joinDisplay(),
            Res.string.info_country to details.originCountry.joinDisplay(),
        ),
    )
}

/**
 * @param scrollable **jamais vrai sous un parent qui défile déjà.** Deux
 *   défilements verticaux imbriqués mesurent l'enfant avec une hauteur infinie,
 *   et Compose lève `IllegalStateException`.
 *
 *   C'est une propriété de **l'endroit où l'on pose le panneau**, pas du
 *   panneau : le même `TvInfoPanel` doit défiler quand il remplace la liste des
 *   épisodes — seul élément défilant d'une fiche de série — et ne pas défiler
 *   sur la fiche d'un épisode, dont la page défile en bloc. L'avoir fixé dans
 *   le composable a fait tomber l'application deux fois, la seconde en
 *   réutilisant le panneau ailleurs. C'est à l'appelant de le dire.
 */
@Composable
private fun InfoList(
    entries: List<Pair<StringResource, String?>>,
    scrollable: Boolean,
    modifier: Modifier = Modifier,
) {
    val visibles = entries.filter { !it.second.isNullOrBlank() }
    val scroll = rememberScrollState()
    Column(
        modifier = modifier.then(if (scrollable) Modifier.verticalScroll(scroll) else Modifier),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        visibles.forEach { (label, valeur) -> InfoRow(stringResource(label), valeur!!) }
    }
}

/**
 * Une ligne « intitulé / valeur ».
 *
 * Les deux colonnes se partagent la largeur au pointeur et à la télécommande, et
 * **s'empilent au doigt** : sous 400 dp, une colonne d'intitulés fixe ne laisse
 * plus à la valeur qu'une bande où « Production » se coupe mot par mot. C'est la
 * même règle que `SettingRow`, pour la même raison.
 */
@Composable
private fun InfoRow(label: String, value: String) {
    val labelStyle = MaterialTheme.typography.labelMedium
    val valueStyle = MaterialTheme.typography.bodyMedium

    if (useBottomNav) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = labelStyle, color = LABEL_COLOR, fontWeight = FontWeight.Medium)
            Text(value, style = valueStyle, color = VALUE_COLOR)
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                style = labelStyle,
                color = LABEL_COLOR,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(160.dp).padding(end = 12.dp),
            )
            Text(value, style = valueStyle, color = VALUE_COLOR, modifier = Modifier.widthIn(max = 620.dp))
        }
    }
}

/**
 * Note et nombre de votes sur la même ligne.
 *
 * Le nombre de votes n'est pas décoratif : 7,5 sur douze votes et 7,5 sur
 * quarante mille ne disent pas la même chose. Aucune note du tout quand personne
 * n'a voté — « 0,0 » se lirait comme un très mauvais film.
 */
@Composable
private fun MovieDetails.ratingLine(): String? = ratingLine(voteAverage, voteCount)

@Composable
private fun TvDetails.ratingLine(): String? = ratingLine(voteAverage, voteCount)

@Composable
private fun ratingLine(average: Double, count: Int): String? {
    if (count <= 0 || average <= 0.0) return null
    // Une décimale, virgule décimale : « 7,5 » et non « 7.543478260869565 ».
    val note = ((average * 10).toInt() / 10.0).toString().replace('.', ',')
    return stringResource(Res.string.info_rating_votes, note, count.toString())
}

private fun List<String>.joinDisplay(): String? =
    filter { it.isNotBlank() }.distinct().takeIf { it.isNotEmpty() }?.joinToString(" · ")

/**
 * Statut TMDB traduit. Les valeurs sont en anglais dans l'API quelle que soit la
 * langue demandée — « Returning Series » s'afficherait tel quel sinon.
 */
@Composable
private fun String.translatedStatus(): String? = when (lowercase()) {
    "returning series" -> stringResource(Res.string.status_returning)
    "ended" -> stringResource(Res.string.status_ended)
    "canceled", "cancelled" -> stringResource(Res.string.status_canceled)
    "in production" -> stringResource(Res.string.status_in_production)
    "planned" -> stringResource(Res.string.status_planned)
    "released" -> stringResource(Res.string.status_released)
    "post production" -> stringResource(Res.string.status_post_production)
    // Statut inconnu de cette liste : on le montre brut plutôt que de le taire.
    // TMDB en ajoute, et une ligne absente serait plus déroutante qu'un mot
    // anglais.
    else -> takeIf { it.isNotBlank() }
}

private val LABEL_COLOR = MOOVIE_TEXT_DIM
private val VALUE_COLOR = Color(0xFFE8E8E8)
