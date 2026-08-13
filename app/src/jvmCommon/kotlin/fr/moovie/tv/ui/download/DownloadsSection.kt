package fr.moovie.tv.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import fr.moovie.tv.data.download.downloadPoster
import fr.moovie.tv.data.download.fetchDownloadPoster
import java.io.File
import fr.moovie.tv.ui.components.MoovieAsyncImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.moovie.tv.resources.downloads_group
import fr.moovie.tv.resources.downloads_group_running
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.download.Download
import fr.moovie.tv.data.download.DownloadQueue
import fr.moovie.tv.data.download.DownloadRepository
import fr.moovie.tv.data.download.DownloadState
import fr.moovie.tv.data.download.moovieDownloadsDir
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.downloads_empty
import fr.moovie.tv.resources.downloads_failed
import fr.moovie.tv.resources.downloads_help
import fr.moovie.tv.resources.downloads_paused
import fr.moovie.tv.resources.downloads_play
import fr.moovie.tv.resources.downloads_pause
import fr.moovie.tv.resources.downloads_queued
import fr.moovie.tv.resources.downloads_ready
import fr.moovie.tv.resources.downloads_remove
import fr.moovie.tv.resources.downloads_resume
import fr.moovie.tv.resources.downloads_used
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.theme.MoovieShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private val DIM = Color(0xFF9A9A9A)
private val PANEL = Color(0xFF1A1A1A)

/**
 * Les téléchargements : ce qu'ils occupent, et de quoi les lire.
 *
 * La liste montre d'abord des tailles plutôt que des affiches, parce qu'on y
 * vient surtout pour faire de la place. Mais elle porte aussi **le seul chemin
 * de lecture qui ne dépende pas de TMDB** : hors ligne, la fiche d'un titre ne
 * charge pas, et renvoyer vers elle pour ouvrir un fichier déjà sur le disque
 * exigerait le réseau pour s'en passer.
 */
@Composable
fun DownloadsSection(
    /**
     * Lance la lecture depuis la liste.
     *
     * Ce chemin est le seul qui ne dépende pas de TMDB : hors ligne la fiche
     * d'un titre ne charge pas, donc y renvoyer pour lire un fichier qui est
     * déjà sur le disque reviendrait à exiger le réseau pour s'en passer.
     */
    onPlay: (Download) -> Unit = {},
    /**
     * Ne garde que les titres qui contiennent ce texte. Vide = tout.
     *
     * C'est la recherche hors ligne : sans réseau, chercher veut dire chercher
     * dans ce qu'on possède, et la liste qui le contient est déjà ici.
     */
    filter: String = "",
) {
    val repo = remember { DownloadRepository() }
    val toutes by repo.downloads.collectAsState(initial = emptyList())
    // Sur le titre **et** le sous-titre : « S1 · E7 » est ce qu'on tape quand on
    // cherche un épisode précis dans une série qu'on a entièrement téléchargée.
    val downloads = remember(toutes, filter) {
        val terme = filter.trim().lowercase()
        if (terme.isEmpty()) {
            toutes
        } else {
            toutes.filter {
                it.title.lowercase().contains(terme) || it.subtitle.lowercase().contains(terme)
            }
        }
    }
    val scope = rememberCoroutineScope()

    // Mesuré sur le disque, et recalculé à chaque changement de la liste : un
    // téléchargement interrompu par une coupure laisse des octets que personne
    // n'a comptés, et c'est quand le disque se remplit que le chiffre doit être
    // juste.
    val used by produceState(0L, downloads.size) {
        value = withContext(Dispatchers.IO) { repo.bytesOnDisk() }
    }

    // Ce que le volume porte en tout. Relevé avec la taille occupée et sur le
    // même fil : `usableSpace` interroge le système de fichiers, ce qui n'a rien
    // à faire sur le fil d'interface.
    val storage by produceState(StorageUsage(0L, 0L, 0L), used) {
        value = withContext(Dispatchers.IO) { storageUsage(moovieDownloadsDir(), used) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.downloads_help), style = MaterialTheme.typography.bodySmall, color = DIM)
        Text(
            stringResource(Res.string.downloads_used, formatSize(used)),
            style = MaterialTheme.typography.titleMedium,
        )
        // « 30 Go occupés » ne dit pas s'il en reste : la barre répond à la
        // question qu'on se pose vraiment ici, celle de la place. Voir StorageBar.
        StorageBar(storage)

        if (downloads.isEmpty()) {
            Text(stringResource(Res.string.downloads_empty), style = MaterialTheme.typography.bodyMedium, color = DIM)
            return@Column
        }

        // Regroupé par série : six épisodes de la même série faisaient six
        // entrées indiscernables, dont le titre ne différait que par un « S2 ·
        // E3 » noyé au milieu de la ligne. Un film reste seul — le grouper avec
        // lui-même n'ajouterait qu'un pli à ouvrir.
        //
        // La clé de groupe se lit dans `key` (`tv:1396:s1e1`), sans rien
        // ajouter au modèle : c'est déjà l'identité du titre, l'épisode n'en
        // est qu'un suffixe.
        downloads.groupBy { it.groupKey() }.forEach { (_, items) ->
            if (items.size == 1) {
                val download = items.first()
                DownloadRow(
                    download = download,
                    onPlay = { onPlay(download) },
                    onPause = { DownloadQueue.pause(download.key) },
                    onResume = { scope.launch { DownloadQueue.resumePending() } },
                    onRemove = { scope.launch { DownloadQueue.remove(download.key) } },
                )
            } else {
                SeriesGroup(
                    items = items,
                    onPlay = onPlay,
                    scope = scope,
                )
            }
        }
    }
}

