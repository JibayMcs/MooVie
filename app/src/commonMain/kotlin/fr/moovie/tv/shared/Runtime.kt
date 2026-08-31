package fr.moovie.tv.shared

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Primitives que le code partagé utilisait implicitement tant qu'il ne visait
 * que la JVM, et qui n'existent pas en Kotlin/Native.
 *
 * Chacune est un `expect` plutôt qu'une réécriture : l'implémentation JVM est
 * exactement l'appel d'origine, donc Android et desktop ne changent pas de
 * comportement d'un iota. Seul iOS reçoit du code neuf.
 */

/**
 * Dispatcher des entrées-sorties bloquantes.
 *
 * `Dispatchers.IO` existe sur la JVM et en natif, mais pas dans le source set
 * commun : kotlinx-coroutines ne le déclare que pour les cibles qui ont
 * réellement des threads. D'où ce pont, plutôt qu'un repli sur
 * `Dispatchers.Default` — qui dimensionne son pool sur le nombre de cœurs et
 * s'épuise dès qu'on y met des appels réseau bloquants.
 */
expect val dispatcherEs: CoroutineDispatcher

/**
 * Millisecondes depuis l'époque Unix.
 *
 * Sert aux durées de validité de cache et aux horodatages envoyés aux
 * hébergeurs. C'est bien l'heure murale qui est demandée, pas une horloge
 * monotone : `TimeSource.Monotonic` ne conviendrait pas.
 */
expect fun maintenantMs(): Long

/**
 * Déchiffre en AES-CBC avec remplissage PKCS#7, ou rend null si l'entrée ne
 * s'ouvre pas avec cette clé.
 *
 * Un seul appelant — `SeekStreamingExtractor`, dont l'hébergeur sert sa charge
 * utile chiffrée sous une clé fixe présente dans son propre JavaScript. Le
 * secret n'en est pas un ; ce qu'il faut ici, c'est l'algorithme.
 */
expect fun dechiffrerAesCbc(donnees: ByteArray, cle: ByteArray, iv: ByteArray): ByteArray?

/**
 * Décomposition canonique Unicode (NFD) : « é » devient « e » suivi d'un accent
 * combinant, ce qui permet ensuite de retirer les diacritiques par simple
 * filtrage.
 *
 * `java.text.Normalizer` n'existe pas en natif ; Foundation sait le faire.
 */
expect fun enNfd(s: String): String

/**
 * Identifiant aléatoire au format UUID v4.
 *
 * Sert à nommer un appareil dans la synchronisation. Ni `java.util.UUID` ni
 * `NSUUID` n'ont d'équivalent commun, mais les deux rendent la même forme.
 */
expect fun genererUuid(): String

/**
 * Octets libres sur le volume qui contient ce chemin.
 *
 * Sert à refuser un téléchargement avant de le commencer plutôt qu'à le voir
 * s'arrêter sans un mot au milieu. Rend [Long.MAX_VALUE] si la mesure échoue :
 * mieux vaut tenter et échouer sur l'écriture que refuser un téléchargement
 * parfaitement possible.
 */
expect fun espaceLibre(chemin: okio.Path): Long

/**
 * Un nombre décimal, au séparateur de la locale.
 *
 * Remplace `"%.1f".format(x)`, qui n'existe pas en commun — mais surtout qui
 * **suit la locale** sur la JVM : une note de 7,5 s'écrit avec une virgule en
 * français et un point en anglais. Une concaténation maison aurait imposé le
 * point partout et changé l'affichage d'Android, pour des notes visibles sur
 * chaque affiche.
 */
expect fun formaterDecimal(valeur: Double, decimales: Int): String
