package fr.moovie.tv.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Un DataStore Preferences qui ne touche pas le disque.
 *
 * Il ne simule pas DataStore, il en tient le contrat : `data` émet l'état
 * courant puis chaque écriture, et `updateData` sérialise les transformations.
 * C'est tout ce dont [androidx.datastore.preferences.core.edit] a besoin, donc
 * tout ce dont les dépôts ont besoin — ils ne connaissent rien d'autre.
 *
 * Ce qu'il ne reproduit **pas**, volontairement : la persistance, et donc le
 * relancement à froid. Un test qui vérifierait qu'une valeur survit à un
 * redémarrage ne prouverait rien ici ; ce qui se teste avec lui, c'est la
 * logique des dépôts, pas la couche de stockage.
 *
 * Posé par [overrideStores], jamais construit directement par un dépôt.
 */
class InMemoryPreferences : DataStore<Preferences> {

    private val state = MutableStateFlow(emptyPreferences())

    // Les écritures se sérialisent : `edit` lit, modifie, réécrit, et deux
    // éditions concurrentes sans verrou perdraient l'une des deux.
    private val writes = Mutex()

    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = writes.withLock {
        // toPreferences() fige le résultat : rendre la copie mutable laisserait
        // l'appelant modifier l'état publié dans le dos du flux.
        val next = transform(state.value).toMutablePreferences().toPreferences()
        state.value = next
        next
    }
}

/** Installe des magasins en mémoire vierges. À appeler en `@BeforeTest`. */
fun useInMemoryStores() = overrideStores { InMemoryPreferences() }

/** Rend la main aux fichiers. À appeler en `@AfterTest`. */
fun useFileStores() = overrideStores(null)
