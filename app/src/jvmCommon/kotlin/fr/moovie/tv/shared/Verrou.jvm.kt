package fr.moovie.tv.shared

import java.util.concurrent.locks.ReentrantLock

/**
 * `ReentrantLock` et non un moniteur `synchronized` : la sémantique est la même
 * — exclusion mutuelle réentrante — mais elle s'exprime en deux appels séparés,
 * ce que réclame le découpage verrouiller/déverrouiller du code commun.
 */
actual class Verrou {
    private val verrou = ReentrantLock()

    actual fun verrouiller() = verrou.lock()

    actual fun deverrouiller() = verrou.unlock()
}
