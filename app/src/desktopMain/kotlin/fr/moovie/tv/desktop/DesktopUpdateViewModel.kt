package fr.moovie.tv.desktop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.UpdateInterval
import fr.moovie.tv.data.update.UpdateRepository
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.update_error
import fr.moovie.tv.ui.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

/**
 * Vérifie la dernière release GitHub au démarrage puis à l'intervalle choisi
 * dans les réglages.
 *
 * Deux chemins de mise à jour en place :
 *  - AppImage (Linux) : le paquet est un fichier unique appartenant à
 *    l'utilisateur, on le télécharge, on se remplace et on relance.
 *  - MSI (Windows) : depuis la 1.6.x le paquet s'installe par utilisateur
 *    (`perUserInstall`), donc msiexec peut réinstaller par-dessus sans
 *    élévation UAC ; l'`upgradeUuid` figé fait de la nouvelle version une mise
 *    à niveau majeure qui remplace l'ancienne. On télécharge le `.msi`, on
 *    lance msiexec depuis un script qui attend notre fermeture puis relance.
 *
 * Partout ailleurs (lancement depuis les sources, format sans self-update) on
 * retombe sur l'ouverture de la page de release dans le navigateur.
 */
class DesktopUpdateViewModel : ViewModel() {

    private val repo = UpdateRepository()
    private val settings = SettingsRepository()
    private val currentVersion = System.getProperty("moovie.version") ?: "0.0.0"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = _state

    /** Version écartée par « Plus tard », à ne plus proposer d'ici la fin de session. */
    private var dismissedVersion: String? = null

    /** Asset .AppImage de la release courante, si elle en propose un. */
    private var appImageAssetUrl: String? = null

    /** Asset .msi de la release courante, si elle en propose un. */
    private var msiAssetUrl: String? = null

