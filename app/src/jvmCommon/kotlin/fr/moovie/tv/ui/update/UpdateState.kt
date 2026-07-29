package fr.moovie.tv.ui.update

/** États de la bannière de mise à jour. */
sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val version: String, val apkUrl: String) : UpdateState
    data class Downloading(val version: String, val progress: Float) : UpdateState
    data class Error(val message: String) : UpdateState
}
