package fr.moovie.tv.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.moovie.tv.core.format.formatDuration
import fr.moovie.tv.data.tmdb.Credits
import fr.moovie.tv.data.tmdb.MovieDetails
import fr.moovie.tv.data.tmdb.TvDetails
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.details_cast
import fr.moovie.tv.resources.info_country
import fr.moovie.tv.resources.info_creator
import fr.moovie.tv.resources.info_director
import fr.moovie.tv.resources.media_movie
import fr.moovie.tv.resources.media_series
import fr.moovie.tv.ui.components.MoovieAsyncImage
import fr.moovie.tv.ui.theme.MoovieShape
import org.jetbrains.compose.resources.stringResource

/** Le gris des lignes secondaires du hero — celui du reste de la fiche. */
private val HERO_DIM = Color(0xFF9A9A9A)

/**
 * L'en-tête d'une fiche, sur les écrans qu'on regarde de loin ou en grand.
 *
 * ## Ce qu'il change
 *
 * L'en-tête d'avant posait une affiche 2:3 à gauche et empilait les
 * métadonnées à droite, sur un fond d'écran **flouté** qui ne servait que de
 * texture. C'est une mise en page de fiche produit : elle décrit un titre. Un
 * service de vidéo montre d'abord une image, en grand et nette, et laisse le
 * texte s'y poser — c'est ce que font myCanal, Netflix ou Disney+, et c'est ce
 * que cette maquette demande.
 *
 * L'image passe donc au premier plan, à fond perdu et sans flou, et tout le
 * reste se pose dessus.
 *
 * ## Le dégradé fait deux choses à la fois
 *
 * Il rend le texte lisible sur une image dont on ne sait rien — un fond clair
 * est aussi probable qu'un fond sombre — et il raccorde le hero au fond de la
 * page. Sans lui, l'image se terminerait par une arête franche au milieu de
 * l'écran, ce qui se lit comme une bannière posée là plutôt que comme le haut
 * d'une page.
 *
 * ## Pourquoi seulement ici
 *
 * Rien de tout cela ne descend au doigt. Sur un téléphone, la largeur ne permet
 * pas deux colonnes, et un hero de 340 dp mangerait l'écran entier avant le
 * premier bouton. La fiche tactile garde donc son en-tête empilé — voir
 * `MovieHeader`, qui reste appelé pour elle.
 *
 * @param meta la ligne de qualificatifs — genre, durée, année. Les éléments
 *   vides sont écartés ici plutôt que par chaque appelant.
 * @param credits les lignes « De : », « Avec : », « Pays : » déjà formées, dans
 *   l'ordre où elles doivent paraître. Vide pour n'en afficher aucune.
 * @param actions la colonne de gauche sous le titre : bouton principal et ce
 *   qui l'accompagne. Un emplacement, parce que ces boutons portent l'état des
 *   sources et n'ont rien à faire dans un composant de mise en page.
 * @param aside posé en bas à droite, au ras du bord — la place de « Plus
 *   d'infos » dans la maquette.
 * @param imageMasquee laisse le cadre **transparent** au lieu d'y peindre le
 *   backdrop : la page joue la bande-annonce derrière, dans cette boîte exacte,
 *   et l'image la recouvrirait. Les dégradés et le texte, eux, restent — c'est
 *   tout l'intérêt, la vidéo prend la place de l'image sans rien changer
 *   d'autre. Voir `DetailsScreenContent`, qui explique pourquoi le lecteur ne
 *   peut pas vivre ici.
 * @param controles en haut à droite du cadre, au-dessus de tout : les commandes
 *   de cette bande-annonce. Elles appartiennent au hero et non à la page, parce
 *   que c'est son cadre qu'elles pilotent.
 */
