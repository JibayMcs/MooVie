package fr.moovie.tv.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import fr.moovie.tv.data.watch.HistoryEntry
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.history_empty
import fr.moovie.tv.resources.history_day_pattern
import fr.moovie.tv.resources.history_episodes
import fr.moovie.tv.resources.history_movies
import fr.moovie.tv.resources.history_remove
import fr.moovie.tv.resources.history_stat_month_genre
import fr.moovie.tv.resources.history_stat_top_series
import fr.moovie.tv.resources.history_stat_year
import fr.moovie.tv.resources.history_title
import fr.moovie.tv.resources.history_views
import fr.moovie.tv.resources.history_today
import fr.moovie.tv.resources.history_unknown_title
import fr.moovie.tv.resources.history_yesterday
import fr.moovie.tv.resources.mark_unwatched
import fr.moovie.tv.resources.watchlist_open
import fr.moovie.tv.ui.components.LocalMoovieFocusMemory
import fr.moovie.tv.ui.components.MOOVIE_ACCENT
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieCard
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieMarqueeText
import java.text.SimpleDateFormat
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Colonnes de la grille. Six tient en 1080p sans réduire les vignettes à rien. */
private const val COLUMNS = 6

/**
 * Page d'historique, partagée TV + desktop : une grille groupée par jour, et
 * au-dessus trois cartes de statistiques — masquées par défaut (réglage
 * « Masquer les widgets »), auquel cas [stats] arrive à `null`.
 *
 * Les cartes de stats ne sont volontairement pas focalisables : entre l'en-tête
 * et la grille, elles piégeraient le D-pad sur des tuiles purement décoratives.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreenContent(
    days: List<HistoryDay>,
    stats: HistoryStats?,
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onMarkUnwatched: (String) -> Unit,
    onBack: () -> Unit = {},
    // Desktop uniquement : sur TV, la télécommande a sa touche Retour et un
    // bouton à l'écran ne ferait que voler le focus à la première vignette.
    showBackButton: Boolean = false,
) {
    var menuFor by remember { mutableStateOf<HistoryEntry?>(null) }
    // Voir MoovieFocusMemory : sans ça, fermer le menu renvoyait le focus
    // en haut de page et obligeait à re-défiler jusqu'à la vignette visée.
    val focusMemory = LocalMoovieFocusMemory.current
    // Le focus arrive sur la 1re vignette : sans ça il resterait nulle part et
    // le premier appui sur le D-pad serait perdu. L'historique arrive après la
    // première composition (flux DataStore) : on retente tant que la grille
    // n'est pas encore posée, sinon la demande tombe dans le vide.
    val firstCard = remember { FocusRequester() }
    LaunchedEffect(days.isNotEmpty()) {
        if (days.isEmpty()) return@LaunchedEffect
        repeat(10) {
            if (runCatching { firstCard.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        Column(modifier = Modifier.fillMaxSize().padding(vertical = 32.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (showBackButton) {
                    MoovieIconButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.common_back),
                    )
                }
                Text(
                    stringResource(Res.string.history_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Spacer(Modifier.height(16.dp))

            if (days.isEmpty()) {
                Text(
                    stringResource(Res.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF9A9A9A),
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                return@Column
            }

            // Marges horizontales dans le contentPadding : sinon le zoom au
            // focus des vignettes de bord est rogné par le conteneur.
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (stats != null) {
                    item(key = "stats") { StatsRow(stats) }
                }
                days.forEachIndexed { dayIndex, day ->
                    stickyHeader(key = "day-${day.dayStart}") { DayHeader(day) }
                    itemsIndexed(
                        items = day.entries.chunked(COLUMNS),
                        key = { _, row -> row.first().key },
                    ) { rowIndex, row ->
                        HistoryGridRow(
                            entries = row,
                            onOpen = { onOpenTitle(it.tmdbId, it.isTv) },
                            onMenu = { menuFor = it },
                            // Toute première vignette de la page.
                            firstFocus = if (dayIndex == 0 && rowIndex == 0) firstCard else null,
                        )
                    }
                }
            }
        }

        menuFor?.let { entry ->
            HistoryMenuDialog(
                entry = entry,
                onDismiss = { menuFor = null; focusMemory.restore() },
                onOpen = { onOpenTitle(entry.tmdbId, entry.isTv) },
                onRemove = { onRemove(entry.key) },
                onMarkUnwatched = { onMarkUnwatched(entry.key) },
            )
        }
    }
}

/** En-tête collant d'un jour : « Aujourd'hui », « Hier », ou la date en clair. */
@Composable
private fun DayHeader(day: HistoryDay) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0A))
            .padding(vertical = 6.dp),
    ) {
        Text(dayLabel(day), style = MaterialTheme.typography.titleLarge, color = MOOVIE_ACCENT)
    }
}

