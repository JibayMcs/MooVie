package fr.moovie.tv.shared

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.text.Normalizer
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

actual val dispatcherEs: CoroutineDispatcher = Dispatchers.IO

actual fun maintenantMs(): Long = System.currentTimeMillis()

actual fun dechiffrerAesCbc(donnees: ByteArray, cle: ByteArray, iv: ByteArray): ByteArray? =
    runCatching {
        Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(cle, "AES"), IvParameterSpec(iv))
        }.doFinal(donnees)
    }.getOrNull()

actual fun enNfd(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)

actual fun genererUuid(): String = UUID.randomUUID().toString()

actual fun espaceLibre(chemin: okio.Path): Long =
    runCatching { chemin.toFile().usableSpace }.getOrDefault(Long.MAX_VALUE)

actual fun formaterDecimal(valeur: Double, decimales: Int): String =
    String.format(java.util.Locale.getDefault(), "%.${decimales}f", valeur)

actual fun espaceTotal(chemin: okio.Path): Long =
    runCatching { chemin.toFile().totalSpace }.getOrDefault(0L)

actual fun fabriqueParDefaut(): androidx.lifecycle.ViewModelProvider.Factory =
    androidx.lifecycle.ViewModelProvider.NewInstanceFactory()
