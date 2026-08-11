package fr.moovie.tv.data.trailer

import fr.moovie.tv.data.store.moovieCacheDir
import java.io.File

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
class DashManifestStore(private val dir: File = moovieCacheDir("trailers")) {

    /**
     * @return l'URI du manifeste, ou null si l'écriture échoue (disque plein,
     *         cache purgé sous nos pieds) — auquel cas l'appelant traite la
     *         bande-annonce comme absente, ce qu'elle est devenue.
     */
    fun write(manifest: String): String? = runCatching {
        prune()
        // Nom stable par contenu : rouvrir la même fiche réécrit le même fichier
        // au lieu d'en semer un par ouverture.
        val file = File(dir, "trailer-${manifest.hashCode().toUInt().toString(16)}.mpd")
        file.writeText(manifest)
        // `File.toURI()` rend `file:/home/…` — **une seule** barre oblique, sans
        // autorité. C'est une URI valide, et libVLC ne la reconnaît pas : il la
        // prend pour un chemin relatif et la colle derrière le répertoire
        // courant, d'où un « cannot open file …/app/file:/home/… ». La forme à
        // trois barres est celle que les deux lecteurs attendent. `rawPath`
        // plutôt que `path` : il garde l'encodage, seul à survivre à un nom
        // d'utilisateur accentué.
        "file://" + file.toURI().rawPath
    }.getOrNull()

    /**
     * Efface les manifestes périmés.
     *
     * Sans ça le répertoire grossit d'un fichier par bande-annonce regardée, et
     * chacun contient des URLs mortes : les garder n'a aucune valeur. Le seuil
     * suit l'expiration annoncée par YouTube (`expiresInSeconds`, ~6 h).
     */
    private fun prune() {
        val limite = System.currentTimeMillis() - EXPIRATION_MS
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.lastModified() < limite) f.delete()
        }
    }

    private companion object {
        const val EXPIRATION_MS = 6L * 60 * 60 * 1000
    }
}
