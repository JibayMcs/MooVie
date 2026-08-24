package fr.moovie.tv.desktop.mpv

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference

/**
 * Liaison JNA vers libmpv — le strict nécessaire, écrit à la main.
 *
 * ## Pourquoi libmpv, et pourquoi un binding maison
 *
 * mpv est un lecteur complet bâti sur FFmpeg et exposé comme **une seule
 * bibliothèque** à l'API C stable : démultiplexage, décodage, synchronisation,
 * seek exact, pistes, sous-titres libass — tout ce que libVLC faisait mal et
 * que le moteur FFmpeg maison aurait dû réécrire. Pas de répertoire de
 * plugins : la panne 1.18.0 (un plugin `xml` manquant et plus aucune
 * bande-annonce) n'a pas d'équivalent ici.
 *
 * Le binding est à nous parce que l'API tient en une quinzaine de fonctions et
 * qu'aucune liaison Java publiée n'est à la fois maintenue et complète sur
 * l'API de rendu. Une dépendance morte est un risque qu'on connaît — c'est
 * l'histoire de ce projet avec ses lecteurs.
 *
 * ## Les règles de fil, en deux lignes
 *
 * L'API est **thread-safe par construction** : les commandes sont une file,
 * appelables de n'importe où. Deux exceptions, respectées par [MpvEngine] :
 * un seul fil dans `mpv_wait_event`, et le rappel de rendu ne doit que
 * signaler — jamais rappeler mpv.
 */
@Suppress("FunctionNaming", "TooManyFunctions")
internal interface Libmpv : Library {

    fun mpv_client_api_version(): Long

    fun mpv_create(): Pointer?

    fun mpv_initialize(ctx: Pointer): Int

    /** Détruit le client et attend la fin de ses fils. Synchones, sûre. */
    fun mpv_terminate_destroy(ctx: Pointer)

    fun mpv_error_string(error: Int): String

    fun mpv_free(data: Pointer)

    /** Avant `mpv_initialize` seulement ; après, passer par les propriétés. */
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int

    /** `args` se termine par un élément null — JNA s'en charge (StringArray). */
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int

    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int

    /** Le retour se libère avec [mpv_free] — d'où le Pointer et pas String. */
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?

    fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int

    fun mpv_set_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int

    fun mpv_request_log_messages(ctx: Pointer, minLevel: String): Int

    /** Chaque changement arrive en événement, étiqueté de [replyUserdata]. */
    fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int

    /**
     * Attend le prochain événement, [timeout] en secondes. Rend un pointeur
     * vers une structure **réutilisée au prochain appel** : tout ce qui doit
     * survivre est copié tout de suite, voir [MpvEvenement.depuis].
     */
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer

    /** Réveille [mpv_wait_event] — c'est ce qui permet d'arrêter son fil. */
    fun mpv_wakeup(ctx: Pointer)

    // ── API de rendu (render.h) ───────────────────────────────────────────

    fun mpv_render_context_create(res: PointerByReference, mpv: Pointer, params: Pointer): Int

    fun mpv_render_context_set_update_callback(ctx: Pointer, callback: RenduReveil?, callbackCtx: Pointer?)

    /** Drapeaux d'état ; le bit [MPV_RENDER_UPDATE_FRAME] dit « trame prête ». */
    fun mpv_render_context_update(ctx: Pointer): Long

    fun mpv_render_context_render(ctx: Pointer, params: Pointer): Int

    /** À appeler **avant** [mpv_terminate_destroy] — l'ordre est une exigence de l'API. */
    fun mpv_render_context_free(ctx: Pointer)

    /**
     * Rappel « quelque chose à redessiner », émis depuis un fil interne de mpv.
     * Interdiction d'y rappeler mpv : il ne fait que signaler.
     */
    interface RenduReveil : Callback {
        fun invoke(ctx: Pointer?)
    }