/**
 * Identité du titre, épisode exclu.
 *
 * `tv:1396:s1e1` et `tv:1396:s1e2` partagent `tv:1396` ; un film garde sa clé
 * entière et reste donc seul dans son groupe.
 *
 * **Lu dans la clé, pas dans `isTv`.** Le champ est facultatif et le bouton du
 * lecteur l'oubliait : ses téléchargements retombaient sur le défaut `false`,
 * donc sur leur clé entière, et chaque épisode formait un groupe d'un seul —
 * onze épisodes groupés d'un côté, quatre lignes isolées de l'autre, pour la
 * même série. La clé, elle, est l'identité : elle ne peut pas être oubliée.
 * C'est déjà ainsi que `WatchProgressRepository` remonte d'un épisode à son
 * titre, et ça répare au passage les enregistrements déjà écrits.
 */
internal fun Download.groupKey(): String =
    if (key.startsWith("tv:")) key.split(':').take(2).joinToString(":") else key

/**
 * Une série et ses épisodes, repliés sous une seule entrée.
 *
 * Dépliée par défaut quand quelque chose y tourne : on vient justement voir où
 * ça en est, et refermer l'information qu'on cherche serait absurde. Repliée
 * sinon, ce qui rend la liste lisible quand on a téléchargé trois saisons.
 */
@Composable
private fun SeriesGroup(
    items: List<Download>,
    onPlay: (Download) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val running = items.count {
        it.state == DownloadState.RUNNING || it.state == DownloadState.QUEUED
    }
    var expanded by remember(items.first().groupKey()) { mutableStateOf(running > 0) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MoovieButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            // Hauteur fixe ici : l'en-tête d'une série n'a qu'une ligne et
            // demie, et lui imposer la hauteur intrinsèque d'un bouton
            // l'étirerait sans rien apporter. Même largeur que les rangées du
            // dessous, pour que la colonne d'affiches reste alignée.
            Affiche(items.first(), Modifier.height(AFFICHE_ENTETE))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                TitreDefilant(
                    items.first().title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (running > 0) {
                        stringResource(
                            Res.string.downloads_group_running,
                            items.size.toString(),
                            running.toString(),
                        )
                    } else {
                        stringResource(Res.string.downloads_group, items.size.toString())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = DIM,
                )
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
        }
        if (expanded) {
            items.sortedBy { it.key }.forEach { download ->
                DownloadRow(
                    download = download,
                    onPlay = { onPlay(download) },
                    onPause = { DownloadQueue.pause(download.key) },
                    onResume = { scope.launch { DownloadQueue.resumePending() } },
                    onRemove = { scope.launch { DownloadQueue.remove(download.key) } },
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    download: Download,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PANEL, MoovieShape)
            // `IntrinsicSize.Min` : la rangée prend la hauteur de sa colonne de
            // texte, et l'affiche peut alors la remplir. Sans ça, `fillMaxHeight`
            // n'a aucune hauteur à remplir — une `Row` se règle sur le plus haut
            // de ses enfants, donc l'image aurait décidé de sa propre taille et
            // laissé du vide sous elle.
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Affiche(download, Modifier.fillMaxHeight())
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
        TitreDefilant(
            listOfNotNull(download.title, download.subtitle.takeIf { it.isNotBlank() })
                .joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
        )

        Text(statusOf(download), style = MaterialTheme.typography.bodySmall, color = DIM)

        // La barre n'apparaît que pendant le travail : sur un titre terminé elle
        // serait une décoration pleine qui n'apprend rien.
        if (download.state == DownloadState.RUNNING || download.state == DownloadState.QUEUED) {
            LinearProgressIndicator(
                progress = { download.progress },
                color = MOOVIE_ACCENT,
                trackColor = Color(0xFF2A2A2A),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        download.error?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFFE06A6A))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (download.state) {
                DownloadState.RUNNING, DownloadState.QUEUED ->
                    MoovieButton(onClick = onPause) { Text(stringResource(Res.string.downloads_pause)) }

                DownloadState.PAUSED, DownloadState.FAILED ->
                    MoovieButton(onClick = onResume) { Text(stringResource(Res.string.downloads_resume)) }

                DownloadState.DONE ->
                    MoovieButton(onClick = onPlay, selected = true) {
                        Text(stringResource(Res.string.downloads_play))
                    }
            }
            MoovieButton(onClick = onRemove) { Text(stringResource(Res.string.downloads_remove)) }
        }
        }
    }
}