/**
 * Libellé d'un jour. Le motif de date vit dans les traductions : l'ordre des
 * mots change d'une langue à l'autre (« 28 juillet » / « July 28 »).
 */
@Composable
private fun dayLabel(day: HistoryDay): String = when (day.relative) {
    RelativeDay.TODAY -> stringResource(Res.string.history_today)
    RelativeDay.YESTERDAY -> stringResource(Res.string.history_yesterday)
    RelativeDay.OLDER -> {
        val pattern = stringResource(Res.string.history_day_pattern)
        remember(day.dayStart, pattern) {
            SimpleDateFormat(pattern, Locale.getDefault())
                .format(Date(day.dayStart))
                // Les jours et mois sont en minuscules en français : en tête de
                // section, une majuscule se lit mieux.
                .replaceFirstChar { it.uppercase() }
        }
    }
}

/** Une ligne de la grille, complétée par du vide pour garder la largeur des cartes. */
@Composable
private fun HistoryGridRow(
    entries: List<HistoryEntry>,
    onOpen: (HistoryEntry) -> Unit,
    onMenu: (HistoryEntry) -> Unit,
    firstFocus: FocusRequester? = null,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, entry ->
            HistoryCard(
                entry = entry,
                onClick = { onOpen(entry) },
                onLongClick = { onMenu(entry) },
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (index == 0 && firstFocus != null) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        repeat(COLUMNS - entries.size) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * Vignette d'un visionnage. L'image est celle consignée au moment de la lecture
 * (photo d'épisode ou image large du titre), donc en 16/9 comme le rail
 * « Reprendre » — pas une affiche.
 */
@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoovieCard(onClick = onClick, onLongClick = onLongClick, focusedScale = 1.08f, modifier = modifier) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF222222)),
            ) {
                AsyncImage(
                    model = entry.imageUrl,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.padding(8.dp)) {
                MoovieMarqueeText(
                    // Un titre marqué vu sans avoir jamais été lu n'a pas de
                    // métadonnées : la date reste, le nom manque.
                    text = entry.title.ifBlank { stringResource(Res.string.history_unknown_title) },
                    style = MaterialTheme.typography.bodySmall,
                )
                entry.episodeLabel?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9A9A9A))
                }
            }
        }
    }
}

/**
 * Bandeau des cartes de stats. Purement informatif, jamais focalisable — et une
 * carte sans donnée ne s'affiche pas du tout : un tiret dans un cadre vide
 * n'apprend rien, l'absence de carte si.
 */
@Composable
private fun StatsRow(stats: HistoryStats) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        if (stats.monthGenre != null) {
            StatCard(
                title = stringResource(Res.string.history_stat_month_genre),
                value = stats.monthGenre,
                detail = pluralStringResource(
                    Res.plurals.history_views,
                    stats.monthGenreCount,
                    stats.monthGenreCount,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        if (stats.yearEpisodes > 0 || stats.yearMovies > 0) {
            StatCard(
                title = stringResource(Res.string.history_stat_year),
                value = pluralStringResource(
                    Res.plurals.history_episodes,
                    stats.yearEpisodes,
                    stats.yearEpisodes,
                ) + " · " + pluralStringResource(
                    Res.plurals.history_movies,
                    stats.yearMovies,
                    stats.yearMovies,
                ),
                detail = stats.yearGenre,
                modifier = Modifier.weight(1f),
            )
        }
        if (stats.topSeriesTitle != null) {
            StatCard(
                title = stringResource(Res.string.history_stat_top_series),
                value = stats.topSeriesTitle,
                detail = pluralStringResource(
                    Res.plurals.history_episodes,
                    stats.topSeriesEpisodes,
                    stats.topSeriesEpisodes,
                ),
                imageUrl = stats.topSeriesImageUrl,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    detail: String?,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF161616))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(64.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color(0xFF9A9A9A))
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9A9A9A))
            }
        }
    }
}

/** Menu d'appui long : ouvrir la fiche, retirer la ligne, ou revenir en non-vu. */
@Composable
private fun HistoryMenuDialog(
    entry: HistoryEntry,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onMarkUnwatched: () -> Unit,
) {
    val firstAction = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF5161616))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                entry.title.ifBlank { stringResource(Res.string.history_unknown_title) } +
                    (entry.episodeLabel?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
            )
            MoovieButton(
                onClick = { onOpen(); onDismiss() },
                modifier = Modifier.fillMaxWidth().focusRequester(firstAction),
            ) {
                Text(stringResource(Res.string.watchlist_open))
            }
            MoovieButton(onClick = { onRemove(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(Res.string.history_remove))
            }
            MoovieButton(onClick = { onMarkUnwatched(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(Res.string.mark_unwatched))
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { firstAction.requestFocus() } }
}
