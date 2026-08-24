package fr.moovie.tv.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import java.awt.Rectangle
import java.awt.Window

/**
 * Plein écran **fenêtré**, à la manière des jeux : on retire le cadre et on
 * couvre l'écran, sans jamais changer de mode d'affichage.
 *
 * ## Pourquoi ne pas se contenter de `WindowPlacement.Fullscreen`
 *
 * Celui-ci passe par le plein écran **exclusif** de Java
 * (`GraphicsDevice.setFullScreenWindow`), qui prend la main sur le mode
 * d'affichage. Mesuré sur un poste à Quadro 2000 (pilote de 2015, sans
 * DirectX 12 — Skiko journalise `Failed to choose DirectX12 adapter` et se
 * rabat) : la fenêtre s'agrandit bien, et **l'écran devient noir**. Seule la
 * souris reste visible. Échap ne rend même plus la main : il n'y a plus d'issue
 * que par le gestionnaire de tâches.
 *
 * Piège de méthode, payé cher : une capture d'écran GDI montre pourtant une
 * image. Ce que le compositeur recompose n'est pas ce que la carte envoie à la
 * dalle, et on peut donc « voir » une fenêtre saine devant un écran éteint. Sur
 * ce défaut-là, **l'œil de l'utilisateur est le seul instrument**.
 *
 * ## Ce que fait celui-ci à la place
 *
 * Rien qui touche au mode d'affichage :
 *
 * 1. on retire du style de la fenêtre tout ce qui dessine un cadre — titre,
 *    bordure de redimensionnement, boutons ;
 * 2. on la pose exactement sur les bornes de **son** écran (pas du principal :
 *    sur deux moniteurs, l'un n'est pas l'autre) ;
 * 3. on la met au-dessus, ce qui recouvre la barre des tâches.
 *
 * Le mode d'affichage ne bouge pas, la carte continue de balayer ce qu'elle
 * balayait, et le compositeur fait le reste. C'est la raison pour laquelle ça
 * marche là où l'exclusif échoue.
 *
 * ## Pourquoi au niveau Win32 et pas dans Compose
 *
 * `Window(undecorated = …)` de Compose Desktop ne se change pas en cours de
 * route : le drapeau n'est lisible qu'à la construction, et le modifier ensuite
 * imposerait de recréer la fenêtre — donc de détruire la composition, donc le
 * lecteur et son moteur mpv, en pleine lecture. Changer le style de la fenêtre
 * native ne recrée rien.
 */
object PleinEcranSansBordure {

    /** Le style d'origine et les bornes, pour savoir revenir. */
    private data class Etat(val style: Long, val bornes: Rectangle)

    private val memoire = HashMap<Window, Etat>()

    /** Vrai si cette plateforme sait faire — Windows seulement. */
    val disponible: Boolean get() = Platform.isWindows()

    /**
     * Retire le cadre et couvre l'écran. Sans effet si déjà appliqué.
     *
     * Rend faux si quoi que ce soit échoue : l'appelant doit alors garder son
     * comportement habituel plutôt que de laisser l'utilisateur devant une
     * fenêtre à moitié transformée.
     */
    fun entre(fenetre: Window): Boolean {
        if (!disponible || memoire.containsKey(fenetre)) return memoire.containsKey(fenetre)
        return runCatching {
            val hwnd = Native.getWindowPointer(fenetre) ?: return false
            val style = User32.INSTANCE.GetWindowLongPtrW(hwnd, GWL_STYLE)
            val ecran = fenetre.graphicsConfiguration?.bounds ?: return false

            memoire[fenetre] = Etat(style, fenetre.bounds)
            User32.INSTANCE.SetWindowLongPtrW(hwnd, GWL_STYLE, style and CADRE.inv())
            User32.INSTANCE.SetWindowPos(
                hwnd, HWND_TOP,
                ecran.x, ecran.y, ecran.width, ecran.height,
                SWP_FRAMECHANGED or SWP_SHOWWINDOW or SWP_NOOWNERZORDER,
            )
            // Au-dessus de tout : sans ça la barre des tâches, qui est elle-même
            // au-dessus, resterait posée en travers du bas de l'image.
            fenetre.isAlwaysOnTop = true
            true
        }.getOrElse {
            memoire.remove(fenetre)
            false
        }
    }

    /** Rétablit le cadre et les bornes d'avant. Sans effet si on n'y était pas. */
    fun sort(fenetre: Window) {
        val etat = memoire.remove(fenetre) ?: return
        runCatching {
            fenetre.isAlwaysOnTop = false
            val hwnd = Native.getWindowPointer(fenetre) ?: return
            User32.INSTANCE.SetWindowLongPtrW(hwnd, GWL_STYLE, etat.style)
            User32.INSTANCE.SetWindowPos(
                hwnd, HWND_TOP,
                etat.bornes.x, etat.bornes.y, etat.bornes.width, etat.bornes.height,
                SWP_FRAMECHANGED or SWP_SHOWWINDOW or SWP_NOOWNERZORDER,
            )
        }
    }

    /** Vrai si cette fenêtre est actuellement sans bordure de notre fait. */
    fun actif(fenetre: Window): Boolean = memoire.containsKey(fenetre)

    /**
     * `user32` réduit à trois appels.
     *
     * Déclarée à la main plutôt que via `jna-platform` : celui-ci pèse plusieurs
     * mégaoctets pour des centaines d'API dont nous n'utilisons rien, et le
     * projet ajoute une dépendance seulement quand elle porte son poids.
     */
    private interface User32 : Library {
        fun GetWindowLongPtrW(hwnd: Pointer, index: Int): Long
        fun SetWindowLongPtrW(hwnd: Pointer, index: Int, valeur: Long): Long
        fun SetWindowPos(
            hwnd: Pointer,
            apres: Pointer?,
            x: Int,
            y: Int,
            largeur: Int,
            hauteur: Int,
            drapeaux: Int,
        ): Boolean

        companion object {
            val INSTANCE: User32 by lazy { Native.load("user32", User32::class.java) }
        }
    }

    private const val GWL_STYLE = -16

    /** Tout ce qui dessine un cadre : titre, bordures, boutons, menu système. */
    private const val CADRE = 0x00C00000L or // WS_CAPTION
        0x00040000L or // WS_THICKFRAME
        0x00080000L or // WS_SYSMENU
        0x00020000L or // WS_MINIMIZEBOX
        0x00010000L // WS_MAXIMIZEBOX

    private val HWND_TOP: Pointer? = null
    private const val SWP_FRAMECHANGED = 0x0020
    private const val SWP_SHOWWINDOW = 0x0040
    private const val SWP_NOOWNERZORDER = 0x0200
}
