package fr.moovie.tv.shared

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

private val aleatoire = SecureRandom()

private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val KDF = "PBKDF2WithHmacSHA256"
private const val IV_BYTES = 12
private const val TAG_BITS = 128

actual fun octetsAleatoires(taille: Int): ByteArray =
    ByteArray(taille).also(aleatoire::nextBytes)

actual suspend fun deriverCleAes(
    phrase: String,
    sel: ByteArray,
    iterations: Int,
    bitsCle: Int,
): ByteArray {
    val spec = PBEKeySpec(phrase.toCharArray(), sel, iterations, bitsCle)
    return SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
}

actual suspend fun chiffrerAesGcm(cle: ByteArray, clair: ByteArray): ByteArray {
    val iv = octetsAleatoires(IV_BYTES)
    val cipher = Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(cle, "AES"), GCMParameterSpec(TAG_BITS, iv))
    }
    // `doFinal` place déjà le tag à la suite du chiffré : la disposition rendue
    // est donc exactement `iv || chiffré || tag`, celle qu'écrivait la version
    // précédente.
    return iv + cipher.doFinal(clair)
}

actual suspend fun dechiffrerAesGcm(cle: ByteArray, blob: ByteArray): ByteArray {
    val iv = blob.copyOfRange(0, IV_BYTES)
    val corps = blob.copyOfRange(IV_BYTES, blob.size)
    val cipher = Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(cle, "AES"), GCMParameterSpec(TAG_BITS, iv))
    }
    return cipher.doFinal(corps)
}

actual fun sha1(donnees: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-1").digest(donnees)
