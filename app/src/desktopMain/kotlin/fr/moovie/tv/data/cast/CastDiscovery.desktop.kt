package fr.moovie.tv.data.cast

/**
 * Pas encore de découverte sur desktop.
 *
 * **Ce n'est pas un choix de conception, c'est un reste à faire.** Le poste de
 * travail sait diffuser depuis peu, et il aurait autant de raisons qu'un
 * téléphone de trouver un Chromecast. Ce qui manque est la pile mDNS :
 * `NsdManager` n'a pas d'équivalent en JVM pure, et il faudrait soit une
 * dépendance, soit une centaine de lignes d'analyse de paquets DNS sur
 * `224.0.0.251:5353`.
 *
 * Rendre une liste vide plutôt que d'échouer laisse l'écran se comporter comme
 * s'il n'y avait aucun récepteur — ce qui est vrai de son point de vue, et ne
 * casse rien.
 */
actual object CastDiscovery {
    actual suspend fun discover(timeoutMs: Long): List<CastDevice> = emptyList()
}
