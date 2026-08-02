package fr.moovie.tv.data.backup

/**
 * Un support où déposer une sauvegarde : clé USB, stockage interne, dossier
 * utilisateur du desktop.
 *
 * @param id chemin absolu du dossier, aussi utilisé comme identifiant.
 * @param label ce que l'utilisateur lit — « Clé USB (SanDisk) », pas un chemin.
 * @param removable vrai pour un support amovible : c'est celui qu'on propose en
 *   premier, puisque tout l'intérêt est de passer d'un appareil à l'autre.
 * @param writableAtRoot faux quand on ne peut écrire que dans le dossier réservé
 *   à l'app faute de permission — l'UI le dit alors plutôt que de laisser
 *   l'utilisateur chercher son fichier à la racine.
 */
data class BackupTarget(
    val id: String,
    val label: String,
    val removable: Boolean,
    val writableAtRoot: Boolean = true,
)

/** Une sauvegarde trouvée sur un support. */
data class BackupFile(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    /** Support d'où elle vient, pour l'afficher dans la liste. */
    val targetLabel: String = "",
)

/**
 * Supports disponibles pour écrire une sauvegarde, amovibles en tête.
 *
 * **Android TV n'a pas de sélecteur de fichiers système** : `OPEN_DOCUMENT` y
 * résout vers un bouchon (`Stubs$DocumentsStub`) qui n'affiche rien. L'app doit
 * donc présenter les supports elle-même — d'où cette fonction, là où sur desktop
 * un dialogue natif aurait suffi.
 */
expect fun backupTargets(): List<BackupTarget>

/**
 * Vrai si l'app peut écrire à la racine des supports.
 *
 * Sur Android 11+ il faut pour cela « Accès à tous les fichiers ». Sans elle on
 * se rabat sur le dossier réservé à l'app, qui a l'avantage d'être lisible par
 * Moo-vie sur **n'importe quel autre appareil** sans permission — le chemin ne
 * dépend que du nom de paquet.
 */
expect fun canWriteBackupRoot(): Boolean

/** Écrit la sauvegarde et rend son chemin complet, ou null en cas d'échec. */
expect fun writeBackup(targetId: String, fileName: String, content: String): String?

/** Sauvegardes trouvées sur tous les supports, la plus récente en tête. */
expect fun findBackups(): List<BackupFile>

/** Contenu d'une sauvegarde, ou null si illisible. */
expect fun readBackup(path: String): String?

/** Extension retenue : reconnaissable, et ouvrable dans n'importe quel éditeur. */
const val BACKUP_EXTENSION = ".moovie.json"