    @Suppress("MagicNumber")
    companion object {

        // mpv_format
        const val FORMAT_FLAG = 3
        const val FORMAT_INT64 = 4
        const val FORMAT_DOUBLE = 5

        // mpv_event_id
        const val EVENT_SHUTDOWN = 1
        const val EVENT_LOG_MESSAGE = 2
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
        const val EVENT_PLAYBACK_RESTART = 21
        const val EVENT_PROPERTY_CHANGE = 22

        // mpv_end_file_reason
        const val END_FILE_EOF = 0
        const val END_FILE_ERROR = 4

        // mpv_render_param_type
        const val RENDER_PARAM_API_TYPE = 1
        const val RENDER_PARAM_SW_SIZE = 17
        const val RENDER_PARAM_SW_FORMAT = 18
        const val RENDER_PARAM_SW_STRIDE = 19
        const val RENDER_PARAM_SW_POINTER = 20

        const val MPV_RENDER_UPDATE_FRAME = 1L

        /** `MPV_RENDER_PARAM_API_TYPE` : rendu logiciel, dans notre tampon. */
        const val RENDER_API_SW = "sw"

        /**
         * Les noms sous lesquels la bibliothèque peut se présenter, du plus
         * probable au moins probable. mpv ≥ 0.35 s'appelle `libmpv.so.2` /
         * `libmpv-2.dll`, les distributions LTS servent encore `libmpv.so.1`.
         */
        private val NOMS = when {
            Platform.isWindows() -> listOf("libmpv-2", "mpv-2", "mpv-1", "mpv")
            Platform.isMac() -> listOf("mpv", "libmpv.2.dylib", "libmpv.1.dylib")
            else -> listOf("mpv", "libmpv.so.2", "libmpv.so.1")
        }

        /**
         * Le fichier embarqué dans le paquet, quand il y en a un.
         *
         * jpackage dépose les ressources de l'application dans un répertoire
         * dont il donne le chemin en propriété système ; c'est là que la CI
         * pose libmpv pour Windows et macOS, dont les paquets n'ont pas de
         * `LD_LIBRARY_PATH` à leur disposition. Le chercher **avant** les noms
         * système garantit qu'on charge la bibliothèque qu'on a testée, et non
         * celle que la machine pourrait avoir par ailleurs — la leçon du
         * mélange de versions libVLC.
         */
        private fun embarquee(): String? {
            val racine = System.getProperty("compose.application.resources.dir") ?: return null
            val nom = when {
                Platform.isWindows() -> "libmpv-2.dll"
                Platform.isMac() -> "libmpv.2.dylib"
                else -> "libmpv.so.2"
            }
            return java.io.File(racine, nom).takeIf { it.isFile }?.absolutePath
        }

        /**
         * Charge la bibliothèque, une fois. Null si elle est introuvable — le
         * lecteur doit alors le **dire**, pas planter : c'est le contraire de
         * la panne silencieuse libVLC qui a coûté la 1.18.0.
         *
         * `moovie.mpv.path` reste prioritaire : c'est la porte de sortie pour
         * essayer une autre version sans reconstruire quoi que ce soit.
         */
        val instance: Libmpv? by lazy {
            regleLocaleNumerique()
            val chemins = listOfNotNull(System.getProperty("moovie.mpv.path"), embarquee()) + NOMS
            var dernierEchec: Throwable? = null
            val charge = chemins.firstNotNullOfOrNull { nom ->
                runCatching { Native.load(nom, Libmpv::class.java) }
                    .onFailure { dernierEchec = it }
                    .getOrNull()
            }
            if (charge == null) diagnostic = expliqueEchec(dernierEchec)
            charge
        }

        /**
         * Pourquoi le chargement a échoué, en une phrase montrable.
         *
         * ## Le défaut que ça corrige
         *
         * L'écran disait « lecteur introuvable » et conseillait de réinstaller.
         * Or sur la machine où ça s'est produit, **le fichier était là**, au bon
         * endroit et à la bonne taille : c'est une de ses dépendances qui
         * manquait. Le conseil était donc faux, et la réinstallation n'aurait
         * rien changé — on a cherché un fichier absent pendant qu'il était sous
         * nos yeux.
         *
         * Windows dit `LoadLibrary` **erreur 126, module introuvable** en
         * désignant la bibliothèque qu'on lui a demandée, jamais celle qui
         * manque réellement. C'est le piège, et il vaut la peine de le nommer :
         * quand le fichier existe, l'erreur ne parle pas de lui.
         */
        @Volatile
        var diagnostic: String? = null
            private set

        private fun expliqueEchec(cause: Throwable?): String {
            val chemin = embarquee()
            val message = cause?.message.orEmpty()
            return when {
                chemin == null ->
                    "libmpv n'est pas fournie avec cette installation."
                !java.io.File(chemin).isFile ->
                    "libmpv est déclarée dans $chemin mais le fichier n'y est pas."
                // Le cas mesuré : le fichier est présent et le chargeur refuse
                // quand même. Sous Windows, presque toujours une dépendance
                // absente — `vulkan-1.dll` sur une machine dont le pilote GPU
                // est trop ancien pour la fournir.
                else ->
                    "libmpv est présente ($chemin) mais n'a pas pu être chargée : " +
                        "une bibliothèque dont elle dépend manque sur ce système. " +
                        message.take(200)
            }
        }

        /**
         * `LC_NUMERIC=C`, exigé par libmpv (et par le FFmpeg qu'il embarque).
         *
         * La JVM cale la locale du process sur le système — français ici — et
         * un analyseur C lit alors « 1.5 » comme « 1 » suivi de déchets. Le
         * moteur FFmpeg maison est tombé exactement là-dessus : `tempo=1.0`
         * refusé pour ses décimales, uniquement hors CI anglophone. Problème
         * POSIX ; les builds Windows de mpv gèrent leur locale eux-mêmes.
         */
        private fun regleLocaleNumerique() {
            if (Platform.isWindows()) return
            runCatching {
                val libc = Native.load(if (Platform.isMac()) "c" else "c", LibC::class.java)
                libc.setlocale(LC_NUMERIC, "C")
            }
        }

        /** `LC_NUMERIC` vaut 1 sur glibc comme sur macOS. */
        private const val LC_NUMERIC = 1
    }
}

