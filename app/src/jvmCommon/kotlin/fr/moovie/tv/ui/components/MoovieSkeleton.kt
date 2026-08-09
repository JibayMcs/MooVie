package fr.moovie.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import fr.moovie.tv.ui.theme.MoovieShape

/**
 * Squelettes de chargement.
 *
 * Un écran vide et un écran en train de charger se ressemblent trop : sur un
 * démarrage à froid, la grille d'affiches grises passait pour une panne. Un
 * squelette dit deux choses qu'un « Chargement… » ne dit pas — *combien* de
 * contenu arrive, et *où* il se posera — si bien que rien ne saute quand les
 * vraies données remplacent les formes.
 *
 * Volontairement dans `jvmCommon` : le démarrage à froid n'est pas un problème
 * de téléphone, c'est un problème de réseau. Une TV derrière un Wi-Fi lointain
 * en profite autant.
 */

/** Teinte de base d'une forme en attente. Un cran au-dessus du fond des cartes. */
private val SKELETON_BASE = Color(0xFF1E1E1E)

/** Crête du reflet qui balaie la forme. Assez discrète pour ne pas clignoter. */
private val SKELETON_HIGHLIGHT = Color(0xFF2E2E2E)

private const val SWEEP_MS = 1_400

/**
 * Fond animé d'une forme en attente : un reflet qui balaie la surface de gauche
 * à droite, en boucle.
 *
 * `composed` plutôt qu'un simple `Modifier.background` : l'animation a besoin
 * d'un état mémorisé, donc d'un contexte de composition.
 */
fun Modifier.moovieShimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SWEEP_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    // Le dégradé est plus large que la forme et se déplace de part et d'autre :
    // le reflet entre et sort du cadre au lieu d'apparaître au milieu.
    val span = 600f
    val start = -span + progress * (span * 3f)
    background(
        Brush.linearGradient(
            colors = listOf(SKELETON_BASE, SKELETON_HIGHLIGHT, SKELETON_BASE),
            start = Offset(start, 0f),
            end = Offset(start + span, 0f),
        ),
    )
}

/**
 * Image distante avec attente animée.
 *
 * Le squelette d'écran couvre l'attente de la *réponse* TMDB ; celui-ci couvre
 * l'attente de chaque *image*. Les deux ne se recouvrent pas : les affiches
 * arrivent une par une, longtemps après la liste qui les décrit, et c'est ce
 * temps-là qu'on voyait en rectangles gris.
 *
 * L'état vient de Coil plutôt que d'un minuteur : une image en erreur ou une
 * URL nulle arrêtent l'animation au lieu de la laisser tourner indéfiniment sur
 * quelque chose qui n'arrivera jamais.
 */
@Composable
fun MoovieAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    /**
     * Visuel de repli quand [model] ne rend rien : l'affiche ou le fond du
     * titre, rendus en gris et barrés. Un épisode non diffusé n'a pas de
     * vignette chez TMDB, et une icône seule dans un cadre vide en dit moins
     * qu'une image du titre marquée « pas encore ». Null = ancien
     * comportement, l'icône discrète.
     */
    fallback: Any? = null,
) {
    // `remember(model)` et non `remember` : une carte recyclée par une liste
    // paresseuse change de modèle sans être recomposée depuis zéro, et resterait
    // sinon marquée « chargée » en affichant l'image du titre précédent.
    // Trois états, et non deux. Le scintillement disait « ça charge » ; son
    // absence ne disait rien du tout, et le fond gris du parent apparaissait tel
    // quel. Un carré gris se lit alors comme un chargement en panne, alors qu'il
    // signifie souvent « cette image n'existe pas » — les épisodes d'une saison
    // non diffusée n'ont pas de vignette chez TMDB, et une série sans affiche
    // reste sans affiche quoi qu'on attende.
    var state by remember(model) {
        mutableStateOf(if (model == null) ImageState.ABSENT else ImageState.LOADING)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (state) {
            ImageState.LOADING -> Box(modifier = Modifier.matchParentSize().moovieShimmer())
            // Discret volontairement : c'est un état de repos, pas une erreur à
            // signaler. Le titre à côté porte déjà l'information utile.
            ImageState.ABSENT -> if (fallback != null) {
                UnavailableArt(fallback, modifier = Modifier.matchParentSize())
            } else {
                Icon(
                    imageVector = Icons.Default.ImageNotSupported,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier.size(28.dp),
                )
            }
            ImageState.LOADED -> Unit
        }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
                onState = {
                    state = when (it) {
                        is AsyncImagePainter.State.Loading -> ImageState.LOADING
                        is AsyncImagePainter.State.Success -> ImageState.LOADED
                        // Erreur **et** vide tombent ici : une URL qui ne rend
                        // rien et une URL absente sont la même chose à l'écran.
                        else -> ImageState.ABSENT
                    }
                },
            )
        }
    }
}