@Composable
internal fun DetailsHero(
    backdropUrl: String?,
    /**
     * L'affiche, posée **par-dessus** l'image de fond.
     *
     * Elle n'est pas un repli du backdrop mais son complément : le backdrop
     * donne l'ambiance en 16:9, l'affiche donne l'objet — c'est elle qu'on
     * reconnaît, et c'est le seul visuel qui existe pour tous les titres.
     */
    afficheUrl: String?,
    titre: String,
    meta: List<String>,
    synopsis: String,
    credits: List<String>,
    hauteur: Dp,
    marge: Dp,
    actions: @Composable () -> Unit,
    aside: (@Composable () -> Unit)? = null,
    imageMasquee: Boolean = false,
    controles: (@Composable () -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxWidth().height(hauteur)) {
        // **Une image, quelle qu'elle soit.** Le backdrop d'abord, c'est le seul
        // cadré pour cette place. À défaut l'affiche, floutée : recadrer un 2:3
        // en bandeau en perd la moitié, mais floutée elle ne prétend plus être
        // une image — elle redevient une couleur, tirée du titre lui-même. Un
        // cadre noir aurait été plus honnête et beaucoup plus triste.
        val fond = backdropUrl ?: afficheUrl
        if (fond != null && !imageMasquee) {
            MoovieAsyncImage(
                model = fond,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(
                    if (backdropUrl == null) Modifier.blur(48.dp) else Modifier,
                ),
            )
        }

        // **Deux dégradés, pas un.**
        //
        // Le vertical raccorde l'image au fond de la page et assombrit la bande
        // basse, celle qui porte le texte. Le latéral protège la colonne de
        // gauche d'une image claire de ce côté-là : le titre y est blanc, et un
        // ciel de jour derrière lui suffit à le rendre illisible. Le second est
        // plus faible que le premier — il corrige, il ne repeint pas.
        //
        // **Les paliers suivent le texte, pas une proportion fixe.** Le cadre
        // occupe désormais toute la hauteur visible : les mêmes arrêts, calés
        // pour un hero de 620 dp, noircissaient la moitié d'une image de
        // 1 400 dp — c'est-à-dire l'image elle-même, qui est tout le sujet.
        // Le bloc de texte tient dans le dernier tiers ; le dégradé n'assombrit
        // donc que là, et laisse le reste tel qu'il est.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x000A0A0A),
                    0.38f to Color(0x1A0A0A0A),
                    0.62f to Color(0x990A0A0A),
                    1f to Color(0xFF0A0A0A),
                ),
            ),
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color(0x990A0A0A),
                    0.5f to Color(0x000A0A0A),
                ),
            ),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = marge, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // **L'affiche seulement quand il n'y a pas de backdrop.**
            //
            // La maquette n'en montre aucune, et pour cause : le backdrop est
            // déjà l'image du titre, en grand et à sa place. Poser l'affiche
            // par-dessus, c'est montrer deux fois la même œuvre — la seconde
            // en petit, et en travers du texte qu'elle repousse.
            //
            // Elle reste le filet du cas sans backdrop, où le cadre serait
            // sinon un rectangle flouté sans rien à reconnaître.
            if (backdropUrl == null && afficheUrl != null) {
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .aspectRatio(2f / 3f)
                        .clip(MoovieShape)
                        .background(Color(0xFF222222)),
                ) {
                    MoovieAsyncImage(
                        model = afficheUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // **Les deux colonnes se partagent la largeur, elles ne la
            // subissent pas.**
            //
            // Bornées à 520 et 640 dp, elles se serraient toutes les deux dans
            // le premier tiers d'une fenêtre large et laissaient la moitié
            // droite vide : le texte paraissait tassé dans un coin d'une image
            // qui, elle, occupait tout. Des poids le répartissent — un tiers au
            // titre et à ses boutons, deux tiers au synopsis, comme la
            // maquette. La borne sur la colonne de droite reste : au-delà, une
            // ligne devient trop longue pour qu'on retrouve la suivante.
            Column(
                modifier = Modifier.weight(0.34f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    titre,
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val ligne = meta.filter { it.isNotBlank() }
                if (ligne.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ligne.forEach { element ->
                            Text(
                                element,
                                style = MaterialTheme.typography.labelLarge,
                                color = HERO_DIM,
                            )
                        }
                    }
                }
                actions()
            }

            // Le synopsis et les crédits, colonne de droite. Bornés en largeur :
            // une ligne qui traverse un écran de 960 dp se relit mal, l'œil
            // perdant le début de la suivante.
            Column(
                modifier = Modifier.weight(0.66f).widthIn(max = 780.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (synopsis.isNotBlank()) {
                    Text(
                        synopsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFDDDDDD),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                credits.forEach { ligne ->
                    Text(
                        ligne,
                        style = MaterialTheme.typography.bodySmall,
                        color = HERO_DIM,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (aside != null) {
                Box(modifier = Modifier.align(Alignment.Bottom)) { aside() }
            }
        }

        // Dernier de la pile : elles se posent sur la vidéo comme sur les
        // dégradés. Même marge horizontale que la page, pour qu'elles
        // s'alignent sur ce qui les surplombe et non sur le bord de l'écran.
        if (controles != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = marge, vertical = 24.dp),
            ) {
                controles.invoke()
            }
        }
    }
}

/**
 * La ligne de qualificatifs d'un film : « Film Drame · 1 h 35 · 2025 ».
 *
 * Trois éléments au plus, et les vides tombent d'eux-mêmes plus haut. C'est la
 * même information que portait le bloc de métadonnées de l'ancien en-tête, mais
 * réduite à ce qui se lit d'un coup d'œil : le genre principal seulement, parce
 * qu'un film en porte jusqu'à cinq et qu'aligner « Drame Thriller Policier
 * Mystère » fait une phrase, pas une étiquette.
 */
@Composable
internal fun metaFilm(details: MovieDetails): List<String> = listOf(
    listOfNotNull(
        stringResource(Res.string.media_movie),
        details.genres.firstOrNull()?.name,
    ).joinToString(" "),
    formatDuration(details.runtime).orEmpty(),
    details.releaseDate?.take(4).orEmpty(),
)

/**
 * La ligne de qualificatifs d'une série : « Série Drame · 2023 ».
 *
 * Pas de compte de saisons, contrairement à ce que l'on serait tenté d'y
 * mettre : la maquette n'en porte pas, et l'information est déjà sous l'onglet
 * « Épisodes », où la rangée des saisons la donne en les montrant. Une ligne de
 * qualificatifs dit ce qu'est le titre, pas ce qu'il contient.
 */
@Composable
internal fun metaSerie(details: TvDetails): List<String> = listOf(
    listOfNotNull(
        stringResource(Res.string.media_series),
        details.genres.firstOrNull()?.name,
    ).joinToString(" "),
    details.year.orEmpty(),
)

/**
 * Les mêmes lignes pour une série, au premier libellé près.
 *
 * « Création » et non « Réalisation » : une série en change à chaque épisode, et
 * TMDB ne range d'ailleurs pas ses réalisateurs dans l'équipe de la série mais
 * dans celle de chaque épisode — la ligne serait vide. C'est le vocabulaire du
 * panneau « En savoir plus », qui fait déjà cette distinction.
 *
 * Le pays est omis : `TvDetails` n'en donne que les codes ISO (« US »), là où un
 * film porte les noms. « Pays : US » informe moins qu'il n'intrigue.
 */
@Composable
internal fun creditsSerie(credits: Credits?, createurs: List<String>): List<String> {
    val distribution = credits?.cast.orEmpty().take(3).joinToString(", ") { it.name }
    return listOf(
        stringResource(Res.string.info_creator) to createurs.joinToString(", "),
        stringResource(Res.string.details_cast) to distribution,
    ).filter { it.second.isNotBlank() }.map { (label, valeur) -> "$label : $valeur" }
}

/**
 * Les lignes « Réalisation : … », « Casting : … », « Pays : … ».
 *
 * Les libellés sont ceux du panneau « En savoir plus » — deux vocabulaires pour
 * la même information donneraient l'impression de deux sources différentes.
 *
 * Trois noms de casting, pas plus : la ligne doit tenir sans se couper, et
 * au-delà de trois on ne lit plus une distribution mais une liste.
 */
@Composable
internal fun creditsDe(credits: Credits?, pays: List<String>): List<String> {
    val realisation = credits?.crew.orEmpty()
        .filter { it.job == "Director" }
        .joinToString(", ") { it.name }
    val distribution = credits?.cast.orEmpty().take(3).joinToString(", ") { it.name }
    return listOf(
        stringResource(Res.string.info_director) to realisation,
        stringResource(Res.string.details_cast) to distribution,
        stringResource(Res.string.info_country) to pays.joinToString(", "),
    ).filter { it.second.isNotBlank() }.map { (label, valeur) -> "$label : $valeur" }
}