/**
 * L'affiche du titre, prise sur le disque.
 *
 * Rien n'est demandé au réseau : le fichier a été récupéré avec les segments
 * (voir `downloadPoster`). Un téléchargement d'avant cette version n'en a pas,
 * et la carte se replie alors sur son texte seul plutôt que de réserver un
 * cadre vide.
 *
 * `remember(key)` parce qu'une liste recycle ses cartes : sans lui, une rangée
 * réutilisée garderait l'affiche du titre précédent le temps d'une frame.
 */
@Composable
private fun Affiche(download: Download, modifier: Modifier = Modifier) {
    // `produceState` plutôt qu'un simple `remember` : les titres téléchargés
    // avant cette version n'ont pas d'affiche sur le disque, et c'est ici qu'on
    // la rattrape — une fois, sans bloquer le rendu. Voir fetchDownloadPoster.
    val fichier by produceState<File?>(null, download.key) {
        value = downloadPoster(download.key)
            ?: withContext(Dispatchers.IO) {
                fetchDownloadPoster(
                    key = download.key,
                    tmdbId = download.tmdbId,
                    isTv = download.isTv,
                    imageUrl = download.imageUrl,
                )
            }
    }
    val image = fichier ?: return
    // Une bande pleine hauteur, et non une vignette posée en haut.
    //
    // Le cadre carré précédent laissait du vide sous l'image dès que la carte
    // dépassait sa hauteur — soit toujours, puisqu'elle porte deux lignes et
    // deux boutons. Une colonne qui court du haut de la carte au bas des
    // boutons n'a pas ce défaut, et donne à la liste une arête franche à
    // gauche.
    //
    // `Crop` retrouve son sens ici : la colonne est haute et étroite, donc très
    // proche du 2:3 d'une affiche, qui n'y perd presque rien. Une image
    // d'épisode en 16:9 est recadrée sur son centre — ce que fait n'importe
    // quelle vignette, et ce qui vaut mieux que deux bandes noires.
    MoovieAsyncImage(
        model = image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.width(AFFICHE_LARGEUR).clip(MoovieShape),
    )
}

/**
 * Un titre qui défile lentement quand il déborde, et reste immobile sinon.
 *
 * `basicMarquee` ne s'anime **que** si le texte dépasse la largeur : sur les
 * titres courts, rien ne bouge, et rien ne distrait. C'est ce qui permet de
 * l'appliquer partout sans trier à la main les titres longs.
 *
 * Le défilement remplace les points de suspension, et c'est un vrai gain sur
 * téléphone : en portrait, la carte laisse une trentaine de caractères, si bien
 * que « Zack Snyder's Justice League · S1 · E1 — … » se coupait exactement là
 * où l'information distinctive commence. Vitesse volontairement basse — on lit
 * le titre, on ne le regarde pas passer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TitreDefilant(texte: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        texte,
        style = style,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            initialDelayMillis = 1_500,
            repeatDelayMillis = 2_000,
            velocity = MARQUEE_VITESSE,
        ),
    )
}

/** Largeur de la colonne d'affiches. Assez pour reconnaître une affiche, pas plus. */
private val AFFICHE_LARGEUR = 62.dp

/** Hauteur de l'affiche d'un en-tête de série, qui n'a pas de boutons à longer. */
private val AFFICHE_ENTETE = 44.dp

/** Vitesse de défilement d'un titre trop long. Deux fois plus lente que le défaut. */
private val MARQUEE_VITESSE = 16.dp

@Composable
private fun statusOf(download: Download): String = when (download.state) {
    DownloadState.DONE -> stringResource(Res.string.downloads_ready, formatSize(download.bytes))
    DownloadState.QUEUED -> stringResource(Res.string.downloads_queued)
    DownloadState.PAUSED -> stringResource(Res.string.downloads_paused)
    DownloadState.FAILED -> stringResource(Res.string.downloads_failed)
    DownloadState.RUNNING -> "${(download.progress * 100).toInt()} % · ${formatSize(download.bytes)}"
}

/**
 * Taille lisible.
 *
 * En base 1000 et non 1024 : c'est ce qu'annoncent les fabricants de disques et
 * les réglages d'Android, et une app qui compte autrement fait douter de son
 * chiffre plutôt que de celui du système.
 */
internal fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f Go".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.0f Mo".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f Ko".format(bytes / 1_000.0)
    else -> "$bytes o"
}
