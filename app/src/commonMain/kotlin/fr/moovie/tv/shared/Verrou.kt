package fr.moovie.tv.shared

/**
 * Exclusion mutuelle non suspendante.
 *
 * `synchronized` est un intrinsèque de la JVM : il n'existe pas dans le source
 * set commun, et Kotlin/Native n'offre rien d'équivalent dans sa bibliothèque
 * standard. Les deux candidats écartés :
 *
 * - `kotlinx.coroutines.sync.Mutex` est suspendant, or les appelants ici ne le
 *   sont pas — `preferencesStore` est appelé depuis des constructeurs de dépôts ;
 * - `kotlinx.atomicfu` fournit bien un `SynchronizedObject`, mais c'est une
 *   dépendance de plus pour une poignée de sections critiques.
 *
 * D'où ce pont minimal. Côté JVM il *est* `synchronized`, au mot près : Android
 * et desktop gardent exactement leur comportement.
 */
expect class Verrou() {
    fun verrouiller()
    fun deverrouiller()
}

/**
 * Exécute [bloc] sous le verrou.
 *
 * `inline`, et c'est le point : `synchronized` l'est aussi, ce qui autorise un
 * `return` depuis l'intérieur du bloc vers la fonction englobante. Une méthode
 * ordinaire l'interdirait — `MoovieClock.now()` s'en sert, et la conversion
 * aurait échoué à la compilation sans cela.
 */
inline fun <T> Verrou.avec(bloc: () -> T): T {
    verrouiller()
    try {
        return bloc()
    } finally {
        deverrouiller()
    }
}
