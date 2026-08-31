package fr.moovie.tv.data.trailer

import fr.moovie.tv.data.store.moovieCacheChemin
import fr.moovie.tv.shared.maintenantMs
import fr.moovie.tv.shared.systemeFichiers
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Dépose le manifeste DASH d'une bande-annonce sur disque et rend son URI.
 *
 * ## Pourquoi un fichier plutôt qu'un serveur
 *
 * Le manifeste est fabriqué en mémoire, mais aucun des deux lecteurs ne sait
 * ouvrir une chaîne : ils veulent une URI. Le desktop a bien son
 * `LocalStreamProxy`, mais Android n'a pas d'équivalent, et en ajouter un pour
 * deux kilo-octets de XML reviendrait à faire tourner un serveur HTTP par
 * bande-annonce. Un `file://` marche à l'identique des deux côtés — ExoPlayer
 * déduit DASH de l'extension `.mpd`, libVLC aussi — et les URLs de segments
 * qu'il contient restent absolues, donc rien ne dépend de l'emplacement.
 *
 * Le répertoire est celui du **cache** : ces manifestes portent des URLs
 * googlevideo qui expirent en six heures, ils sont donc jetables par nature.
 * Que l'OS les purge est exactement le comportement voulu.
 */
class DashManifestStore(
    private val dir: Path = moovieCacheChemin("trailers").toPath(),
    private val fs: FileSystem = systemeFichiers,
) {

    /**
     * @return l'URI du manifeste, ou null si l'écriture échoue (disque plein,
     *         cache purgé sous nos pieds) — auquel cas l'appelant traite la
     *         bande-annonce comme absente, ce qu'elle est devenue.
     */
    fun write(manifest: String): String? = runCatching {
        fs.createDirectories(dir)
        prune()
        // Nom stable par contenu : rouvrir la même fiche réécrit le même fichier
        // au lieu d'en semer un par ouverture.
        val fichier = dir / "trailer-${manifest.hashCode().toUInt().toString(16)}.mpd"
        fs.write(fichier) { writeUtf8(manifest) }
        // Trois barres obliques, et non les deux de `File.toURI()` : celle-ci
        // rend `file:/home/…`, une URI valide que libVLC ne reconnaît pas — il
        // la prend pour un chemin relatif et la colle derrière le répertoire
        // courant, d'où un « cannot open file …/app/file:/home/… ». La forme à
        // trois barres est celle que les deux lecteurs attendent.
        //
        // `enCheminUri` remplace `URI.rawPath` — et reproduit sa règle exacte,
        // qui n'est pas celle d'un encodeur d'URL ordinaire : les caractères
        // non-ASCII restent intacts, seuls les ASCII interdits sont cités. Voir
        // la documentation de cette fonction, et `UriFichierTest`.
        "file://" + enCheminUri(fichier.toString())
    }.getOrNull()

    /**
     * Efface les manifestes périmés.
     *
     * Sans ça le répertoire grossit d'un fichier par bande-annonce regardée, et
     * chacun contient des URLs mortes : les garder n'a aucune valeur. Le seuil
     * suit l'expiration annoncée par YouTube (`expiresInSeconds`, ~6 h).
     */
    private fun prune() {
        val limite = maintenantMs() - EXPIRATION_MS
        fs.listOrNull(dir)?.forEach { f ->
            val meta = fs.metadataOrNull(f) ?: return@forEach
            if (meta.isRegularFile && (meta.lastModifiedAtMillis ?: 0L) < limite) fs.delete(f)
        }
    }

    private companion object {
        const val EXPIRATION_MS = 6L * 60 * 60 * 1000
    }
}
