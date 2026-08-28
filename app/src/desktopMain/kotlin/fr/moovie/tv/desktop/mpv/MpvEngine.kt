package fr.moovie.tv.desktop.mpv

import com.sun.jna.Memory
import com.sun.jna.Pointer
import fr.moovie.tv.core.subtitles.model.SubtitleStyle
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Une image prête à dessiner, en BGRA — l'ordre d'octets qu'attend Skia.
 *
 * **[pixels] appartient au fil de rendu et sera réécrit à la trame suivante** :
 * le consommateur doit en avoir fini avant de rendre la main — ce que fait la
 * surface, qui recopie dans un bitmap Skia de façon synchrone. Allouer un
 * tableau neuf par trame serait plus confortable et a été essayé : 8 Mo par
 * image en 1080p, à 25 images par seconde, c'est 200 Mo/s d'objets que G1
 * classe « humongous » et fragmente — l'application mourait d'un
 * OutOfMemoryError après une quarantaine de secondes de lecture, mesuré.
 */
internal class TrameVideo(
    val pixels: ByteArray,
    val largeur: Int,
    val hauteur: Int,
)

/** Une piste sélectionnable du média, telle que l'interface la présentera. */
internal class PisteMpv(val id: Long, val libelle: String, val active: Boolean)

/**
 * Le lecteur desktop : libmpv derrière une façade Kotlin.
 *
 * ## Ce que cette classe ne fait pas, et pourquoi c'est le but
 *
 * Pas de démultiplexeur, pas d'horloge, pas de compensation de seek, pas de
 * graphe audio : mpv fait tout cela, et le fait depuis quinze ans. Les deux
 * lecteurs précédents ont montré le prix de chaque alternative — libVLC et ses
 * plugins qui manquent, et un moteur FFmpeg maison où chaque comportement de
 * lecteur restait à écrire. Ici la classe se borne à traduire : commandes vers
 * mpv, événements et trames vers l'application.
 *
 * ## Fils
 *
 *  - **événements** — seul fil dans `mpv_wait_event` ; il relâche l'ouverture,
 *    annonce fin et erreurs, relaie le journal ;
 *  - **rendu** — réveillé par mpv, il dessine la trame dans notre tampon et la
 *    publie. Le rappel de mpv ne fait *que* réveiller : rappeler mpv depuis un
 *    de ses rappels est interdit par l'API.
 *
 * Tout le reste — position, pause, seek, pistes — se lit et s'écrit depuis
 * n'importe quel fil : l'API mpv est une file de commandes, thread-safe.
 */
