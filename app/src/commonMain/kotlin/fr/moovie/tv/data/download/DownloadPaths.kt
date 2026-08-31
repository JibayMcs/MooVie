package fr.moovie.tv.data.download

import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.flow.first
import fr.moovie.tv.shared.systemeFichiers
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Racine des téléchargements.
 *
 * **Propre à l'application, donc effacée à la désinstallation.** C'est un choix,
 * pas un oubli : écrire ailleurs demande sur Android la permission *Accès à tous
 * les fichiers*, qui existe déjà pour les sauvegardes — mais un dossier de
 * plusieurs dizaines de gigaoctets abandonné au milieu du stockage de quelqu'un
 * qui a désinstallé l'app serait une bien pire surprise qu'un film à
 * retélécharger. La sauvegarde, elle, est petite et irremplaçable ; un film ne
 * l'est pas.
 *
 * Sur Android c'est le stockage *externe* propre à l'app et non `filesDir` :
 * la mémoire interne d'une box est souvent de huit gigaoctets, on y tiendrait
 * deux films.
 */
expect fun moovieDownloadsChemin(): String

/** Racine des téléchargements, en chemin okio. */
fun moovieDownloadsDir(): Path = moovieDownloadsChemin().toPath()

private val fs: FileSystem get() = systemeFichiers

/** Dossier d'un téléchargement : ses segments, sa playlist et sa fiche. */
fun downloadDir(key: String): Path = moovieDownloadsDir() / safeName(key)

/**
 * Nom de l'affiche, rangée dans le dossier du titre.
 *
 * À côté des segments et non dans un cache d'images : le cache de Coil est une
 * commodité d'affichage, que le système peut vider quand il veut et que rien
 * n'oblige à contenir ce titre-là. Or une bibliothèque hors ligne sans
 * vignettes est une liste de texte — c'est justement à ce moment que l'affiche
 * compte, puisqu'il n'y a plus de réseau pour aller la chercher.
 *
 * L'extension ment volontairement : TMDB sert du JPEG sous `.jpg`, mais Coil
 * lit l'en-tête du fichier, pas son nom.
 *
 * **Le nom dit le format attendu, et c'est une leçon payée.** La première
 * version s'appelait `poster.jpg` et enregistrait `Download.imageUrl` — lequel
 * vient de la carte « Reprendre », donc une image d'arrière-plan en 16:9. Dans
 * une colonne étroite, elle se recadrait sur son centre : un gros plan de visage
 * là où l'on attendait une affiche. Le fichier a changé de nom pour que les
 * bibliothèques déjà constituées reprennent la bonne image au lieu de garder la
 * mauvaise pour toujours.
 */
private const val POSTER_NAME = "poster-2x3.jpg"

/**
 * L'affiche d'un téléchargement, ou null si elle n'a pas été récupérée.
 *
 * Null plutôt qu'un fichier absent : l'appelant est une carte, et « pas
 * d'image » est pour elle une mise en page différente, pas une erreur. Les
 * titres téléchargés avant cette version n'en ont pas — ils restent lisibles,
 * sans vignette.
 */
fun downloadPoster(key: String): Path? = (downloadDir(key) / POSTER_NAME).takeIf { chemin ->
    val meta = fs.metadataOrNull(chemin)
    meta?.isRegularFile == true && (meta.size ?: 0L) > 0L
}

/**
 * Récupère l'affiche manquante d'un titre déjà téléchargé, et la rend.
 *
 * ### Pourquoi un rattrapage
 *
 * L'affiche n'est enregistrée qu'au **démarrage** d'un téléchargement. Tout ce
 * qui était déjà sur le disque avant cette version n'en a donc pas, et la
 * bibliothèque serait restée une liste de texte pour qui en avait le plus —
 * exactement les gens à qui la vignette sert le plus. Le rattrapage se fait à
 * l'affichage, une fois, tant qu'il y a du réseau.
 *
 * Au meilleur effort et silencieux : hors ligne, l'appel échoue et la carte
 * reste telle quelle. Une vignette n'a jamais valu un message d'erreur.
 */
suspend fun fetchDownloadPoster(
    key: String,
    tmdbId: Int,
    isTv: Boolean,
    imageUrl: String? = null,
): Path? {
    downloadPoster(key)?.let { return it }
    val dir = downloadDir(key)
    if (fs.metadataOrNull(dir)?.isDirectory != true) return null
    val url = posterUrlOf(tmdbId, isTv) ?: imageUrl?.takeIf { it.isNotBlank() } ?: return null
    val cible = dir / POSTER_NAME
    return runCatching {
        ByteFetcherKtor().fetch(url, emptyMap(), cible)
        downloadPoster(key)
            // L'image de la première version, devenue inutile : quelques dizaines
            // de kilo-octets, mais sur une bibliothèque de cent titres c'est un
            // ménage qui vaut la ligne.
            ?.also { runCatching { fs.delete(dir / "poster.jpg") } }
    }.getOrElse {
        runCatching { fs.delete(cible) }
        null
    }
}

/**
 * L'affiche du titre chez TMDB, en 2:3, ou null.
 *
 * **Demandée à TMDB plutôt que reprise de [Download.imageUrl]** : ce champ sert
 * d'abord la carte « Reprendre », qui veut une image large, et il contient donc
 * un arrière-plan. C'est la même affiche que l'accueil montre — c'est le point,
 * les deux listes doivent se ressembler.
 *
 * Sans clé ou sans réseau, null : l'appelant se rabat alors sur ce qu'il a.
 */
private suspend fun posterUrlOf(tmdbId: Int, isTv: Boolean): String? {
    if (tmdbId <= 0) return null
    val cle = runCatching { SettingsRepository().tmdbApiKey.first() }.getOrNull()
    if (cle.isNullOrBlank()) return null
    val repo = TmdbRepository()
    return runCatching {
        if (isTv) repo.tvDetails(cle, tmdbId).posterUrl() else repo.movieDetails(cle, tmdbId).posterUrl()
    }.getOrNull()
}

/**
 * Une clé média (`tv:1396:s1e1`) porte des `:` que Windows refuse et que FAT32
 * n'aime pas davantage. On la transpose plutôt que de forger un identifiant de
 * plus : le dossier reste lisible à l'œil quand on va voir ce qui occupe le
 * disque.
 */
internal fun safeName(key: String): String = key.map {
    if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_'
}.joinToString("")
