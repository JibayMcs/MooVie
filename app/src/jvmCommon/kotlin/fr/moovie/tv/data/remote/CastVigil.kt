package fr.moovie.tv.data.remote

/**
 * Ce qu'un relevé apprend de la box.
 *
 * Quatre cas et non deux : « elle ne répond pas » et « elle répond qu'elle ne
 * joue rien » sont des informations opposées, et [RemoteStatus] existe déjà pour
 * ne pas les confondre. Une pause, elle, se distingue d'une lecture parce
 * qu'elle n'a plus rien de neuf à raconter — c'est ce qui règle la cadence.
 */
enum class CastPulse {
    PLAYING,
    PAUSED,

    /** La box a répondu, et rien ne joue. */
    IDLE,

    /** La box n'a pas répondu. */
    SILENT,
    ;

    companion object {
        fun of(status: RemoteStatus): CastPulse = when (status) {
            RemoteStatus.Unreachable -> SILENT
            is RemoteStatus.Known -> {
                val now = status.state.now
                when {
                    now == null -> IDLE
                    now.playing -> PLAYING
                    else -> PAUSED
                }
            }
        }
    }
}

/**
 * Ce que la notification de diffusion doit montrer, au vu du dernier relevé.
 *
 * ## Le défaut que ça corrige, et qu'aucun test unitaire n'a vu
 *
 * Un relevé « rien ne joue » ne doit **pas** vider le lecteur. C'est [CastVigil]
 * qui décide de la fin d'une session, et elle tolère exprès quelques creux —
 * l'enchaînement automatique vers l'épisode suivant en est un.
 *
 * Confier ce creux au lecteur donnait tout autre chose : `SimpleBasePlayer`
 * passait en `STATE_IDLE`, `PlayerNotificationManager` en concluait qu'il n'y
 * avait plus rien à afficher et **annulait la notification**, ce qui arrêtait le
 * service. Mesuré sur l'émulateur : la vigie rendait bien `Watch(4000)` sur le
 * premier creux, et deux millisecondes plus tard la notification était annulée.
 * La politique était juste, et parfaitement ignorée.
 *
 * On garde donc le dernier média connu, en le disant **à l'arrêt**. C'est vrai —
 * rien ne joue — et ça laisse la décision à qui l'a prise.
 *
 * @param previous ce qu'on montrait jusqu'ici.
 * @param reading le relevé qui vient d'arriver, null si la box ne joue rien.
 */
fun castDisplay(previous: NowPlaying?, reading: NowPlaying?): NowPlaying? = when {
    reading != null && reading.mediaKey.isNotBlank() -> reading
    // Une clé vide est un téléviseur d'avant cette version : il joue quelque
    // chose, mais rien qu'on sache nommer ni enregistrer. Mieux vaut garder ce
    // qu'on affichait que le remplacer par un média sans identité.
    else -> previous?.copy(playing = false)
}

/** Ce que la sonde doit faire ensuite. */
sealed interface CastVerdict {
    /** Poursuivre, et redemander dans [delayMs]. */
    data class Watch(val delayMs: Long) : CastVerdict

    /** Se retirer : la session est finie, la notification doit disparaître. */
    data object Retire : CastVerdict
}