@Suppress("TooManyFunctions")
internal class MpvEngine(
    private val largeurMax: Int = LARGEUR_MAX,
    private val hauteurMax: Int = HAUTEUR_MAX,
    private val surImage: (TrameVideo) -> Unit,
    private val surFin: () -> Unit = {},
    private val surErreur: (String) -> Unit = {},
) {

    private val mpv: Libmpv? = Libmpv.instance

    /**
     * `@Volatile` n'est pas décoratif : l'ouverture et la fermeture arrivent
     * par des fils différents — l'écran ouvre hors interface, la sortie ferme
     * sur son propre fil. Sans barrière mémoire, le fil de fermeture peut lire
     * un `ctx` encore nul et **ressortir sans rien détruire** : c'est le bug de
     * la lecture qui continuait en arrière-plan après avoir quitté le lecteur —
     * invisible des sondes, qui ouvrent et ferment sur le même fil.
     */
    @Volatile
    private var ctx: Pointer? = null

    @Volatile
    private var rendu: Pointer? = null

    /** Référence forte sur le rappel JNA : ramassé, il devient un saut dans le vide. */
    @Volatile
    private var reveil: Libmpv.RenduReveil? = null

    private val fils = java.util.concurrent.CopyOnWriteArrayList<Thread>()
    private val trameDispo = Semaphore(0)

    @Volatile
    private var vivant = false

    @Volatile
    private var charge = false

    @Volatile
    private var termine = false

    /** Relâché par FILE_LOADED ou par une fin d'ouverture en erreur. */
    @Volatile
    private var ouverture: CountDownLatch? = null

    @Volatile
    private var ouvertureReussie = false

    /**
     * Cible d'un seek pas encore abouti, et l'instant de la demande.
     *
     * `time-pos` ne saute qu'une fois le seek exécuté ; entre les deux, la
     * position rendue est celle où l'utilisateur a cliqué — sans quoi deux
     * appuis rapides sur « avancer » repartent de l'ancienne position et se
     * mangent. Relâchée par PLAYBACK_RESTART, ou par expiration si le flux ne
     * répond plus : une cible qui fait autorité pour toujours serait une barre
     * de progression figée sur un clic.
     */
    @Volatile
    private var seekCible = -1L

    @Volatile
    private var seekDemandeA = 0L


    // ── Cycle de vie ──────────────────────────────────────────────────────

    /**
     * Ouvre [url] et démarre la lecture. Bloque jusqu'à ce que le média soit
     * chargé ou refusé — l'appelant décide du fil, les écrans appellent déjà
     * hors interface.
     *
     * Les [entetes] descendent jusqu'aux requêtes de segment : mpv les passe au
     * démultiplexeur FFmpeg, qui les répercute — l'invariant que libVLC n'a
     * jamais tenu et qui a fait naître le relais local. `MpvHeadersTest` le
     * verrouille.
     *
     * [urlAudio] ajoute le son comme **piste externe** quand image et son
     * arrivent par deux URL — la seule forme sous laquelle YouTube sert encore
     * ses bandes-annonces. C'est mpv qui tient les deux ensemble, comme il
     * tiendrait deux pistes d'un même conteneur ; le manifeste DASH fabriqué
     * pour les lecteurs à entrée unique n'a pas à exister ici.
     */
    fun ouvre(
        url: String,
        entetes: Map<String, String> = emptyMap(),
        departMs: Long = 0L,
        urlAudio: String? = null,
    ): Boolean {
        val lib = mpv ?: run {
            surErreur("libmpv introuvable : le lecteur ne peut pas démarrer")
            return false
        }
        if (ctx == null && !demarre(lib)) return false
        val contexte = ctx ?: return false

        entetes.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.let { lib.mpv_set_property_string(contexte, "user-agent", it.value) }
        entetes.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }
            ?.let { lib.mpv_set_property_string(contexte, "referrer", it.value) }
        val autres = entetes.filterKeys {
            !it.equals("User-Agent", ignoreCase = true) && !it.equals("Referer", ignoreCase = true)
        }
        // `change-list … append` prend chaque en-tête **verbatim** : pas de
        // séparateur à échapper. Poser la liste d'un bloc demanderait de citer
        // les virgules — mesuré : l'élément préfixé `%n%` n'atteint jamais la
        // requête, l'option ayant rejeté la liste sans le dire.
        lib.mpv_command(contexte, arrayOf("change-list", "http-header-fields", "clr", "", null))
        autres.forEach { (nom, valeur) ->
            lib.mpv_command(
                contexte,
                arrayOf("change-list", "http-header-fields", "append", "$nom: $valeur", null),
            )
        }

        lib.mpv_set_property_string(contexte, "start", if (departMs > 0) "+${departMs / 1000.0}" else "none")

        // La piste audio externe se déclare avant l'ouverture, en liste — par
        // `append`, qui prend l'URL verbatim : celles de googlevideo portent
        // des virgules, le séparateur des listes mpv.
        lib.mpv_command(contexte, arrayOf("change-list", "audio-files", "clr", "", null))
        urlAudio?.let {
            lib.mpv_command(contexte, arrayOf("change-list", "audio-files", "append", it, null))
        }

        termine = false
        charge = false
        ouvertureReussie = false
        val attente = CountDownLatch(1)
        ouverture = attente

        val code = lib.mpv_command(contexte, arrayOf("loadfile", url, "replace", null))
        if (code < 0) {
            surErreur("ouverture refusée : ${lib.mpv_error_string(code)}")
            return false
        }
        // Réseau : DNS + connexion + entêtes de conteneur. Au-delà, le flux ne
        // répondra pas davantage — on rend un échec franc, pas une attente.
        if (!attente.await(OUVERTURE_MAX_S, TimeUnit.SECONDS)) {
            surErreur("ouverture sans réponse après ${OUVERTURE_MAX_S}s : $url")
            return false
        }
        // Fermé pendant l'attente : le latch a été relâché par la fermeture,
        // pas par un média — l'ouverture a perdu la course, et c'est un échec.
        if (!vivant) return false
        return ouvertureReussie
    }

    /**
     * Monte l'instance mpv : options, initialisation, rendu, fils. Une fois.
     *
     * Sous le même verrou que [ferme] : le montage et le démontage viennent de
     * fils différents, et un démontage qui croise un montage à moitié fait
     * laisserait l'un des deux avec des pointeurs morts.
     */
    @Synchronized
    private fun demarre(lib: Libmpv): Boolean {
        if (ctx != null) return true
        val contexte = lib.mpv_create() ?: run {
            surErreur("mpv_create a rendu null")
            return false
        }

        // Un lecteur embarqué, pas le mpv de bureau : rien ne doit venir de la
        // machine de l'utilisateur (config, scripts, raccourcis), rien ne doit
        // partir vers un binaire externe (ytdl). La fenêtre est la nôtre.
        lib.mpv_set_option_string(contexte, "config", "no")
        lib.mpv_set_option_string(contexte, "load-scripts", "no")
        lib.mpv_set_option_string(contexte, "input-default-bindings", "no")
        lib.mpv_set_option_string(contexte, "osc", "no")
        lib.mpv_set_option_string(contexte, "ytdl", "no")
        lib.mpv_set_option_string(contexte, "terminal", "no")
        lib.mpv_set_option_string(contexte, "audio-display", "no")
        lib.mpv_set_option_string(contexte, "vo", "libmpv")
        lib.mpv_set_option_string(contexte, "idle", "yes")
        // ── Décodage matériel, avec recopie en mémoire centrale ──────────────
        //
        // Le rendu est logiciel : il nous faut les trames en RAM, donc les
        // surfaces GPU ne servent à rien telles quelles. D'où `-copy`, qui
        // décode sur le GPU puis rapatrie — et **non** `auto`, dont les trames
        // resteraient inatteignables pour notre tampon.
        //
        // Le réglage précédent était `no`, au motif que la recopie coûterait
        // plus cher que décoder en logiciel. C'était vrai pour du 1080p H.264 ;
        // ça ne l'est pas pour ce que les catalogues servent aujourd'hui.
        // Mesuré sur un HEVC 10 bits 3840×1920, 240 trames, recopie comprise :
        //
        // | décodage        | durée  | CPU    |
        // |-----------------|--------|--------|
        // | `no`            | 1,85 s | 951 %  |
        // | `auto-copy`     | 1,78 s | 246 %  |
        //
        // Quatre fois moins de processeur à vitesse égale. Sur une machine sans
        // décodeur HEVC — un Xeon de 2012 avec sa Quadro 2000, mesuré à une
        // dizaine d'images par seconde — ça ne change rien : mpv retombe seul
        // sur le logiciel, et c'est la raison de choisir `auto-copy` plutôt
        // qu'un décodeur nommé, qui échouerait au lieu de se replier.
        lib.mpv_set_option_string(contexte, "hwdec", "auto-copy")
        // La veille système est gérée par l'application (KeepAwake), pas par mpv.
        lib.mpv_set_option_string(contexte, "stop-screensaver", "no")
        // Amplification jusqu'à 200 % : le curseur du lecteur va au-delà de
        // 100 pour les films dont la piste est trop basse — le comportement
        // qu'offrait libVLC, que l'interface expose déjà.
        lib.mpv_set_option_string(contexte, "volume-max", "200")
        // La fin de lecture ne décharge pas le média : sans `keep-open`, mpv
        // repasse en veille et position comme durée retombent à zéro — un seek
        // au-delà de la fin affichait « 0:00 / 0:00 » sur tout l'écran. La fin
        // se lit alors sur `eof-reached`, observé ci-dessous : END_FILE ne
        // vient plus.
        lib.mpv_set_option_string(contexte, "keep-open", "yes")
        // Aucun sous-titre au démarrage : mpv en choisirait un tout seul dès
        // qu'une piste existe. Les afficher reste un choix explicite ; les
        // pistes, elles, restent proposées dans le menu.
        lib.mpv_set_option_string(contexte, "sid", "no")

        val code = lib.mpv_initialize(contexte)
        if (code < 0) {
            surErreur("mpv_initialize : ${lib.mpv_error_string(code)}")
            lib.mpv_terminate_destroy(contexte)
            return false
        }
        lib.mpv_request_log_messages(contexte, "warn")
        lib.mpv_observe_property(contexte, ETIQUETTE_EOF, "eof-reached", Libmpv.FORMAT_FLAG)

        val creation = ParamsRendu(
            listOf(Libmpv.RENDER_PARAM_API_TYPE to ParamsRendu.texte(Libmpv.RENDER_API_SW)),
        )
        val reference = PointerByReference()
        val codeRendu = lib.mpv_render_context_create(reference, contexte, creation.pointeur)
        if (codeRendu < 0) {
            surErreur("rendu logiciel refusé : ${lib.mpv_error_string(codeRendu)}")
            lib.mpv_terminate_destroy(contexte)
            return false
        }
        rendu = reference.value

        val rappel = object : Libmpv.RenduReveil {
            override fun invoke(ctx: Pointer?) {
                trameDispo.release()
            }
        }
        reveil = rappel
        lib.mpv_render_context_set_update_callback(reference.value, rappel, null)

        ctx = contexte
        vivant = true
        demarreFil("moovie-mpv-evenements") { boucleEvenements(lib, contexte) }
        demarreFil("moovie-mpv-rendu") { boucleRendu(lib, reference.value) }
        return true
    }

    @Synchronized
    fun ferme() {
        val lib = mpv ?: return
        val contexte = ctx ?: return
        vivant = false
        ctx = null
        // Une ouverture en vol attend son média : la relâcher, en échec — la
        // fermeture a gagné, et personne ne doit rester bloqué trente secondes
        // sur un lecteur déjà mort.
        ouverture?.countDown()
        // Réveille les deux fils, puis attend qu'ils soient sortis : libérer le
        // contexte de rendu pendant qu'un rendu est en vol est un SIGSEGV, pas
        // une erreur rattrapable.
        lib.mpv_wakeup(contexte)
        trameDispo.release()
        fils.forEach { runCatching { it.join(FIN_FIL_MS) } }
        fils.clear()
        rendu?.let {
            lib.mpv_render_context_set_update_callback(it, null, null)
            lib.mpv_render_context_free(it)
        }
        rendu = null
        reveil = null
        lib.mpv_terminate_destroy(contexte)
    }

    private fun demarreFil(nom: String, corps: () -> Unit) {
        val fil = Thread({ runCatching(corps).onFailure { surErreur(it.message ?: nom) } }, nom)
        fil.isDaemon = true
        fils += fil
        fil.start()
    }

    // ── Fils ──────────────────────────────────────────────────────────────

    private fun boucleEvenements(lib: Libmpv, contexte: Pointer) {
        while (vivant) {
            val evenement = MpvEvenement.depuis(lib.mpv_wait_event(contexte, ATTENTE_EVENEMENT_S))
            when (evenement.id) {
                Libmpv.EVENT_SHUTDOWN -> return
                Libmpv.EVENT_FILE_LOADED -> {
                    charge = true
                    ouvertureReussie = true
                    // **Ce que le décodage a réellement obtenu.** `auto-copy` est
                    // une demande, pas une garantie : sans décodeur pour ce
                    // format, mpv retombe en logiciel sans rien dire. Sur une
                    // machine trop lente, la question « est-ce que le matériel a
                    // pris ? » est la première à se poser, et jusqu'ici rien ne
                    // permettait d'y répondre — un Xeon de 2012 rendait dix
                    // images par seconde et il fallait le déduire.
                    println(
                        "[lecteur] décodage=${lisTexte("hwdec-current") ?: "?"}" +
                            " format=${lisTexte("video-format") ?: "?"}" +
                            " ${lisTexte("width") ?: "?"}×${lisTexte("height") ?: "?"}" +
                            " gamma=${lisTexte("video-params/gamma") ?: "?"}",
                    )
                    ouverture?.countDown()
                }
                Libmpv.EVENT_PLAYBACK_RESTART -> seekCible = -1L
                // La fin, version `keep-open` : le média reste chargé — durée
                // et position restent lisibles — et c'est ce drapeau qui dit
                // que la lecture a épuisé le flux. Sur la transition seulement :
                // il redevient faux à chaque seek en arrière ou nouveau média.
                Libmpv.EVENT_PROPERTY_CHANGE -> if (evenement.etiquette == ETIQUETTE_EOF) {
                    val atteint = evenement.drapeau == true
                    if (atteint && !termine && charge) {
                        termine = true
                        surFin()
                    }
                    if (!atteint) termine = false
                }
                Libmpv.EVENT_END_FILE -> when {
                    // Échec pendant l'ouverture : relâcher l'appelant, en échec.
                    // Sur l'**erreur** seulement : un `loadfile replace` émet
                    // d'abord la fin (STOP) du média précédent, et la prendre
                    // pour un refus ferait échouer toute réouverture.
                    evenement.raisonFin == Libmpv.END_FILE_ERROR && ouverture?.count == 1L -> {
                        surErreur("ouverture impossible : ${lib.mpv_error_string(evenement.erreurFin)}")
                        ouverture?.countDown()
                    }
                    // Une fin sur erreur n'est **pas** une fin : elle passe par
                    // surErreur et jamais par surFin. Prendre une panne pour un
                    // épisode terminé, c'est enchaîner le suivant sur un flux
                    // cassé — le défaut historique que ce contrat interdit.
                    evenement.raisonFin == Libmpv.END_FILE_ERROR -> {
                        surErreur("lecture interrompue : ${lib.mpv_error_string(evenement.erreurFin)}")
                        termine = true
                    }
                    evenement.raisonFin == Libmpv.END_FILE_EOF -> {
                        termine = true
                        surFin()
                    }
                    // STOP, QUIT, REDIRECT : des transitions, pas des fins.
                    else -> Unit
                }
                Libmpv.EVENT_LOG_MESSAGE -> evenement.journal?.let { println("[mpv] $it") }
            }
        }
    }

    /**
     * Dessine quand mpv le demande.
     *
     * La taille vient du flux (`dwidth`/`dheight`, l'aspect déjà appliqué),
     * plafonnée : décoder du 4K pour une fenêtre coûte tout et n'apporte rien.
     * Chaque trame publiée a son propre tableau — l'invariant qui a coûté des
     * SIGSEGV en plein visionnage du temps du tampon partagé avec libVLC.
     */
    private fun boucleRendu(lib: Libmpv, contexte: Pointer) {
        var tampon: Memory? = null
        var pixels = ByteArray(0)
        var largeur = 0
        var hauteur = 0
        while (vivant) {
            if (!trameDispo.tryAcquire(ATTENTE_TRAME_MS, TimeUnit.MILLISECONDS)) continue
            if (!vivant) return
            val drapeaux = lib.mpv_render_context_update(contexte)
            if (drapeaux and Libmpv.MPV_RENDER_UPDATE_FRAME == 0L) continue

            val l = lisEntier("dwidth")?.toInt() ?: 0
            val h = lisEntier("dheight")?.toInt() ?: 0
            if (l <= 0 || h <= 0) continue
            val facteur = minOf(1.0, largeurMax.toDouble() / l, hauteurMax.toDouble() / h)
            // Dimensions paires : certains convertisseurs de chroma l'exigent.
            val cibleL = ((l * facteur).toInt() / 2) * 2
            val cibleH = ((h * facteur).toInt() / 2) * 2
            if (cibleL != largeur || cibleH != hauteur || tampon == null) {
                largeur = cibleL
                hauteur = cibleH
                tampon = Memory(largeur.toLong() * hauteur * OCTETS_PIXEL)
                // Réutilisé d'une trame à l'autre — voir le contrat de
                // [TrameVideo] : l'allocation par trame tuait le tas.
                pixels = ByteArray(largeur * hauteur * OCTETS_PIXEL)
            }

            val params = ParamsRendu(
                listOf(
                    Libmpv.RENDER_PARAM_SW_SIZE to ParamsRendu.entiers(largeur, hauteur),
                    Libmpv.RENDER_PARAM_SW_FORMAT to ParamsRendu.texte(FORMAT_PIXELS),
                    Libmpv.RENDER_PARAM_SW_STRIDE to ParamsRendu.taille(largeur.toLong() * OCTETS_PIXEL),
                    Libmpv.RENDER_PARAM_SW_POINTER to tampon,
                ),
            )
            if (lib.mpv_render_context_render(contexte, params.pointeur) < 0) continue

            tampon.read(0, pixels, 0, pixels.size)
            // Le « 0 » de `bgr0` est un octet de bourrage que mpv laisse à
            // zéro. Skia le lit comme un alpha : la vidéo devenait un voile
            // au travers duquel on voyait le fond de la fiche. libVLC, lui,
            // écrivait 255 — même chemin Skia, deux résultats.
            var i = OCTET_ALPHA
            while (i < pixels.size) {
                pixels[i] = OPAQUE
                i += OCTETS_PIXEL
            }
            surImage(TrameVideo(pixels, largeur, hauteur))
        }
    }

    // ── Commandes ─────────────────────────────────────────────────────────

    val enLecture: Boolean get() = vivant && charge && !termine && !(lisDrapeau("pause") ?: false)

    fun bascule() = pause(!(lisDrapeau("pause") ?: false))

    fun pause(valeur: Boolean) {
        poseDrapeau("pause", valeur)
    }

    fun seek(positionMs: Long) {
        val duree = dureeMs()
        val cible = positionMs.coerceAtLeast(0).let { if (duree > 0) it.coerceAtMost(duree) else it }
        seekCible = cible
        seekDemandeA = System.currentTimeMillis()
        termine = false
        // `exact` : atterrir sur la trame demandée, pas sur l'image-clé d'avant.
        // C'est ce que libVLC ne savait pas faire et qui a valu au contrôleur
        // vlcj sa compensation apprise — sept à dix secondes d'écart en HLS.
        commande("seek", (cible / 1000.0).toString(), "absolute+exact")
    }

    fun positionMs(): Long {
        val cible = seekCible
        if (cible >= 0) {
            // L'expiration protège d'un flux qui ne confirmera jamais le saut.
            if (System.currentTimeMillis() - seekDemandeA < SEEK_EN_VOL_MS) return cible
            seekCible = -1L
        }
        return ((lisDouble("time-pos") ?: 0.0) * 1000).toLong().coerceAtLeast(0)
    }

    fun dureeMs(): Long = ((lisDouble("duration") ?: 0.0) * 1000).toLong().coerceAtLeast(0)

    /**
     * Fin de la portion en mémoire, en position absolue de média — le contrat
     * de la barre de progression. libVLC 3 n'avait rien à offrir ici ; mpv
     * publie la fin de son cache de démultiplexage.
     */
    fun tamponMs(): Long =
        ((lisDouble("demuxer-cache-time") ?: 0.0) * 1000).toLong().coerceAtLeast(0)

    val vitesseCourante: Float get() = (lisDouble("speed") ?: 1.0).toFloat()

    fun regleVitesse(valeur: Float) {
        poseDouble("speed", valeur.toDouble())
    }

    /** [valeur] entre 0 et 1, l'échelle de l'application ; mpv parle en 0–100. */
    fun regleVolume(valeur: Float) {
        poseDouble("volume", (valeur.coerceIn(0f, 1f) * VOLUME_MAX).toDouble())
    }

    /** [valeur] entre 0 et 200 : au-delà de 100, mpv amplifie. */
    fun regleVolumePourcent(valeur: Int) {
        poseDouble("volume", valeur.coerceIn(0, AMPLIFICATION_MAX).toDouble())
    }

    fun coupeSon(valeur: Boolean) {
        poseDrapeau("mute", valeur)
    }

    /**
     * Remplissage du cache pendant une mise en mémoire tampon, 100 hors tampon.
     *
     * `paused-for-cache` est un **signal**, pas une heuristique : là où libVLC
     * obligeait à déduire l'état du cache d'une horloge qui avance, mpv dit
     * quand la lecture attend le réseau, et où en est le remplissage.
     */
    fun remplissageCache(): Float {
        if (lisDrapeau("paused-for-cache") != true) return REMPLI
        return (lisEntier("cache-buffering-state") ?: 0L).toFloat()
    }

    /**
     * Ajoute des sous-titres externes sans en activer aucun (`auto`) : ils
     * apparaissent dans le menu, l'affichage reste un choix explicite.
     */
    fun ajouteSousTitres(urls: Collection<String>) {
        urls.filter { it.isNotBlank() }.forEach { commande("sub-add", it, "auto") }
    }

    /**
     * Choisit la variante vidéo par sa hauteur, ou rend la main à mpv (null).
     *
     * Sur un master HLS, chaque variante est une piste vidéo sélectionnable **à
     * chaud** — là où libVLC imposait une réouverture complète du flux, avec sa
     * coupure et son recalage de position.
     */
    fun selectionneVideoParHauteur(hauteur: Int?) {
        if (hauteur == null) {
            poseTexte("vid", "auto")
            return
        }
        val piste = pistesVideo().minByOrNull { kotlin.math.abs(it.second - hauteur) } ?: return
        poseTexte("vid", piste.first.toString())
    }

    /** Paires (id, hauteur) des pistes vidéo annoncées par le démultiplexeur. */
    private fun pistesVideo(): List<Pair<Long, Int>> {
        val nombre = lisEntier("track-list/count") ?: return emptyList()
        return (0 until nombre).mapNotNull { i ->
            if (lisTexte("track-list/$i/type") != "video") return@mapNotNull null
            val id = lisEntier("track-list/$i/id") ?: return@mapNotNull null
            val hauteur = lisEntier("track-list/$i/demux-h")?.toInt() ?: return@mapNotNull null
            id to hauteur
        }
    }

    fun fps(): Double = lisDouble("container-fps") ?: 0.0

    // ── Pistes ────────────────────────────────────────────────────────────

    /**
     * Pistes du type demandé (`audio` ou `sub`), lues de `track-list`.
     *
     * Champ par champ et non par nœud : l'API nœud de mpv demande une gestion
     * mémoire à part entière pour un besoin qui se résume à quatre champs.
     */
    fun pistes(type: String): List<PisteMpv> {
        val nombre = lisEntier("track-list/count") ?: return emptyList()
        return (0 until nombre).mapNotNull { i ->
            if (lisTexte("track-list/$i/type") != type) return@mapNotNull null
            val id = lisEntier("track-list/$i/id") ?: return@mapNotNull null
            val langue = lisTexte("track-list/$i/lang")
            val titre = lisTexte("track-list/$i/title")
            val libelle = listOfNotNull(langue?.uppercase(), titre)
                .joinToString(" — ")
                .ifBlank { "#$id" }
            PisteMpv(id, libelle, lisTexte("track-list/$i/selected") == "yes")
        }
    }

    fun selectionneAudio(id: Long) {
        poseTexte("aid", id.toString())
    }

    /** [id] à null désactive les sous-titres. */
    fun selectionneSousTitre(id: Long?) {
        poseTexte("sid", id?.toString() ?: "no")
    }

    /**
     * Charge un sous-titre externe, ou retire le précédent si [chemin] est nul.
     *
     * `sub-remove` sans identifiant retire la piste externe courante : un seul
     * fichier externe vit à la fois, c'est le contrat de l'écran (le fichier
     * recalé remplace le précédent).
     */
    /** Vrai quand un sous-titre externe est monté — il n'y en a qu'un à la fois. */
    @Volatile
    private var sousTitreMonte = false

    /**
     * Applique l'apparence choisie par l'utilisateur.
     *
     * Réappliquée sans compter : mpv accepte ces propriétés à tout moment, y
     * compris avant qu'un sous-titre soit monté, et le rendu suit à la trame
     * suivante. C'est ce qui permet à l'écran de simplement republier le style
     * quand il change, sans savoir où en est la lecture.
     */
    fun styleSousTitres(style: SubtitleStyle) {
        mpvSubtitleProperties(style).forEach { (nom, valeur) -> poseTexte(nom, valeur) }
    }

    fun sousTitreExterne(chemin: String?) {
        // Retirer ce qui n'existe pas n'est pas un ordre, c'est du bruit :
        // l'écran repasse par ici avec `null` au montage, et `sub-remove` sans
        // piste externe répond « error running command » dans le journal.
        if (chemin == null && !sousTitreMonte) return
        if (sousTitreMonte) commande("sub-remove")
        sousTitreMonte = false
        if (chemin != null) {
            commande("sub-add", chemin, "select")
            sousTitreMonte = true
        }
    }

    // ── Lecture/écriture de propriétés ────────────────────────────────────

    private fun commande(vararg args: String) {
        val lib = mpv ?: return
        val contexte = ctx ?: return
        val code = lib.mpv_command(contexte, arrayOf(*args, null))
        // Diagnostic, pas une panne : un seek refusé sur un flux non seekable
        // ne doit pas déclencher la cascade de sources. surErreur est réservé
        // à ce qui compromet la lecture elle-même.
        if (code < 0) println("[mpv] commande ${args.first()} : ${lib.mpv_error_string(code)}")
    }

    private fun lisDouble(nom: String): Double? {
        val lib = mpv ?: return null
        val contexte = ctx ?: return null
        val valeur = Memory(8)
        if (lib.mpv_get_property(contexte, nom, Libmpv.FORMAT_DOUBLE, valeur) < 0) return null
        return valeur.getDouble(0)
    }

    private fun lisEntier(nom: String): Long? {
        val lib = mpv ?: return null
        val contexte = ctx ?: return null
        val valeur = Memory(8)
        if (lib.mpv_get_property(contexte, nom, Libmpv.FORMAT_INT64, valeur) < 0) return null
        return valeur.getLong(0)
    }

    private fun lisDrapeau(nom: String): Boolean? {
        val lib = mpv ?: return null
        val contexte = ctx ?: return null
        val valeur = Memory(4)
        if (lib.mpv_get_property(contexte, nom, Libmpv.FORMAT_FLAG, valeur) < 0) return null
        return valeur.getInt(0) != 0
    }

    private fun lisTexte(nom: String): String? {
        val lib = mpv ?: return null
        val contexte = ctx ?: return null
        val brut = lib.mpv_get_property_string(contexte, nom) ?: return null
        val valeur = brut.getString(0)
        lib.mpv_free(brut)
        return valeur
    }

    private fun poseDouble(nom: String, valeur: Double) {
        val lib = mpv ?: return
        val contexte = ctx ?: return
        val donnee = Memory(8).apply { setDouble(0, valeur) }
        lib.mpv_set_property(contexte, nom, Libmpv.FORMAT_DOUBLE, donnee)
    }

    private fun poseDrapeau(nom: String, valeur: Boolean) {
        val lib = mpv ?: return
        val contexte = ctx ?: return
        val donnee = Memory(4).apply { setInt(0, if (valeur) 1 else 0) }
        lib.mpv_set_property(contexte, nom, Libmpv.FORMAT_FLAG, donnee)
    }

    private fun poseTexte(nom: String, valeur: String) {
        val lib = mpv ?: return
        val contexte = ctx ?: return
        lib.mpv_set_property_string(contexte, nom, valeur)
    }

    internal companion object {
        /** Plafond de l'image rendue — au-delà, on paie sans rien voir. */
        const val LARGEUR_MAX = 1920
        const val HAUTEUR_MAX = 1080

        const val OCTETS_PIXEL = 4

        /** Position de l'octet de bourrage/alpha dans un pixel `bgr0`. */
        const val OCTET_ALPHA = 3
        const val OPAQUE = 0xFF.toByte()

        /** BGRA petit-boutiste, l'ordre en mémoire que Skia lit tel quel. */
        const val FORMAT_PIXELS = "bgr0"

        const val OUVERTURE_MAX_S = 30L
        const val ATTENTE_EVENEMENT_S = 0.25
        const val ATTENTE_TRAME_MS = 250L
        const val FIN_FIL_MS = 1_500L
        const val SEEK_EN_VOL_MS = 1_500L

        /** Étiquette de l'observation `eof-reached`. */
        const val ETIQUETTE_EOF = 1L
        const val VOLUME_MAX = 100
        const val AMPLIFICATION_MAX = 200
        const val REMPLI = 100f
    }
}
