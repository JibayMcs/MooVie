package fr.moovie.tv.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import fr.moovie.tv.ui.components.CadreDefilant
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.discovery.MoodAnswers
import fr.moovie.tv.data.discovery.MoodOption
import fr.moovie.tv.data.discovery.MoodQuestion
import fr.moovie.tv.data.discovery.moodOptionsFor
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.discovery_quiz_skip
import fr.moovie.tv.resources.discovery_quiz_reset
import fr.moovie.tv.resources.mood_amis
import fr.moovie.tv.resources.mood_court
import fr.moovie.tv.resources.mood_detendue
import fr.moovie.tv.resources.mood_deux
import fr.moovie.tv.resources.mood_famille
import fr.moovie.tv.resources.mood_peuimporte
import fr.moovie.tv.resources.mood_peur
import fr.moovie.tv.resources.mood_rire
import fr.moovie.tv.resources.mood_serie
import fr.moovie.tv.resources.mood_seul
import fr.moovie.tv.resources.mood_soiree
import fr.moovie.tv.resources.mood_tension
import fr.moovie.tv.ui.adaptive.useBottomNav
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.components.MoovieCard
import fr.moovie.tv.ui.theme.MOOVIE_ACCENT
import fr.moovie.tv.ui.theme.MoovieGradient
import fr.moovie.tv.ui.theme.MoovieShape
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import fr.moovie.tv.ui.theme.MOOVIE_SCRIM
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM
import fr.moovie.tv.ui.theme.margePage

/**
 * Le questionnaire : une question par écran, les réponses en cartes.
 *
 * Les réponses sont **elles-mêmes des cartes**, de la même grammaire que le
 * reste de la page : c'est ce qui fait que le questionnaire n'a pas l'air d'un
 * formulaire posé au milieu d'une application de cinéma.
 *
 * Trois questions, et on peut sortir à tout moment ([onSkip]) : sur un profil
 * qui a déjà un historique, le questionnaire n'apporte qu'un groupe de plus.
 * L'imposer serait payer trois appuis pour quelque chose de facultatif.
 */
