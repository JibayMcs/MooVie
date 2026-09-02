package fr.moovie.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import fr.moovie.tv.core.format.formatDuration
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.currentTmdbLanguage
import fr.moovie.tv.data.tmdb.Episode
import fr.moovie.tv.data.tmdb.TmdbRepository
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.details_tab_episodes
import fr.moovie.tv.ui.components.MoovieAsyncImage
import fr.moovie.tv.ui.components.MoovieCard
import fr.moovie.tv.ui.theme.ESPACE
import fr.moovie.tv.ui.theme.ESPACE_SERRE
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.theme.MOOVIE_SURFACE
import fr.moovie.tv.ui.theme.MOOVIE_TEXT
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_MUTED
import fr.moovie.tv.ui.theme.MoovieShape
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource

/**
 * La liste des épisodes, en panneau glissant par-dessus la lecture.
 *
 * ## Ce qu'il remplace
 *
 * Deux flèches — épisode précédent, épisode suivant — posées au milieu du
 * transport. Elles fonctionnaient, mais **à l'aveugle** : rien n'y disait où
 * l'on en était, combien il en restait, ni ce que le suivant racontait ; et
 * rejoindre le troisième épisode d'une autre saison demandait de quitter le
 * lecteur, revenir à la fiche, changer de saison, rouvrir. Deux boutons qui
 * ne savent que « d'un cran » ne peuvent pas répondre à « lequel ».
 *
 * Un panneau le peut : il montre la saison, la liste, et marque celui qui joue.
 * Un geste de plus pour l'épisode suivant, beaucoup moins pour tous les autres.
 *
 * ## Pourquoi il charge lui-même ses données
 *
 * On arrive au lecteur par cinq chemins — la fiche, la reprise de l'accueil, un
 * téléchargement, l'écran de diffusion, une notification — et un seul d'entre
 * eux vient d'un écran qui connaît la série. Faire descendre la liste jusqu'ici
 * aurait voulu dire l'ajouter à `Screen.Player`, donc la porter dans les trois
 * racines de plateforme et dans tous les appels qui construisent un lecteur,
 * pour une donnée dont on n'a besoin que si l'on ouvre ce panneau.
 *
 * Il a l'identifiant TMDB dans sa clé de média : une requête au moment de
 * l'ouverture coûte moins cher que ce plombage, et ne coûte rien du tout tant
 * que personne n'ouvre le panneau.
 *
 * ## Le focus
 *
 * Il va sur l'épisode en cours, pas en tête de liste : c'est de là qu'on veut
 * repartir. Même règle que le menu des filtres et que les options du lecteur.
 */
@Composable
fun BoxScope.PlayerEpisodesPanel(
    visible: Boolean,
    tmdbId: Int,
    saisonCourante: Int,
    episodeCourant: Int,
    onJouer: (season: Int, episode: Int) -> Unit,
    onFermer: () -> Unit,
) {
    // Le voile de fermeture : un appui hors du panneau le referme. Au pointeur
    // seulement — la télécommande n'a pas de « hors du panneau », elle a Retour.
    if (visible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .pointerInput(Unit) { detectTapGestures { onFermer() } },
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = Modifier.align(Alignment.CenterEnd),
    ) {
        ContenuPanneau(
            tmdbId = tmdbId,
            saisonCourante = saisonCourante,
            episodeCourant = episodeCourant,
            onJouer = onJouer,
        )
    }
}

