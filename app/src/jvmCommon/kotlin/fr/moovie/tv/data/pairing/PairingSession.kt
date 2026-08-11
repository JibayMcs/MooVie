package fr.moovie.tv.data.pairing

import fr.moovie.tv.data.remote.RemoteTokenRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Propriétaire du serveur d'appairage, hors de la modale.
 *
 * ### Pourquoi il existe
 *
 * Tant qu'il ne s'agissait que de saisir des clés, le serveur pouvait vivre et
 * mourir avec la modale : on regardait le QR pendant qu'on tapait. Une
 * télécommande, elle, sert **pendant qu'on navigue** — donc modale fermée. Le
 * serveur doit lui survivre, et cette survie ne doit pas être silencieuse.
 *
 * ### Trois demandeurs, une socket
 *
 * - **La modale**, le temps qu'on la regarde ([releaseDialog]).
 * - **La télécommande armée** ([armRemote]) : le téléphone a chargé la page, il
 *   faut que le serveur survive à la fermeture de la modale.
 * - **Le téléviseur** ([retainHost]) : il écoute en permanence pour qu'un
 *   téléphone le trouve sans qu'on ait à prendre la télécommande physique.
 *
 * Le troisième est arrivé en dernier et ne se déduisait pas des deux autres :
 * fermer la modale coupait la socket **sous le téléviseur**, qui n'avait rien
 * demandé et se retrouvait muet jusqu'au prochain démarrage. C'est mesurable —
 * `/ping` répondait 204 modale ouverte, plus rien une fois refermée.
 *
 * ### Ce qui la borne
 *
 * Elle meurt avec le premier plan : `MainActivity.onPause` appelle [stop].
 * Quitter Moo-vie ne laisse donc rien à l'écoute du réseau.
 *
 * C'est aussi pourquoi [resume] existe. La composition, elle, **survit** à la
 * pause : le `remember` de la modale garde une référence sur un serveur mort et
 * ne recrée rien. Sans ce rappel, le téléviseur cessait d'écouter au premier
 * passage en arrière-plan, définitivement, et l'annonce mDNS avec lui.
 *
 * Un objet global plutôt qu'une dépendance passée de main en main : les points
 * d'appel sont une modale Compose et des rappels de cycle de vie Android, qui
 * n'ont aucune raison de se connaître.
 */
object PairingSession {

    private val _server = MutableStateFlow<PairingServer?>(null)
    val server: StateFlow<PairingServer?> = _server.asStateFlow()

    private val _remoteArmed = MutableStateFlow(false)

    /** Vrai quand le téléphone a ouvert la télécommande : le serveur survivra. */
    val remoteArmed: StateFlow<Boolean> = _remoteArmed.asStateFlow()

    /** Vrai tant que le téléviseur tient l'écoute ouverte. Faux sur un téléphone. */
    private val _hosted = MutableStateFlow(false)

    /**
     * De quoi reconstruire le serveur après une pause.
     *
     * Gardée ici et non dans la composition, parce que c'est ici qu'on sait
     * qu'il a été coupé — et que celle-là n'aura pas été démontée entre-temps.
     */
    private var factory: (() -> PairingServer)? = null

    /**
     * Rend le serveur courant, en le créant au besoin.
     *
     * Réutiliser plutôt que recréer : deux serveurs voudraient dire deux sockets
     * et deux ports, et l'annonce réseau n'en désignerait qu'un.
     *
     * Appelée depuis la composition, donc du fil principal : c'est ce qui rend
     * ce `?:` sans verrou correct. Le jeton, lui, est lu plus tard, sur la
     * coroutine du serveur — voir [PairingServer].
     */
    fun start(create: () -> PairingServer): PairingServer {
        factory = create
        return _server.value ?: create().also {
            it.start()
            _server.value = it
        }
    }

    /** Le téléviseur prend l'écoute à son compte, et la garde. */
    fun retainHost() {
        _hosted.value = true
    }

    /** Le téléviseur la rend : plus rien ne justifie l'écoute, sauf la modale. */
    fun releaseHost() {
        _hosted.value = false
        releaseDialog()
    }

    /**
     * Retour au premier plan : on relance ce que [stop] a coupé en partant.
     *
     * **Seulement pour le téléviseur.** Un téléphone qui a ouvert la modale une
     * fois ne doit pas se remettre à écouter le réseau à chaque retour dans
     * l'application : là-bas, le serveur n'a de raison d'être que sous les yeux.
     */
    fun resume() {
        if (!_hosted.value || _server.value != null) return
        factory?.let { start(it) }
    }

    /**
     * Révoque les télécommandes appairées : jeton neuf, adresse neuve.
     *
     * La contrepartie d'un jeton qui dure. Sans elle, un téléphone appairé une
     * fois garderait la main sur le téléviseur pour toujours, et le seul recours
     * serait de réinstaller l'application.
     *
     * L'ordre compte : on écrit d'abord le nouveau jeton, puis on demande au
     * serveur de le relire. L'inverse le ferait relire l'ancien. Serveur éteint,
     * la seconde étape ne fait rien — il lira le nouveau jeton en démarrant.
     */
    suspend fun renewToken() {
        RemoteTokenRepository().regenerate()
        _server.value?.refreshToken()
    }

    /** Appelé quand le téléphone charge la page de télécommande. */
    fun armRemote() {
        _remoteArmed.value = true
    }

    /** Fermeture de la modale : on coupe, sauf si quelqu'un d'autre en dépend. */
    fun releaseDialog() {
        if (!_remoteArmed.value && !_hosted.value) stop()
    }

    fun stop() {
        _server.value?.close()
        _server.value = null
        _remoteArmed.value = false
    }
}
