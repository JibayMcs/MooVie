package fr.moovie.tv.data.pairing

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
 * ### Ce qui la borne
 *
 * - Elle ne dure que si la télécommande a été **armée** ([armRemote]), c'est-à-dire
 *   si le téléphone a ouvert la page de télécommande. Fermer la modale sans être
 *   passé par là coupe le serveur, exactement comme avant.
 * - Elle meurt avec le premier plan : `MainActivity.onPause` appelle [stop].
 *   Quitter Moo-vie ne laisse donc rien à l'écoute du réseau.
 *
 * Un objet global plutôt qu'une dépendance passée de main en main : les deux
 * points d'appel sont une modale Compose et un rappel de cycle de vie Android,
 * qui n'ont aucune raison de se connaître.
 */
object PairingSession {

    private val _server = MutableStateFlow<PairingServer?>(null)
    val server: StateFlow<PairingServer?> = _server.asStateFlow()

    private val _remoteArmed = MutableStateFlow(false)

    /** Vrai quand le téléphone a ouvert la télécommande : le serveur survivra. */
    val remoteArmed: StateFlow<Boolean> = _remoteArmed.asStateFlow()

    /**
     * Rend le serveur courant, en le créant au besoin.
     *
     * Réutiliser plutôt que recréer : rouvrir la modale pendant que la
     * télécommande est armée changerait sinon le jeton, et la page ouverte sur le
     * téléphone tomberait en 404 au premier appui.
     */
    fun start(create: () -> PairingServer): PairingServer =
        _server.value ?: create().also {
            it.start()
            _server.value = it
        }

    /** Appelé quand le téléphone charge la page de télécommande. */
    fun armRemote() {
        _remoteArmed.value = true
    }

    /** Fermeture de la modale : on coupe, sauf si la télécommande est armée. */
    fun releaseDialog() {
        if (!_remoteArmed.value) stop()
    }

    fun stop() {
        _server.value?.close()
        _server.value = null
        _remoteArmed.value = false
    }
}
