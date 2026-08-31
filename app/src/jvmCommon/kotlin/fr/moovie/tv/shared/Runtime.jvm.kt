package fr.moovie.tv.shared

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
