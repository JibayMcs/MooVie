package fr.moovie.tv.data.settings

/**
 * Délai d'inactivité avant l'écran de veille, quand la lecture est en pause.
 * [NEVER] désactive la veille — même logique que [UpdateInterval] : un seul
 * réglage plutôt qu'un interrupteur doublé d'une liste.
 */
enum class ScreensaverDelay(val minutes: Int) {
    NEVER(0),
    M5(5),
    M10(10),
    M15(15),
    M20(20),
    M25(25),
    M30(30),
    H1(60),
}