/**
 * Quand cesser de suivre la box, et à quel rythme la relever.
 *
 * ## Les deux questions, et pourquoi elles sont ici
 *
 * Elles sont le vrai travail de la notification de diffusion, et ni l'une ni
 * l'autre n'a de rapport avec Android. Les laisser dans le service les rendrait
 * intestables : il faudrait un téléviseur, un téléphone, et la patience de
 * regarder une notification pendant une minute pour savoir si elle s'en va.
 *
 * ## Quand s'arrêter
 *
 * **Un service en avant-plan qui survit à la lecture est une notification
 * fantôme.** Elle ne se balaie pas — elle est `ongoing` —, elle offre des
 * boutons qui ne commandent plus rien, et la seule façon de s'en défaire est de
 * tuer l'application. Trois fins possibles, et il faut couvrir les trois :
 *
 * 1. **La lecture se termine** — la box répond, et ne joue plus rien. On lui
 *    laisse [IDLES_BEFORE_RETIRE] relevés, de quoi traverser l'enchaînement
 *    automatique vers l'épisode suivant sans lâcher la session.
 * 2. **La box est débranchée** — plus aucun relevé n'arrive. C'est le cas que
 *    rien d'autre ne rattrape : personne n'ira fermer la notification côté
 *    téléviseur, puisqu'il est éteint. [SILENCES_BEFORE_RETIRE] silences
 *    d'affilée suffisent à conclure.
 * 3. **La diffusion n'a jamais démarré** — la box a pris la demande puis n'a
 *    rien trouvé. Le budget est alors bien plus large, voir ci-dessous.
 *
 * Un silence isolé ne prouve rien : un paquet perdu sur le Wi-Fi suffit, et
 * c'est déjà ce qui faisait clignoter le mini-lecteur avant qu'on ne compte les
 * silences ([RemoteStatus]). D'où des compteurs, et non un verdict par relevé.
 *
 * ## La patience avant la première image
 *
 * [IDLES_BEFORE_START] est volontairement énorme à côté de
 * [IDLES_BEFORE_RETIRE] — soixante secondes contre douze. Ce n'est pas un
 * réglage prudent, c'est une mesure : **une résolution à froid prend 30 s sur la
 * box**, 2,6 s une fois en cache. Pendant tout ce temps la box répond « rien ne
 * joue », puisque son lecteur n'est pas encore monté. Un budget calibré sur la
 * fin de lecture couperait donc la session au milieu de presque chaque premier
 * lancement, et la notification disparaîtrait précisément quand elle est la
 * seule chose à regarder.
 *
 * ## La cadence
 *
 * Une seconde convient tant qu'on regarde l'écran de télécommande — et c'est lui
 * qui la tient, pour sa barre de progression. En fond, personne ne lit la
 * position : réveiller la radio chaque seconde ne ferait que coûter de la
 * batterie. [PLAYING_POLL_MS] reste deux fois plus rapide que le pas d'écriture
 * de la reprise, ce qui suffit à ne rien perdre.
 *
 * Une box en pause n'a plus rien de neuf à dire : [PAUSED_POLL_MS] est trois
 * fois plus lent. Un silence, à l'inverse, se réessaie **vite** : toute la
 * question est de distinguer un paquet perdu d'une box débranchée, et y répondre
 * tôt est ce qui borne la durée de la notification fantôme.
 *
 * Immuable : [observe] rend le nouvel état avec son verdict, ce qui se teste
 * sans horloge, sans réseau et sans attendre.
 */
data class CastVigil(
    /** Relevés muets consécutifs. */
    val silences: Int = 0,
    /** Relevés « rien ne joue » consécutifs. */
    val idles: Int = 0,
    /** La box a-t-elle joué au moins une fois depuis le début de la session ? */
    val started: Boolean = false,
) {

    fun observe(pulse: CastPulse): Pair<CastVigil, CastVerdict> = when (pulse) {
        // Une lecture ou une pause remettent tout à zéro : la box est vivante et
        // le titre est monté. Une pause compte comme un démarrage — c'est bien
        // que la résolution a abouti.
        CastPulse.PLAYING ->
            CastVigil(started = true) to CastVerdict.Watch(PLAYING_POLL_MS)

        CastPulse.PAUSED ->
            CastVigil(started = true) to CastVerdict.Watch(PAUSED_POLL_MS)

        CastPulse.IDLE -> {
            val next = copy(silences = 0, idles = idles + 1)
            val budget = if (started) IDLES_BEFORE_RETIRE else IDLES_BEFORE_START
            next to if (next.idles >= budget) CastVerdict.Retire
            else CastVerdict.Watch(IDLE_POLL_MS)
        }

        // Un silence ne dit rien de ce que la box joue : il ne touche donc pas
        // au compteur d'inactivité. Les confondre ferait retirer la session sur
        // une coupure Wi-Fi au milieu d'un film.
        CastPulse.SILENT -> {
            val next = copy(silences = silences + 1)
            next to if (next.silences >= SILENCES_BEFORE_RETIRE) CastVerdict.Retire
            else CastVerdict.Watch(SILENT_POLL_MS)
        }
    }

    companion object {
        /** Deux fois plus rapide que le pas d'écriture de la reprise (10 s). */
        const val PLAYING_POLL_MS = 5_000L

        /** Une box en pause n'a rien de neuf à raconter. */
        const val PAUSED_POLL_MS = 15_000L

        const val IDLE_POLL_MS = 4_000L

        /** Vite : c'est ce qui borne la durée d'une notification fantôme. */
        const val SILENT_POLL_MS = 3_000L

        /** ~12 s de silence. Une box débranchée, pas un paquet perdu. */
        const val SILENCES_BEFORE_RETIRE = 4

        /** ~12 s sans lecture. De quoi traverser un enchaînement d'épisode. */
        const val IDLES_BEFORE_RETIRE = 3

        /** ~60 s. Une résolution à froid en prend 30 sur la box — mesuré. */
        const val IDLES_BEFORE_START = 15
    }
}
