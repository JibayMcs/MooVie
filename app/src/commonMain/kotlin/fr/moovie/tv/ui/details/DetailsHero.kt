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
import fr.moovie.tv.ui.adaptive.HeightClass
import fr.moovie.tv.ui.adaptive.LocalHeightClass
import fr.moovie.tv.ui.components.MoovieAsyncImage
import fr.moovie.tv.ui.theme.MoovieShape
import org.jetbrains.compose.resources.stringResource
import fr.moovie.tv.ui.theme.MOOVIE_BG
import fr.moovie.tv.ui.theme.MOOVIE_SURFACE_HIGH
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_MUTED

/** Le gris des lignes secondaires du hero — celui du reste de la fiche. */
private val HERO_DIM = MOOVIE_TEXT_DIM

/**
 * La hauteur que le voile couvre, mesurée depuis le bas du cadre.
 *
 * C'est la place que prend le bloc de texte — titre, ligne de méta, bouton
 * principal, rangée d'actions — plus de quoi respirer au-dessus. Deux valeurs
 * parce que le bloc lui-même en a deux : resserré sur un cadre court, ample
 * ailleurs (voir [CADRE_COURT]). Un voile calé sur la version ample couvrirait
 * neuf dixièmes d'un cadre de téléviseur, soit très exactement le défaut qu'il
 * est censé corriger.
 */
private val HAUTEUR_VOILE = 330.dp
private val HAUTEUR_VOILE_COURT = 210.dp

/**
 * Largeur au-delà de laquelle un titre cesse de se lire d'un trait.
 *
 * Le titre occupe toute la largeur du cadre pour ne jamais être coupé (voir
 * plus bas), mais « toute la largeur » d'une fenêtre de bureau fait mille sept
 * cents points : l'œil y perd la fin de la première ligne avant d'avoir trouvé
 * le début de la seconde.
 */
private val LARGEUR_MAX_TITRE = 1100.dp

/**
 * Part de l'image que le fondu couvre, en portrait.
 *
 * Zéro raccorderait le texte à une arête franche ; un noircirait l'image
 * entière. Les deux tiers laissent voir ce qu'on montre et éteignent le bas
 * assez tôt pour que personne ne voie où l'image s'arrête.
 */