    private val isWindows =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /**
     * Fichier AppImage en cours d'exécution. Le runtime AppImage exporte
     * `APPIMAGE` : c'est le seul indicateur fiable, et son absence signifie
     * qu'on ne doit surtout pas tenter de se remplacer.
     */
    private val runningAppImage: File? =
        System.getenv("APPIMAGE")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }

    init {
        viewModelScope.launch {
            settings.updateInterval.collectLatest { interval ->
                if (interval == UpdateInterval.NEVER) {
                    if (_state.value is UpdateState.Available) _state.value = UpdateState.None
                    return@collectLatest
                }
                while (true) {
                    check()
                    delay(interval.minutes * 60_000L)
                }
            }
        }
    }

    private suspend fun check() {
        val release = repo.latestRelease() ?: return
        if (release.draft || release.prerelease) return
        if (!repo.isNewer(release.tagName, currentVersion)) return
        val version = release.tagName.removePrefix("v")
        if (version == dismissedVersion) return
        appImageAssetUrl = release.assets
            .firstOrNull { it.name.endsWith(".AppImage", ignoreCase = true) }
            ?.downloadUrl
        msiAssetUrl = release.assets
            .firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
            ?.downloadUrl
        // apkUrl transporte ici l'URL de la page de release (repli navigateur).
        _state.value = UpdateState.Available(version, release.htmlUrl)
    }

    /** « Plus tard » : masque cette version jusqu'au prochain démarrage. */
    fun dismiss() {
        dismissedVersion = (_state.value as? UpdateState.Available)?.version
        _state.value = UpdateState.None
    }

    /**
     * AppImage : télécharge la nouvelle version, remplace le fichier courant et
     * relance. Windows/MSI : télécharge le `.msi` et le passe à msiexec. Sinon :
     * ouvre la page de release dans le navigateur.
     */
    fun install() {
        val available = _state.value as? UpdateState.Available ?: return
        val appImage = runningAppImage
        val appImageUrl = appImageAssetUrl
        val msiUrl = msiAssetUrl
        when {
            appImage != null && !appImageUrl.isNullOrBlank() ->
                viewModelScope.launch { replaceSelf(available.version, appImageUrl, appImage) }

            isWindows && !msiUrl.isNullOrBlank() ->
                viewModelScope.launch { installMsi(available.version, msiUrl) }

            available.apkUrl.isNotBlank() ->
                runCatching { Desktop.getDesktop().browse(URI(available.apkUrl)) }
        }
    }

    private suspend fun replaceSelf(version: String, url: String, target: File) {
        _state.value = UpdateState.Downloading(version, 0f)
        // Fichier temporaire dans le même répertoire : le remplacement final est
        // un rename, qui n'est atomique qu'au sein d'un même système de fichiers.
        val staged = File(target.parentFile, "${target.name}.new")
        val downloaded = repo.downloadApk(url, staged) { progress ->
            _state.value = UpdateState.Downloading(version, progress)
        }
        if (!downloaded || !isAppImage(staged)) {
            staged.delete()
            _state.value = UpdateState.Error(getString(Res.string.update_error))
            return
        }
        val replaced = withContext(Dispatchers.IO) {
            runCatching {
                staged.setExecutable(true, false)
                // On ne peut pas écrire dans un exécutable en cours d'exécution
                // (ETXTBSY), mais on peut renommer par-dessus : le process
                // courant garde l'ancien inode jusqu'à sa sortie.
                Files.move(
                    staged.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.isSuccess
        }
        if (!replaced) {
            staged.delete()
            _state.value = UpdateState.Error(getString(Res.string.update_error))
            return
        }
        relaunch(target)
        exitProcess(0)
    }

    /**
     * Windows : télécharge le `.msi` de la release et le confie à msiexec via un
     * script qui attend la fermeture de cette instance avant de réécrire les
     * fichiers, puis relance l'app.
     */
    private suspend fun installMsi(version: String, url: String) {
        _state.value = UpdateState.Downloading(version, 0f)
        // %TEMP% et non le dossier d'install : les fichiers de l'app sont sur le
        // point d'être remplacés par msiexec, on n'écrit rien dedans.
        val staged = File(System.getProperty("java.io.tmpdir"), "Moo-vie-$version.msi")
        val downloaded = repo.downloadApk(url, staged) { progress ->
            _state.value = UpdateState.Downloading(version, progress)
        }
        if (!downloaded || !isMsi(staged)) {
            staged.delete()
            _state.value = UpdateState.Error(getString(Res.string.update_error))
            return
        }
        val launched = withContext(Dispatchers.IO) {
            runCatching { launchMsiInstaller(staged) }.isSuccess
        }
        if (!launched) {
            staged.delete()
            _state.value = UpdateState.Error(getString(Res.string.update_error))
            return
        }
        // On sort tout de suite : msiexec, détaché, ne peut pas mettre à niveau
        // tant que l'exécutable courant tient ses fichiers ouverts.
        exitProcess(0)
    }

    /**
     * Écrit et démarre, détaché, un script qui : attend quelques secondes que ce
     * processus meure, lance msiexec (`/qb` : barre de progression, aucune
     * question ; pas d'UAC car l'install est par utilisateur), relance l'app
     * fraîchement installée, puis se supprime.
     *
     * Le script est nécessaire parce qu'on ne peut ni attendre msiexec (il doit
     * survivre à notre mort) ni relancer l'app depuis un processus qu'on tue.
     */
    private fun launchMsiInstaller(msi: File) {
        // Lanceur natif jpackage (Moo-vie.exe) : même chemin après une mise à
        // niveau majeure au même INSTALLDIR, donc relançable tel quel. Absent →
        // on renonce à la relance, l'utilisateur rouvre via le raccourci.
        val exe = ProcessHandle.current().info().command().orElse(null)
        val log = File(System.getProperty("java.io.tmpdir"), "Moo-vie-update.log")
        val script = File.createTempFile("moovie-update", ".cmd").apply {
            writeText(
                buildString {
                    appendLine("@echo off")
                    // ping plutôt que timeout : ce dernier exige un vrai console
                    // input, absent d'un process démarré détaché.
                    appendLine("ping 127.0.0.1 -n 3 >nul")
                    appendLine(
                        "msiexec /i \"${msi.absolutePath}\" /qb /norestart " +
                            "/l*v \"${log.absolutePath}\"",
                    )
                    if (exe != null) appendLine("start \"\" \"$exe\"")
                    // %~f0 : le script se supprime lui-même en dernier.
                    appendLine("del \"%~f0\"")
                },
            )
        }
        // start "" /min : la fenêtre du script est détachée de ce process et
        // survivra à exitProcess ; minimisée pour ne pas surgir au premier plan.
        ProcessBuilder("cmd", "/c", "start", "", "/min", script.absolutePath)
            .apply { scrubLauncherEnv(environment()) }
            .start()
    }

    /**
     * Relance la nouvelle version dans sa propre session, une fois ce processus
     * mort.
     *
     * `setsid` détache l'enfant de la session courante et les sorties sont
     * jetées : branchées sur des tubes, elles seraient refermées par la mort du
     * parent. La seconde tentative couvre l'absence de `setsid`, absent de
     * certaines images minimales.
     */
    private fun relaunch(target: File) {
        // Une seconde d'attente avant de démarrer : le runtime AppImage démonte
        // son squashfs en sortant, autant le laisser finir.
        val script = "sleep 1; exec '" + target.absolutePath.replace("'", "'\\''") + "'"
        val attempts = listOf(
            listOf("setsid", "--fork", "sh", "-c", script),
            listOf("sh", "-c", "$script &"),
        )
        for (command in attempts) {
            val started = runCatching {
                ProcessBuilder(command)
                    .apply { scrubLauncherEnv(environment()) }
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            }
            if (started.isSuccess) return
            // Sans cette trace, un échec de relance était totalement muet :
            // l'app se fermait et rien ne se passait.
            System.err.println(
                "MOOVIE: relance impossible via ${command.first()} — " +
                    "${started.exceptionOrNull()?.message}",
            )
        }
    }

    /**
     * Purge de l'environnement transmis à la nouvelle instance les variables
     * qui n'ont de sens que pour le processus courant.
     *
     * `_JPACKAGE_LAUNCHER` est la cause du bug qui empêchait l'app de se rouvrir
     * après mise à jour : le lanceur jpackage se ré-exécute une fois et se passe
     * les arguments de la JVM par cette variable. En l'héritant, la nouvelle
     * instance se croyait déjà dans sa seconde passe, ne lisait plus son fichier
     * `.cfg` et démarrait une JVM sans classe principale — elle affichait l'aide
     * de `java` et s'arrêtait aussitôt.
     *
     * Les variables du runtime AppImage pointent, elles, vers un point de
     * montage sur le point de disparaître ; le nouveau runtime les repositionne.
     */
    private fun scrubLauncherEnv(env: MutableMap<String, String>) {
        listOf("_JPACKAGE_LAUNCHER", "APPDIR", "APPIMAGE", "ARGV0", "OWD", "LD_LIBRARY_PATH")
            .forEach(env::remove)
    }

    /**
     * Vérifie la signature AppImage de type 2 : un ELF portant `AI\x02` aux
     * octets 8 à 10. Sans ce contrôle, une page d'erreur HTML téléchargée à la
     * place du binaire remplacerait l'application par un fichier inutilisable —
     * et il n'y aurait plus d'app pour se rattraper.
     */
    private suspend fun isAppImage(file: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(11)
                if (input.read(header) < header.size) return@use false
                val elf = header[0] == 0x7F.toByte() &&
                    header[1] == 'E'.code.toByte() &&
                    header[2] == 'L'.code.toByte() &&
                    header[3] == 'F'.code.toByte()
                elf &&
                    header[8] == 'A'.code.toByte() &&
                    header[9] == 'I'.code.toByte() &&
                    header[10] == 0x02.toByte()
            }
        }.getOrDefault(false)
    }

    /**
     * Vérifie que le fichier est bien un MSI : un `.msi` est un fichier composé
     * OLE2, reconnaissable à sa signature `D0 CF 11 E0 A1 B1 1A E1`. Sans ce
     * contrôle, une page d'erreur HTML servie à la place du binaire serait
     * passée à msiexec, qui échouerait sans qu'on sache pourquoi.
     */
    private suspend fun isMsi(file: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(8)
                if (input.read(header) < header.size) return@use false
                header.contentEquals(
                    byteArrayOf(
                        0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
                        0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte(),
                    ),
                )
            }
        }.getOrDefault(false)
    }
}
