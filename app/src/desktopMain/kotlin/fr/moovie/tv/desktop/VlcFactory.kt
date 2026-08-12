package fr.moovie.tv.desktop

import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * La fabrique libVLC du processus, ou null si libVLC est introuvable.
 *
 * **Une seule pour toute l'application, et c'est le point.** La découverte
 * native repositionne `jna.library.path` et les chemins de recherche : la faire
 * deux fois, c'est risquer de charger deux bibliothèques différentes dans le
 * même processus. Depuis que le lecteur n'est plus seul à vouloir lire une vidéo
 * — l'aperçu de bande-annonce de la fiche s'y est ajouté — cette unicité devait
 * cesser d'être un effet de bord du `remember` de l'écran du lecteur.
 *
 * `lazy` et non un objet construit au démarrage : sur une machine sans VLC,
 * l'application doit s'ouvrir normalement et n'échouer qu'au moment de lire.
 */
/**
 * Le fil sur lequel on crée, lance et libère les lecteurs d'aperçu.
 *
 * **Un seul pour tout le processus, et c'est tout l'intérêt.** La première
 * version en créait un par aperçu — un exécutif à un fil, mais *par
 * composable*. Sérialiser chacun dans son coin ne sérialise rien du tout : en
 * changeant de fiche, la libération de l'ancien lecteur et la création du
 * nouveau tournaient en parallèle sur la même instance libVLC, et le processus
 * tombait en SIGSEGV dans `libvlc_media_player_new`.
 *
 * Jamais arrêté : il vit aussi longtemps que l'application, comme la fabrique
 * qu'il pilote. Démon, pour ne pas retenir la JVM à la fermeture.
 */
internal val vlcCommands: ExecutorService by lazy {
    Executors.newSingleThreadExecutor { r ->
        Thread(r, "moovie-vlc-preview").apply { isDaemon = true }
    }
}

internal val vlcFactory: MediaPlayerFactory? by lazy {
    runCatching {
        // Depuis l'AppImage, AppRun pose MOOVIE_VLC_HOME sur la libvlc
        // embarquée, et on saute alors la découverte de vlcj : celle-ci trouve
        // le VLC du système et repositionne jna.library.path par-dessus le
        // nôtre. On chargeait ainsi la libvlc de l'hôte avec notre libvlccore et
        // nos plugins — exactement le mélange de versions que l'embarquement
        // doit supprimer.
        val home = System.getenv("MOOVIE_VLC_HOME")?.takeIf { File(it, "libvlc.so").exists() }
        if (home != null) {
            System.setProperty("jna.library.path", home)
            NativeLibrary.addSearchPath("vlc", home)
            NativeLibrary.addSearchPath("vlccore", home)
        } else {
            NativeDiscovery().discover()
        }
        MediaPlayerFactory()
    }.onFailure {
        // Trace en console : indispensable pour diagnostiquer une libVLC
        // absente/incompatible (snap, version, JNA…).
        it.printStackTrace()
    }.getOrNull()
}