/**
 * Visuel de repli d'une vignette absente : l'image du titre, désaturée,
 * assombrie et barrée en diagonale.
 *
 * Les trois traitements portent chacun une part du message et aucun ne suffit
 * seul. Le gris sort l'image du flux des vignettes réelles ; l'assombrissement
 * l'empêche de rivaliser avec le texte posé à côté ; la barre dit ce que les
 * deux autres laissent deviner — non pas « image manquante », mais « pas
 * encore disponible ».
 *
 * La barre est doublée : un trait sombre plus large sous le trait clair. Sur
 * une image de plein jour, un simple trait blanc disparaît.
 */
@Composable
private fun UnavailableArt(model: Any, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // setToSaturation(0) : la matrice de luminance standard, donc un
            // gris perçu et non la moyenne des canaux.
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            modifier = Modifier.matchParentSize(),
        )
        Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)))
        Canvas(modifier = Modifier.matchParentSize()) {
            // Coin bas-gauche vers coin haut-droit, en retrait des bords : une
            // diagonale qui touche les angles se confond avec le cadre.
            val inset = size.minDimension * 0.18f
            val start = Offset(inset, size.height - inset)
            val end = Offset(size.width - inset, inset)
            // Épaisseurs en dp et non en pixels : un trait de 6 px reste 6 px
            // sur la dalle 450 dpi d'un téléphone, où il devient deux fois plus
            // fin qu'à l'écran où il a été réglé.
            drawLine(
                color = Color.Black.copy(alpha = 0.5f),
                start = start,
                end = end,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = start,
                end = end,
                strokeWidth = 1.5f.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Ce qu'une vignette a à montrer : en cours, rendue, ou rien à rendre. */
private enum class ImageState { LOADING, LOADED, ABSENT }

/** Forme rectangulaire en attente, aux coins du thème. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(MoovieShape).moovieShimmer())
}

/** Ligne de texte en attente : hauteur d'une ligne, largeur au choix. */
@Composable
fun SkeletonLine(width: Dp, height: Dp = 14.dp, modifier: Modifier = Modifier) {
    SkeletonBox(modifier = modifier.width(width).height(height))
}

/**
 * Rangée d'affiches en attente, telle que l'accueil en affiche.
 *
 * [posterWidth] doit reprendre la largeur réelle des cartes : c'est ce qui évite
 * que la rangée se réorganise quand les vraies affiches arrivent.
 */
@Composable
fun SkeletonRail(
    posterWidth: Dp,
    modifier: Modifier = Modifier,
    count: Int = 6,
    /** 2:3 pour une affiche, 16:9 pour une vignette d'épisode. */
    aspectRatio: Float = 2f / 3f,
) {
    // Hauteur calculée, jamais déduite d'un `aspectRatio`. Dans une Row dont la
    // largeur restante est plus étroite que demandée, `aspectRatio` résout la
    // contrainte sur la **hauteur** : la deuxième affiche fantôme sortait à
    // 229 × 680 dp au lieu de 138 × 207, et chassait tout le reste de l'écran.
    // Une taille explicite ne laisse pas ce choix au système.
    val posterHeight = posterWidth / aspectRatio
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonLine(width = 180.dp, height = 20.dp)
        // Rogné : six affiches font près du double de la largeur d'un téléphone,
        // et une rangée figée ne défile pas pour rattraper le débordement.
        Row(
            modifier = Modifier.fillMaxWidth().height(posterHeight).clipToBounds(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repeat(count) {
                SkeletonBox(modifier = Modifier.width(posterWidth).height(posterHeight))
            }
        }
    }
}

/**
 * Grille d'affiches en attente, pour le catalogue et la recherche.
 *
 * Rendue en lignes plutôt qu'en `LazyVerticalGrid` : une grille paresseuse
 * mesure et recycle pour rien quand son contenu est figé, et il n'y a de toute
 * façon jamais plus de deux ou trois lignes visibles.
 */
@Composable
fun SkeletonGrid(
    columns: Int,
    modifier: Modifier = Modifier,
    rows: Int = 2,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                repeat(columns) {
                    SkeletonBox(
                        modifier = Modifier.weight(1f).aspectRatio(2f / 3f),
                    )
                }
            }
        }
    }
}

/**
 * En-tête de fiche en attente : le visuel, puis quelques lignes de métadonnées.
 */
@Composable
fun SkeletonDetails(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Taille explicite, même raison que dans SkeletonRail.
        SkeletonBox(modifier = Modifier.width(150.dp).height(225.dp))
        SkeletonLine(width = 120.dp)
        SkeletonLine(width = 260.dp, height = 24.dp)
        SkeletonLine(width = 180.dp)
    }
}
