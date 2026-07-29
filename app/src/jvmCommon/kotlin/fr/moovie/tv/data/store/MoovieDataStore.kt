package fr.moovie.tv.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File

/** Fichier de persistance d'un DataStore Preferences nommé (chemin par plateforme). */
expect fun moovieDataStoreFile(name: String): File

private val stores = mutableMapOf<String, DataStore<Preferences>>()

/**
 * DataStore Preferences partagé, par nom ("moovie_settings", "moovie_watch"…).
 * Instance unique par fichier — DataStore interdit deux instances actives sur
 * le même fichier — créée à la demande.
 */
fun preferencesStore(name: String): DataStore<Preferences> = synchronized(stores) {
    stores.getOrPut(name) {
        PreferenceDataStoreFactory.create(produceFile = { moovieDataStoreFile(name) })
    }
}