@Suppress("FunctionNaming")
private interface LibC : Library {
    fun setlocale(category: Int, locale: String): String?
}

/**
 * Un événement mpv, copié hors de la structure que `mpv_wait_event` réutilise.
 *
 * Les lectures se font par décalages plutôt que par une `Structure` JNA : la
 * disposition est triviale (deux int, un long, un pointeur), et l'accès direct
 * évite la réflexion de JNA sur un chemin parcouru en boucle.
 */
internal class MpvEvenement(
    val id: Int,
    val erreur: Int,
    /** `reply_userdata` : l'étiquette posée à `mpv_observe_property`. */
    val etiquette: Long,
    /** `MPV_EVENT_END_FILE` : raison de la fin, sinon 0. */
    val raisonFin: Int,
    /** `MPV_EVENT_END_FILE` : code d'erreur quand la raison est une erreur. */
    val erreurFin: Int,
    /** `MPV_EVENT_PROPERTY_CHANGE` d'un drapeau : sa valeur, sinon null. */
    val drapeau: Boolean?,
    /** `MPV_EVENT_LOG_MESSAGE` : "préfixe niveau : texte", sinon null. */
    val journal: String?,
) {
    companion object {
        /** Décalages dans `mpv_event` (LP64 comme Windows x64 : identiques). */
        private const val CHAMP_ID = 0L
        private const val CHAMP_ERREUR = 4L
        private const val CHAMP_ETIQUETTE = 8L
        private const val CHAMP_DONNEES = 16L

        @Suppress("MagicNumber")
        fun depuis(brut: Pointer): MpvEvenement {
            val id = brut.getInt(CHAMP_ID)
            val erreur = brut.getInt(CHAMP_ERREUR)
            val etiquette = brut.getLong(CHAMP_ETIQUETTE)
            val donnees = brut.getPointer(CHAMP_DONNEES)
            var raisonFin = 0
            var erreurFin = 0
            var drapeau: Boolean? = null
            var journal: String? = null
            when (id) {
                Libmpv.EVENT_END_FILE -> if (donnees != null) {
                    raisonFin = donnees.getInt(0)
                    erreurFin = donnees.getInt(4)
                }
                Libmpv.EVENT_LOG_MESSAGE -> if (donnees != null) {
                    // mpv_event_log_message : trois char* puis le niveau.
                    val prefixe = donnees.getPointer(0)?.getString(0)
                    val niveau = donnees.getPointer(8)?.getString(0)
                    val texte = donnees.getPointer(16)?.getString(0)?.trimEnd()
                    journal = "$prefixe $niveau : $texte"
                }
                Libmpv.EVENT_PROPERTY_CHANGE -> if (donnees != null) {
                    // mpv_event_property : char* name, int format, void* data.
                    val format = donnees.getInt(8)
                    val valeur = donnees.getPointer(16)
                    if (format == Libmpv.FORMAT_FLAG && valeur != null) {
                        drapeau = valeur.getInt(0) != 0
                    }
                }
            }
            return MpvEvenement(id, erreur, etiquette, raisonFin, erreurFin, drapeau, journal)
        }
    }
}

/**
 * Tableau de `mpv_render_param` contigu, terminé par une entrée nulle.
 *
 * La structure fait 16 octets en LP64 comme en LLP64 : un int (aligné à 8 par
 * le pointeur qui suit) puis le pointeur. **Les mémoires pointées sont
 * retenues par l'objet** : tant que lui vit, rien de ce qu'il référence ne
 * peut être ramassé pendant l'appel natif — le genre de disparition qui se
 * paie en SIGSEGV aléatoire, pas en exception.
 */
internal class ParamsRendu(private val paires: List<Pair<Int, Memory>>) {

    val pointeur: Memory = Memory(((paires.size + 1) * TAILLE).toLong()).also { zone ->
        paires.forEachIndexed { i, (type, valeur) ->
            zone.setInt((i * TAILLE).toLong(), type)
            zone.setPointer((i * TAILLE + 8).toLong(), valeur)
        }
        zone.setInt((paires.size * TAILLE).toLong(), 0)
        zone.setPointer((paires.size * TAILLE + 8).toLong(), Pointer.NULL)
    }

    companion object {
        private const val TAILLE = 16

        fun texte(valeur: String): Memory {
            val octets = valeur.toByteArray()
            return Memory((octets.size + 1).toLong()).apply {
                write(0, octets, 0, octets.size)
                setByte(octets.size.toLong(), 0)
            }
        }

        fun entiers(a: Int, b: Int): Memory = Memory(8).apply {
            setInt(0, a)
            setInt(4, b)
        }

        fun taille(valeur: Long): Memory = Memory(8).apply { setLong(0, valeur) }
    }
}