private const val FONDU_IMAGE = 0.66f

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
    /**
     * Empile au lieu de juxtaposer : l'image en haut, tout le texte dessous.
     *
     * C'est la forme du portrait. Les deux colonnes supposent une largeur qu'un
     * téléphone n'a pas, et le texte posé **sur** l'image suppose une moitié
     * libre qui n'existe pas non plus quand l'image est un 16:9 pleine largeur.
     */
    enColonne: Boolean = false,
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
                modifier = Modifier
                    // **En colonne, l'image garde son format.**
                    //
                    // Étirée sur tout le cadre — image plus bloc de texte — un
                    // 16:9 se retrouvait recadré en portrait : on n'en voyait
                    // plus qu'une bande centrale très agrandie. Elle occupe
                    // donc sa propre hauteur, en haut, et le texte prend le
                    // reste.
                    .then(
                        if (enColonne) {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .align(Alignment.TopCenter)
                        } else {
                            Modifier.fillMaxSize()
                        },
                    )
                    .then(if (backdropUrl == null) Modifier.blur(48.dp) else Modifier),
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
        // **Le voile s'ancre sur le bas, en points, pas en fraction.**
        //
        // Des paliers exprimés en pourcentage supposent que le bloc de texte
        // occupe toujours la même part du cadre. Il n'en occupe pas du tout la
        // même : il fait à peu près la même hauteur physique partout — un
        // titre, une ligne de méta, un bouton, une rangée d'icônes — alors que
        // le cadre, lui, suit l'écran. Sur une fenêtre de bureau haute de
        // 1 045 dp, le hero en fait 941 et le texte un quart ; sur un
        // téléviseur 1080p, qui ne fait que 540 dp de haut, le hero tombe à 436
        // et le même texte en occupe plus de la moitié.
        //
        // Les mêmes fractions y noircissaient donc la moitié de l'image, et le
        // cadre se lisait comme une bande d'image posée sur un bloc noir —
        // exactement ce qu'on nous a remonté d'un salon. Ancré en points, le
        // voile couvre le texte et rien de plus, quelle que soit la hauteur.
        val voile = when {
            // **Le voile mord dans l'image, il ne commence pas après elle.**
            //
            // Calé sur le bas exact de l'image, il n'assombrissait que le texte
            // — qui est déjà sur le fond de la page — et l'image se terminait
            // par une arête franche au milieu de l'écran. C'est précisément ce
            // qu'un dégradé existe pour éviter : une image qui s'arrête net se
            // lit comme une bannière posée là, pas comme le haut d'une page.
            //
            // Il commence donc dans son dernier tiers, et le raccord se fait
            // dans l'image elle-même.
            enColonne -> hauteur - hauteur / 16f * 9f * FONDU_IMAGE
            LocalHeightClass.current != HeightClass.EXPANDED -> HAUTEUR_VOILE_COURT
            else -> HAUTEUR_VOILE
        }
        val debutVoile = ((hauteur - voile) / hauteur).coerceIn(0.05f, 0.75f)
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x000A0A0A),
                    debutVoile to Color(0x000A0A0A),
                    // Le gros de l'assombrissement se fait sur la seconde
                    // moitié du voile : au-dessus, le texte n'a pas encore
                    // commencé et l'image n'a aucune raison de payer pour lui.
                    (debutVoile + (1f - debutVoile) * 0.45f) to Color(0x800A0A0A),
                    1f to MOOVIE_BG,
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

        // **Court quand l'appareil est court, pas quand le cadre l'est.**
        //
        // C'est la hauteur offerte par l'appareil qui décide de la typographie,
        // et le projet la nomme déjà : un téléviseur 1080p fait 540 dp de haut
        // ([HeightClass.MEDIUM]), une fenêtre de bureau plus de 900
        // ([HeightClass.EXPANDED]). Un seuil maison sur la hauteur du cadre
        // aurait dit à peu près la même chose, en inventant un second
        // vocabulaire pour la même question.
        val court = LocalHeightClass.current != HeightClass.EXPANDED
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = marge, vertical = if (court) 18.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(if (court) 8.dp else 12.dp),
        ) {
        // **Le titre occupe toute la largeur, au-dessus des deux colonnes.**
        //
        // Enfermé dans la colonne de gauche — un tiers du cadre —, il était
        // coupé dès qu'il dépassait trois mots : « The Lord of the Rings: The
        // Fellowship of the Ring » n'y tenait pas, et l'ellipse tombait au
        // milieu du titre qu'on venait chercher. C'est le seul élément du hero
        // dont la longueur ne se négocie pas : on lui donne la largeur.
        Text(
            titre,
            // `displayMedium` fait quarante-cinq points : sur un téléviseur,
            // un titre de deux mots y passe à la ligne et coûte cent vingt
            // points de cadre à lui seul. Un cran en dessous tient sur une
            // ligne et rend cette hauteur à l'image, sans qu'on lise moins bien
            // de trois mètres — la taille reste très au-dessus du reste.
            style = if (court) {
                MaterialTheme.typography.headlineLarge
            } else {
                MaterialTheme.typography.displayMedium
            },
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = LARGEUR_MAX_TITRE),
        )
        // **En portrait, une seule colonne.**
        //
        // Les deux colonnes supposent une largeur : sur 413 points, un tiers
        // pour le titre et ses boutons en laisse cent quarante — de quoi couper
        // « Whisper » en deux et empiler les icônes. Le portrait déroule donc
        // tout sous l'image : méta, actions, synopsis, crédits, dans l'ordre où
        // on les lit.
        if (enColonne) {
            val ligne = meta.filter { it.isNotBlank() }
            if (ligne.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ligne.forEach {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = HERO_DIM)
                    }
                }
            }
            actions()
            if (synopsis.isNotBlank()) {
                Text(
                    synopsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MOOVIE_TEXT_MUTED,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            credits.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = HERO_DIM,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        .background(MOOVIE_SURFACE_HIGH),
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
                verticalArrangement = Arrangement.spacedBy(if (court) 8.dp else 12.dp),
            ) {
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
                        color = MOOVIE_TEXT_MUTED,
                        maxLines = if (court) 3 else 4,
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
