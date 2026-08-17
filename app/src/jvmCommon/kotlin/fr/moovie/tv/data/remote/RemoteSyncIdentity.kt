package fr.moovie.tv.data.remote

/**
 * L'empreinte de synchro de **cet appareil**, telle que le serveur d'appairage
 * peut la lire.
 *
 * Même motif que [RemoteNowPlaying] et [RemoteTyping], et pour la même raison :
 * le serveur répond sur un fil de socket, et interroger DataStore depuis ce
 * fil-là le bloquerait le temps d'une lecture disque, pour une valeur qui ne
 * change qu'aux réglages.
 *
 * Vide tant que rien n'a été publié, ce qui est la bonne réponse par défaut :
 * « je ne peux pas prouver qu'on écrit au même endroit ». Voir
 * [fr.moovie.tv.data.sync.SyncSettingsRepository.syncFingerprint].
 */
object RemoteSyncIdentity {

    @Volatile
    var fingerprint: String = ""
        private set

    fun publish(value: String) {
        fingerprint = value
    }
}
