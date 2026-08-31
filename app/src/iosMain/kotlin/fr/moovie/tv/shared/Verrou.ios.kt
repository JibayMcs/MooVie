package fr.moovie.tv.shared

import platform.Foundation.NSRecursiveLock

/**
 * `NSRecursiveLock` et non `NSLock` : `synchronized` est réentrant sur la JVM,
 * un même thread pouvant reprendre un moniteur qu'il détient déjà. Un `NSLock`
 * simple se bloquerait lui-même dans ce cas, et la différence ne se verrait
 * qu'à l'exécution sur l'appareil — le pire endroit pour la découvrir.
 */
actual class Verrou {
    private val verrou = NSRecursiveLock()

    actual fun verrouiller() = verrou.lock()

    actual fun deverrouiller() = verrou.unlock()
}
