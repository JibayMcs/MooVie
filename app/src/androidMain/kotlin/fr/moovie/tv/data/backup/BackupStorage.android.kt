package fr.moovie.tv.data.backup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import fr.moovie.tv.data.store.appContext
import java.io.File

/**
 * Supports vus par Android : les volumes montés, amovibles en premier.
 *
 * `StorageManager.storageVolumes` est la seule source qui distingue une clé USB
 * du stockage interne et qui en donne un **libellé lisible** (« SanDisk »)
 * plutôt qu'un UUID. Son `directory` n'existe qu'à partir d'Android 11 ; en
 * dessous, et en repli général, on passe par `getExternalFilesDirs`, qui rend un
 * dossier par volume sans demander la moindre permission.
 */
actual fun backupTargets(): List<BackupTarget> {
    val root = canWriteBackupRoot()
    val targets = linkedMapOf<String, BackupTarget>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val sm = appContext.getSystemService(StorageManager::class.java)
        sm?.storageVolumes.orEmpty().forEach { volume ->
            val dir = runCatching { volume.directory }.getOrNull() ?: return@forEach
            if (!dir.canRead()) return@forEach
            targets[dir.absolutePath] = BackupTarget(
                id = dir.absolutePath,
                label = volume.getDescription(appContext) ?: dir.name,
                removable = volume.isRemovable,
                writableAtRoot = root && dir.canWrite(),
            )
        }
    }

    // Dossiers réservés à l'app, un par volume. Toujours accessibles en
    // écriture, et — c'est ce qui compte — **au même chemin sur tout appareil**,
    // puisqu'il dérive du nom de paquet : la TV suivante y lira sans permission.
    appContext.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
        val volumeRoot = dir.absolutePath.substringBefore("/Android/data")
        if (targets.containsKey(volumeRoot)) return@forEach
        targets[volumeRoot] = BackupTarget(
            id = dir.absolutePath,
            label = if (Environment.isExternalStorageRemovable(dir)) {
                "Stockage amovible"
            } else {
                "Stockage interne"
            },
            removable = runCatching { Environment.isExternalStorageRemovable(dir) }.getOrDefault(false),
            writableAtRoot = false,
        )
    }

    // Amovible d'abord : c'est le support qui sert à migrer.
    return targets.values.sortedByDescending { it.removable }
}

actual fun canWriteBackupRoot(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

/**
 * Écran système « Accès à tous les fichiers », ouvert sur **notre** fiche.
 *
 * `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` avec l'URI du paquet mène
 * directement à l'interrupteur de Moo-vie ; la variante sans URI ouvre la liste
 * de toutes les applications, où il faut ensuite se chercher soi-même — pénible
 * au doigt, décourageant à la télécommande.
 *
 * `NEW_TASK` est obligatoire : on part du contexte d'application, pas d'une
 * activité. Et l'on vérifie que quelque chose répond avant de lancer — toutes
 * les ROM de boîtier TV ne fournissent pas cet écran, et une exception ici
 * ferait tomber l'app au moment précis où elle prétend aider.
 */
actual fun requestBackupRootAccess(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    val intent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.fromParts("package", appContext.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    if (intent.resolveActivity(appContext.packageManager) == null) return false
    return runCatching { appContext.startActivity(intent); true }.getOrDefault(false)
}

actual fun writeBackup(targetId: String, fileName: String, content: String): String? = runCatching {
    val dir = File(targetId)
    // Sans la permission « tous les fichiers », la racine d'un volume n'est pas
    // inscriptible : on retombe sur le dossier de l'app du même volume.
    val target = if (dir.canWrite()) dir else appFilesDirOn(targetId) ?: return null
    target.mkdirs()
    val file = File(target, fileName)
    file.writeText(content)
    file.absolutePath
}.getOrNull()

actual fun findBackups(): List<BackupFile> = backupTargets()
    .flatMap { target ->
        val dirs = listOfNotNull(File(target.id), appFilesDirOn(target.id)).distinct()
        dirs.flatMap { dir ->
            runCatching {
                dir.listFiles { f -> f.isFile && f.name.endsWith(BACKUP_EXTENSION) }.orEmpty().toList()
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
    }
    .distinctBy { it.path }
    .sortedByDescending { it.modifiedAt }

actual fun readBackup(path: String): String? =
    runCatching { File(path).takeIf { it.isFile }?.readText() }.getOrNull()

/** Dossier réservé à l'app sur le volume qui contient [path]. */
private fun appFilesDirOn(path: String): File? =
    appContext.getExternalFilesDirs(null).filterNotNull().firstOrNull {
        it.absolutePath.startsWith(path.substringBefore("/Android/data"))
    }
