package fr.moovie.tv.ui.update

/** États de la bannière de mise à jour. */
sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val version: String, val apkUrl: String) : UpdateState
    data class Downloading(val version: String, val progress: Float) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Issue d'une vérification **demandée à la main** depuis les réglages.
 *
 * Distincte de [UpdateState], qui décrit la bannière : une version trouvée s'y
 * annonce toute seule, mais « tu es à jour » et « la vérification a échoué » ne
 * s'y voient pas. Sans ce retour, appuyer sur le bouton ne produirait rien de
 * visible dans le cas le plus courant — celui où il n'y a rien à installer.
 */
enum class UpdateCheck {
    /** Aucune vérification manuelle en cours ni récente à annoncer. */
    IDLE,
    CHECKING,
    UP_TO_DATE,

    /** Release injoignable : réseau, DNS, ou GitHub en panne. */
    FAILED,
}
