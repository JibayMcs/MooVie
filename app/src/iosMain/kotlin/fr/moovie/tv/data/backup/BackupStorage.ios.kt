package fr.moovie.tv.data.backup

import fr.moovie.tv.shared.systemeFichiers
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * Sauvegardes iOS : un unique support, le dossier Documents de l'application.
 *
 * ### Pourquoi Documents, contrairement à tout le reste
 *
 * Les réglages et les téléchargements vont dans Application Support ou dans
 * Caches, précisément pour rester hors de vue. Une sauvegarde est l'inverse :
 * son intérêt est de **sortir de l'appareil**. Documents est le seul répertoire
 * qu'iOS expose dans l'application Fichiers, donc le seul depuis lequel
 * l'utilisateur peut copier son export vers iCloud, AirDrop ou un ordinateur.
 * C'est le pendant de la clé USB d'Android TV.
 *
 * Cela suppose `UIFileSharingEnabled` et `LSSupportsOpeningDocumentsInPlace`
 * dans l'Info.plist, sans quoi le dossier reste invisible et la fonction perd
 * tout son sens.
 *
 * ### Un seul support, et pas de permission à demander
 *
 * iOS n'a ni support amovible ni notion d'« accès à tous les fichiers » : le bac
 * à sable est la seule racine accessible, et l'app y écrit de plein droit. Les
 * deux fonctions de permission répondent donc franchement plutôt que de faire
 * mine de négocier quelque chose.
 */
@OptIn(ExperimentalForeignApi::class)
private fun dossierSauvegardes(): String {
    val repertoire = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val base = repertoire?.path ?: error("répertoire Documents introuvable")
    return "$base/Sauvegardes"
}

actual fun backupTargets(): List<BackupTarget> = listOf(
    BackupTarget(
        id = dossierSauvegardes(),
        // Le nom que l'utilisateur verra dans l'app Fichiers, pour qu'il sache
        // où aller chercher.
        label = "Fichiers · Moo-vie",
        removable = false,
        writableAtRoot = true,
    ),
)

actual fun canWriteBackupRoot(): Boolean = true

/** Rien à demander : le bac à sable est accessible sans permission. */
actual fun requestBackupRootAccess(): Boolean = true

actual fun writeBackup(targetId: String, fileName: String, content: String): String? = runCatching {
    val dossier = targetId.toPath()
    systemeFichiers.createDirectories(dossier)
    val fichier = dossier / fileName
    systemeFichiers.write(fichier) { writeUtf8(content) }
    fichier.toString()
}.getOrNull()

actual fun findBackups(): List<BackupFile> = runCatching {
    val dossier = dossierSauvegardes().toPath()
    systemeFichiers.listOrNull(dossier).orEmpty()
        .mapNotNull { chemin ->
            val meta = systemeFichiers.metadataOrNull(chemin) ?: return@mapNotNull null
            if (!meta.isRegularFile) return@mapNotNull null
            BackupFile(
                path = chemin.toString(),
                name = chemin.name,
                sizeBytes = meta.size ?: 0L,
                modifiedAt = meta.lastModifiedAtMillis ?: 0L,
                targetLabel = "Fichiers · Moo-vie",
            )
        }
}.getOrDefault(emptyList())

actual fun readBackup(path: String): String? = runCatching {
    systemeFichiers.read(path.toPath()) { readUtf8() }
}.getOrNull()
