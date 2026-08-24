package fr.moovie.tv.data.subtitles

import fr.moovie.tv.core.subtitles.model.SubtitleCandidate
import fr.moovie.tv.core.subtitles.usecase.SubtitleTiming
import fr.moovie.tv.core.subtitles.usecase.retimeSrt
import fr.moovie.tv.data.store.moovieCacheDir
import java.io.File

/**
 * Garde sur disque les sous-titres téléchargés, et en produit des versions
 * recalées à la demande.
 *
 * **Le fichier d'origine n'est jamais réécrit.** Chaque réglage produit un
 * nouveau fichier dérivé, et l'original reste intact : un téléchargement coûte
 * une unité d'un quota qui se compte sur les doigts d'une main, le perdre parce
 * qu'un décalage a mal tourné serait inacceptable. Revenir en arrière ne coûte
 * donc rien.
 *
 * Chaque version recalée porte un **nom différent**. Ce n'est pas de la
 * cosmétique : Media3 met en cache par URI, et réécrire le même chemin
 * laisserait le lecteur afficher l'ancienne version. Le compteur garantit qu'un
 * nouveau réglage est un nouveau fichier, donc réellement rechargé.
 */
class SubtitleFileStore(
    private val dir: File = moovieCacheDir("subtitles"),
) {

    private var version = 0

    /** Écrit le sous-titre brut tel que téléchargé, et rend son fichier. */
    fun storeOriginal(mediaKey: String, candidate: SubtitleCandidate, content: String): File {
        dir.mkdirs()
        val file = File(dir, "${safe(mediaKey)}_${candidate.fileId}.srt")
        file.writeText(content)
        return file
    }

    /**
     * Produit la version à donner au lecteur. Sans réglage, c'est l'original
     * lui-même — inutile de recopier 46 Ko pour ne rien changer.
     */
    fun retimed(original: File, timing: SubtitleTiming): File {
        if (timing.isIdentity) return original
        dir.mkdirs()
        version++
        val out = File(dir, "${original.nameWithoutExtension}_v$version.srt")
        out.writeText(retimeSrt(original.readText(), timing))
        return out
    }

    /**
     * Efface les fichiers dérivés d'un média, en gardant les originaux.
     *
     * Appelé en quittant la lecture : ajuster un décalage pendant un film peut
     * en produire une dizaine, et rien ne justifie de les garder — alors que
     * l'original, lui, a été payé.
     */
    fun clearDerived(mediaKey: String) {
        val prefix = safe(mediaKey)
        dir.listFiles()
            ?.filter { it.name.startsWith(prefix) && it.name.contains("_v") }
            ?.forEach { it.delete() }
    }

    /**
     * Identifiants des sous-titres déjà téléchargés pour ce média.
     *
     * Sert à marquer la liste : sans ce repère, retrouver lequel des candidats
     * a déjà été payé relève du pari, et se tromper coûte une unité d'un quota
     * qui se compte sur les doigts d'une main.
     */
    fun storedIds(mediaKey: String): Set<String> {
        val prefix = safe(mediaKey) + "_"
        return dir.listFiles()
            ?.asSequence()
            ?.map { it.name }
            ?.filter { it.startsWith(prefix) && it.endsWith(".srt") }
            // Les versions recalées portent un suffixe `_v<n>` : ce sont des
            // dérivés du même téléchargement, pas des téléchargements de plus.
            ?.map { it.removePrefix(prefix).removeSuffix(".srt") }
            ?.filterNot { it.contains("_v") }
            ?.toSet()
            .orEmpty()
    }

    /** Sous-titre déjà téléchargé pour ce média et ce candidat, s'il existe. */
    fun existing(mediaKey: String, candidate: SubtitleCandidate): File? =
        File(dir, "${safe(mediaKey)}_${candidate.fileId}.srt").takeIf { it.isFile }

    /**
     * Le dernier sous-titre utilisé pour ce média, s'il y en a un.
     *
     * ## Pourquoi « le dernier » et pas « le choisi »
     *
     * Le choix vit dans le ViewModel du lecteur, et la diffusion Chromecast part
     * de la fiche — le lecteur n'a jamais été ouvert, il n'y a donc pas de choix
     * à lire. Ce qui reste sur le disque est en revanche parlant : c'est ce que
     * l'utilisateur a téléchargé pour ce titre, et recalé s'il l'a fait.
     *
     * D'où le tri par date de modification, qui fait remonter la **version
     * recalée** (`_v<n>`) avant l'originale : entre les deux, celle qu'on veut
     * envoyer est celle qu'il a ajustée.
     */
    fun dernierUtilise(mediaKey: String): File? {
        val prefix = safe(mediaKey) + "_"
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(".srt") }
            ?.maxByOrNull { it.lastModified() }
    }

    /** `tv:1396:s1e1` porte des deux-points, que Windows refuse dans un nom. */
    private fun safe(mediaKey: String): String =
        mediaKey.replace(Regex("""[^A-Za-z0-9_-]"""), "_")
}
