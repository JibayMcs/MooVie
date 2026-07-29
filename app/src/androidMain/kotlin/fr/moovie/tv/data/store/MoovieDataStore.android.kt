package fr.moovie.tv.data.store

import android.annotation.SuppressLint
import android.content.Context
import java.io.File

/**
 * Contexte applicatif, posé par MooVieApp dans attachBaseContext — avant toute
 * utilisation des repos par les ViewModels.
 */
@SuppressLint("StaticFieldLeak")
lateinit var appContext: Context

// Même chemin que l'ancien délégué preferencesDataStore(name) :
// filesDir/datastore/<name>.preferences_pb — les données existantes des
// utilisateurs (clé TMDB, reprises, historique) sont conservées.
actual fun moovieDataStoreFile(name: String): File =
    File(appContext.filesDir, "datastore/$name.preferences_pb")
