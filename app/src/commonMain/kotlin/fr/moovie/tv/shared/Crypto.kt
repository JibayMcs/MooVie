package fr.moovie.tv.shared

/**
 * Primitives de chiffrement de la synchronisation.
 *
 * Les `actual` JVM sont le code d'origine, mot pour mot : `javax.crypto` et
 * `SecureRandom`. Remplacer une pile qui tourne chez les utilisateurs Android
 * et desktop n'aurait apporté aucun bénéfice et tout le risque. Côté iOS,
 * l'implémentation s'appuie sur `cryptography-kotlin` et son provider Apple —
 * CommonCrypto ne sait pas faire de GCM, et CryptoKit n'est pas atteignable
 * depuis Kotlin/Native.
 *
 * Le **format sur le fil est identique** des deux côtés, c'est la contrainte
 * qui a dicté ces signatures : un appareil Android doit pouvoir lire ce qu'un
 * iPhone a écrit sur le même dépôt, et réciproquement.
 */

/** Octets aléatoires de qualité cryptographique. */
expect fun octetsAleatoires(taille: Int): ByteArray

/**
 * Dérive une clé AES depuis une phrase de passe, en PBKDF2-HMAC-SHA256.
 *
 * Le coût est **voulu** : c'est lui qui rend une phrase humaine difficile à
 * casser. L'appelant met le résultat en cache, la dérivation ne devant pas se
 * refaire à chaque fichier.
 */
expect suspend fun deriverCleAes(
    phrase: String,
    sel: ByteArray,
    iterations: Int,
    bitsCle: Int,
): ByteArray

/**
 * Chiffre en AES-GCM et rend `iv || chiffré || tag`.
 *
 * L'IV est tiré ici et non par l'appelant, parce que les deux implémentations
 * ne le placent pas au même endroit de leur API — mais toutes deux produisent
 * cette disposition, qui est celle qu'écrivait déjà la version JVM.
 */
expect suspend fun chiffrerAesGcm(cle: ByteArray, clair: ByteArray): ByteArray

/** Inverse de [chiffrerAesGcm]. Lève si le tag ne vérifie pas. */
expect suspend fun dechiffrerAesGcm(cle: ByteArray, blob: ByteArray): ByteArray

/** SHA-1 — exigé par l'API B2 pour la somme de contrôle d'un envoi. */
expect fun sha1(donnees: ByteArray): ByteArray
