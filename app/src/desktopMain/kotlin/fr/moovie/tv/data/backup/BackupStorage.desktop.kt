package fr.moovie.tv.data.backup

import java.io.File

/**
 * Supports vus depuis le bureau.
 *
 * Pas de `StorageManager` ici : on liste les points de montage habituels des
 * volumes amovibles selon l'OS, plus le dossier personnel qui sert de repli
 * toujours disponible. Écrire à la racine d'une clé ne demande aucune
 * permission sur un bureau — la question ne se pose que sur Android.
 */
actual fun backupTargets(): List<BackupTarget> {
    val user = System.getProperty("user.name").orEmpty()
    val removableRoots = listOf(
        // Linux : montages automatiques des environnements de bureau.
        File("/media/$user"), File("/run/media/$user"), File("/media"),
        // macOS.
        File("/Volumes"),
    )

    val removable = removableRoots
        .filter { it.isDirectory }
        .flatMap { root -> root.listFiles()?.filter { it.isDirectory && it.canWrite() }.orEmpty() }
        .map { BackupTarget(id = it.absolutePath, label = it.name, removable = true) }

    // Windows : les lettres de lecteur, en écartant le disque système.
    val windows = File.listRoots()
        .filter { it.canWrite() && !it.absolutePath.startsWith("C:") }
        .map { BackupTarget(id = it.absolutePath, label = it.absolutePath, removable = true) }

    val home = System.getProperty("user.home")?.let {
        BackupTarget(id = it, label = "Dossier personnel", removable = false)
    }

    return (removable + windows + listOfNotNull(home)).distinctBy { it.id }
}

/** Un bureau écrit où l'utilisateur a le droit d'écrire : rien à demander. */
actual fun canWriteBackupRoot(): Boolean = true

/**
 * Rien à demander : un utilisateur de bureau écrit déjà où il veut. Ce
 * `false` n'est pas un échec, c'est l'absence de question à poser — et il
 * suffit à ce que l'écran de sauvegarde n'affiche aucun bouton ici.
 */
actual fun requestBackupRootAccess(): Boolean = false

actual fun writeBackup(targetId: String, fileName: String, content: String): String? = runCatching {
    val dir = File(targetId).apply { mkdirs() }
    File(dir, fileName).also { it.writeText(content) }.absolutePath
}.getOrNull()

actual fun findBackups(): List<BackupFile> = backupTargets()
    .flatMap { target ->
        runCatching {
            File(target.id)
                .listFiles { f -> f.isFile && f.name.endsWith(BACKUP_EXTENSION) }
                .orEmpty().toList()
        }.getOrDefault(emptyList())
            .map {
                BackupFile(
                    path = it.absolutePath,
                    name = it.name,
                    sizeBytes = it.length(),
                    modifiedAt = it.lastModified(),
                    targetLabel = target.label,
                )
            }
    }
    .distinctBy { it.path }
    .sortedByDescending { it.modifiedAt }

actual fun readBackup(path: String): String? =
    runCatching { File(path).takeIf { it.isFile }?.readText() }.getOrNull()