@Composable
private fun ContenuPanneau(
    tmdbId: Int,
    saisonCourante: Int,
    episodeCourant: Int,
    onJouer: (season: Int, episode: Int) -> Unit,
) {
    // **Toutes les saisons dans une seule liste, et non un sélecteur.**
    //
    // Le panneau a d'abord eu une barre de saisons, comme la fiche. Mais on
    // n'ouvre pas cette liste-ci pour explorer une série : on l'ouvre pendant
    // qu'elle joue, pour aller à un épisode précis — souvent le suivant, qui est
    // parfois le premier de la saison d'après. Un sélecteur y ajoute un choix
    // avant le choix, et cache le passage d'une saison à l'autre derrière un
    // geste. Déroulée, la liste montre la série entière et la frontière entre
    // deux saisons devient une ligne qu'on franchit.
    //
    // Le prix est une requête par saison. Elles partent dans l'ordre et la liste
    // se remplit au fur et à mesure : on ne regarde pas un écran vide en
    // attendant la vingtième.
    var saisons by remember { mutableStateOf<List<Int>>(emptyList()) }
    var parSaison by remember { mutableStateOf<Map<Int, List<Episode>>>(emptyMap()) }

    LaunchedEffect(tmdbId) {
        val cle = SettingsRepository().tmdbApiKey.first()
        val repo = TmdbRepository(currentTmdbLanguage())
        val details = runCatching { repo.tvDetails(cle, tmdbId) }.getOrNull() ?: return@LaunchedEffect
        // La saison 0 est celle des hors-séries chez TMDB : elle n'a pas sa place
        // dans une liste dont on se sert pour suivre une histoire.
        val numeros = details.seasons.map { it.seasonNumber }.filter { it > 0 }.sorted()
        saisons = numeros
        // La saison en cours d'abord : c'est celle qu'on vient consulter neuf
        // fois sur dix, et la faire attendre derrière les précédentes ferait
        // regarder un chargement pour rien.
        val ordre = numeros.sortedBy { if (it == saisonCourante) -1 else it }
        ordre.forEach { numero ->
            runCatching { repo.season(cle, tmdbId, numero) }.onSuccess { saison ->
                parSaison = parSaison + (numero to saison.episodes)
            }
        }
    }

    // Aplatie dans l'ordre des saisons, quel que soit l'ordre d'arrivée des
    // réponses : la liste ne doit pas se réorganiser sous les yeux.
    val lignes = remember(saisons, parSaison) {
        saisons.flatMap { numero ->
            parSaison[numero].orEmpty().map { numero to it }
        }
    }

    val listeEtat = rememberLazyListState()
    val focusEntree = remember { FocusRequester() }
    // Où le focus se pose en entrant : sur l'épisode qui joue. **Toujours
    // quelque part** — la première version ne le demandait qu'au-delà du premier
    // épisode, si bien qu'un pilote en cours ouvrait un panneau que la
    // télécommande ne pouvait pas atteindre.
    val indexEntree = lignes
        .indexOfFirst { (saison, ep) ->
            saison == saisonCourante && ep.episodeNumber == episodeCourant
        }
        .coerceAtLeast(0)
    // On l'amène à l'écran **avant** de lui demander le focus : une cible qui
    // n'est pas composée n'existe pas pour Compose, et la demande échouerait en
    // silence sur une série de cent épisodes.
    LaunchedEffect(lignes.size) {
        if (lignes.isEmpty()) return@LaunchedEffect
        if (indexEntree > 0) runCatching { listeEtat.scrollToItem(indexEntree) }
        runCatching { focusEntree.requestFocus() }
    }

    Column(
        modifier = Modifier
            // Un plafond, comme le panneau des sources : sur une fenêtre étroite
            // un panneau à largeur fixe déborderait, et sur un téléviseur il
            // couvrirait la moitié de l'image qu'on est en train de regarder.
            .widthIn(max = LARGEUR_PANNEAU)
            .fillMaxHeight()
            .background(MOOVIE_SURFACE)
            .padding(vertical = ESPACE, horizontal = ESPACE),
        verticalArrangement = Arrangement.spacedBy(ESPACE_SERRE),
    ) {
        Text(
            stringResource(Res.string.details_tab_episodes),
            style = MaterialTheme.typography.titleLarge,
            color = MOOVIE_TEXT,
            modifier = Modifier.padding(bottom = ESPACE_SERRE),
        )

        LazyColumn(
            state = listeEtat,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(ESPACE_SERRE),
        ) {
            itemsIndexed(lignes, key = { _, (s, ep) -> "$s-${ep.episodeNumber}" }) { index, ligne ->
                val (saison, episode) = ligne
                LigneEpisode(
                    saison = saison,
                    episode = episode,
                    enCours = saison == saisonCourante && episode.episodeNumber == episodeCourant,
                    onClick = { onJouer(saison, episode.episodeNumber) },
                    modifier = if (index == indexEntree) {
                        Modifier.focusRequester(focusEntree)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/**
 * Une ligne : la vignette, le repère saison/épisode, le nom, la durée.
 *
 * **Le repère « S1 · E3 » est en tête, pas déduit de la position.** La liste
 * couvre toute la série : sans lui, deux épisodes numéro 1 se suivraient à la
 * frontière de deux saisons sans que rien ne dise laquelle est laquelle — et
 * c'est précisément là qu'on vient chercher quelque chose.
 *
 * L'épisode en cours porte un filet d'accent à gauche plutôt qu'un fond plein :
 * le fond est déjà la marque du focus, et deux surfaces pleines côte à côte ne
 * se distinguent plus à trois mètres.
 */
@Composable
private fun LigneEpisode(
    saison: Int,
    episode: Episode,
    enCours: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoovieCard(onClick = onClick, focusedScale = 1.02f, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(ESPACE_SERRE),
            horizontalArrangement = Arrangement.spacedBy(ESPACE_SERRE),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (enCours) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(HAUTEUR_VIGNETTE)
                        .background(MOOVIE_ACCENT),
                )
            }
            MoovieAsyncImage(
                model = episode.stillUrl(),
                contentDescription = null,
                modifier = Modifier
                    .height(HAUTEUR_VIGNETTE)
                    .aspectRatio(16f / 9f)
                    .background(MOOVIE_SURFACE, MoovieShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ESPACE_SERRE)) {
                    Text(
                        "S$saison · E${episode.episodeNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (enCours) MOOVIE_ACCENT else MOOVIE_TEXT_DIM,
                    )
                    formatDuration(episode.runtime)?.let { duree ->
                        Text(
                            duree,
                            style = MaterialTheme.typography.labelSmall,
                            color = MOOVIE_TEXT_DIM,
                        )
                    }
                }
                Text(
                    episode.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (enCours) FontWeight.Bold else FontWeight.Normal,
                    color = if (enCours) MOOVIE_TEXT else MOOVIE_TEXT_MUTED,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(ESPACE_SERRE))
        }
    }
}

/**
 * Largeur du panneau. Assez pour deux lignes de titre à côté d'une vignette,
 * pas assez pour couvrir la moitié d'un téléviseur : on choisit un épisode en
 * regardant le film, pas à sa place.
 */
private val LARGEUR_PANNEAU = 400.dp

/** Hauteur de la vignette. Un 16:9 de 54 dp reste reconnaissable à trois mètres. */
private val HAUTEUR_VIGNETTE = 54.dp
