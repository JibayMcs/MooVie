package fr.moovie.tv.data.settings

/**
 * Fréquence de vérification des mises à jour. [NEVER] coupe complètement la
 * vérification périodique — c'est l'équivalent d'un interrupteur, intégré au
 * même sélecteur pour n'avoir qu'un seul réglage à comprendre.
 */
enum class UpdateInterval(val minutes: Int) {
    NEVER(0),
    M15(15),
    M30(30),
    H1(60),
    H3(180),
    H6(360),
    H24(1440),
}