@Composable
fun MoodQuizContent(
    question: MoodQuestion,
    answers: MoodAnswers,
    onAnswer: (MoodOption) -> Unit,
    onSkip: () -> Unit,
    /** Efface les trois réponses. Null tant qu'il n'y a rien à effacer. */
    onReset: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val hPad = margePage()
    val options = remember(question) { moodOptionsFor(question) }
    val choisie = answers.answerFor(question)

    /*
     * **Défilant, et c'est une correction de bug.**
     *
     * Un téléviseur 1080p n'offre que 540 dp de haut. L'en-tête de la page, la
     * question, son sous-titre et une carte de réponse en 4:3 dépassaient ce
     * budget : le bouton « Passer » tombait sous la ligne de flottaison, et
     * comme rien ne défilait, il était purement inatteignable — sur l'appareil
     * qui n'a ni doigt ni molette pour aller le chercher.
     *
     * Le conteneur défile donc, et c'est le **focus** qui le pilote : la
     * télécommande descend des cartes vers les boutons, et Compose amène de
     * lui-même l'élément focalisé dans le champ de vision.
     */
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = if (useBottomNav) 16.dp else 40.dp, bottom = 32.dp),
    ) {
        // Progression : trois traits, pas un pourcentage. On veut savoir
        // combien il en reste, pas où l'on en est à un pour cent près.
        Row(
            modifier = Modifier.padding(horizontal = hPad),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MoodQuestion.entries.forEach { q ->
                Box(
                    Modifier
                        .width(26.dp)
                        .height(3.dp)
                        .background(
                            when {
                                q == question -> Brush.horizontalGradient(
                                    listOf(MOOVIE_ACCENT, MOOVIE_ACCENT),
                                )
                                answers.answerFor(q) != null -> MoovieGradient
                                else -> Brush.horizontalGradient(
                                    listOf(Color(0xFF2A2A33), Color(0xFF2A2A33)),
                                )
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(questionTitle(question)),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = hPad),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(questionSub(question)),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF8A8A95),
            modifier = Modifier.padding(horizontal = hPad),
        )
        Spacer(Modifier.height(if (useBottomNav) 20.dp else 28.dp))

        // **En portrait, une grille — pas une main qui défile.**
        //
        // Six ou sept humeurs, dont trois tenaient à l'écran : les autres
        // attendaient hors champ, et la question se répondait sur un tiers des
        // choix. Un repère de défilement l'aurait signalé, mais il aurait fallu
        // le suivre — or c'est un questionnaire, pas un catalogue : on veut
        // comparer les réponses d'un regard, ce qu'une file ne permet jamais.
        //
        // Trois par ligne, la largeur partagée à parts égales. Deux lignes
        // affichent alors les sept, « Passer » compris, sans un geste.
        //
        // La main reste sur les écrans larges : elle y tient tout entière, et
        // c'est la même forme que les rangées de la découverte.
        if (useBottomNav) {
            val toutes: List<MoodOption?> = options + listOf(null)
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = hPad, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                toutes.chunked(COLONNES_GRILLE).forEach { ligne ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ligne.forEach { option ->
                            if (option == null) {
                                PasserCard(onClick = onSkip, modifier = Modifier.weight(1f))
                            } else {
                                MoodOptionCard(
                                    option = option,
                                    selected = option.id == choisie?.id,
                                    onClick = { onAnswer(option) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        // La dernière ligne est incomplète : les cases vides
                        // gardent la largeur de leurs voisines, sans quoi deux
                        // cartes seules s'étireraient sur toute la ligne et ne
                        // ressembleraient plus à celles du dessus.
                        repeat(COLONNES_GRILLE - ligne.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
        val mainState = rememberLazyListState()
        CadreDefilant(etat = mainState, modifier = Modifier.fillMaxWidth()) {
        // Marges dans le contentPadding, jamais autour : la carte grandit au
        // focus et se ferait rogner par le bord du conteneur défilant.
        LazyRow(
            state = mainState,
            contentPadding = PaddingValues(horizontal = hPad, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(if (useBottomNav) 10.dp else 16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(options, key = { it.id }) { option ->
                MoodOptionCard(
                    option = option,
                    selected = option.id == choisie?.id,
                    onClick = { onAnswer(option) },
                )
            }
            // Passer est une **carte**, en bout de main, et non un bouton posé
            // sous les autres. Deux raisons, toutes deux propres au téléviseur :
            // la télécommande y arrive d'un coup de flèche droite au lieu de
            // devoir descendre, et sur les 540 dp d'un 1080p le bouton tombait
            // sous la ligne de flottaison. Une sortie qu'on ne peut pas
            // atteindre n'est pas une sortie.
            item(key = "passer") { PasserCard(onClick = onSkip) }
        }
        }
        }

        // Effacer les réponses reste un bouton, et reste en bas : c'est le seul
        // geste destructeur de l'écran, il n'a rien à faire dans la main où
        // l'on choisit. Absent tant qu'il n'y a rien à effacer.
        onReset?.let { reset ->
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.padding(horizontal = hPad)) {
                MoovieButton(onClick = reset) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.discovery_quiz_reset))
                }
            }
        }
    }
}

/**
 * Le gabarit d'une carte du questionnaire.
 *
 * En grille, elle prend la part de largeur que sa colonne lui donne ; en main,
 * elle porte sa propre largeur. Le rapport 0,72 est commun aux deux : c'est lui
 * qui fait qu'une carte se lit comme une carte, et non comme une vignette.
 */
/** Colonnes de la grille en portrait. Trois : quatre donneraient des cartes de
 * 90 dp, où le sous-titre — « thriller, policier » — ne tiendrait plus. */
private const val COLONNES_GRILLE = 3

@Composable
private fun gabaritCarte(): Modifier = Modifier
    .then(if (useBottomNav) Modifier.fillMaxWidth() else Modifier.width(168.dp))
    .aspectRatio(0.72f)
    .clip(MoovieShape)

/**
 * La carte « Passer », en bout de main.
 *
 * Volontairement **sans illustration** : elle n'est pas une réponse de plus,
 * elle sort du questionnaire. Même gabarit que ses voisines pour que la main
 * reste régulière, mais un fond sourd et un simple contour au lieu d'une image,
 * de sorte qu'on ne la choisisse jamais par méprise en parcourant les visuels.
 */
@Composable
private fun PasserCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    MoovieCard(onClick = onClick, focusedScale = 1.06f, modifier = modifier) {
        Box(
            modifier = gabaritCarte()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF17171D), Color(0xFF0C0C11)),
                    ),
                )
                .border(1.dp, Color(0xFF2E2E38), MoovieShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MOOVIE_TEXT_DIM,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    stringResource(Res.string.discovery_quiz_skip),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFCFCFD8),
                )
            }
        }
    }
}

/**
 * Une réponse : un fond illustré, un libellé, et ce que la réponse fait.
 *
 * Le sous-titre (« thriller, policier ») n'est pas décoratif : un questionnaire
 * dont on voit les effets est un questionnaire qu'on peut corriger quand il
 * déçoit. Les pages du genre les cachent, et le résultat paraît alors
 * arbitraire.
 */
@Composable
private fun MoodOptionCard(
    option: MoodOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoovieCard(onClick = onClick, focusedScale = 1.06f, modifier = modifier) {
        Box(
            modifier = gabaritCarte(),
        ) {
            Image(
                painter = painterResource(moodDrawable(option.id)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Voile bas : sans lui le libellé se pose sur la vague la plus
            // claire et devient illisible sur deux cartes sur douze.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to MOOVIE_SCRIM,
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            ) {
                Text(
                    stringResource(optionLabel(option.id)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    stringResource(optionDetail(option.id)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFCFCFD8),
                )
            }
            if (selected) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomStart)
                        .background(MoovieGradient),
                )
            }
        }
    }
}

private fun moodDrawable(id: String): DrawableResource = when (id) {
    "detendue" -> Res.drawable.mood_detendue
    "tension" -> Res.drawable.mood_tension
    "rire" -> Res.drawable.mood_rire
    "peur" -> Res.drawable.mood_peur
    "seul" -> Res.drawable.mood_seul
    "deux" -> Res.drawable.mood_deux
    "amis" -> Res.drawable.mood_amis
    "famille" -> Res.drawable.mood_famille
    "court" -> Res.drawable.mood_court
    "soiree" -> Res.drawable.mood_soiree
    "peuimporte" -> Res.drawable.mood_peuimporte
    "serie" -> Res.drawable.mood_serie
    else -> error("Unknown mood option: $id")
}
