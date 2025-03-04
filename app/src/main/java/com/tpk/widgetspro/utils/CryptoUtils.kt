package com.tpk.widgetspro.utils

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    fun decryptApiKey(encryptedToken: String, fernetKey: String): String {
        val decodedKey = decodeBase64Url(fernetKey)
        if (decodedKey.size != 32) throw IllegalArgumentException("Invalid Fernet key")

        val signingKey = decodedKey.copyOfRange(0, 16)
        val encryptionKey = decodedKey.copyOfRange(16, 32)
        val tokenBytes = decodeBase64Url(encryptedToken)

        if (tokenBytes[0] != 0x80.toByte()) throw IllegalArgumentException("Invalid token version")

        val iv = tokenBytes.copyOfRange(9, 25)
        val ciphertext = tokenBytes.copyOfRange(25, tokenBytes.size - 32)
        val receivedHmac = tokenBytes.copyOfRange(tokenBytes.size - 32, tokenBytes.size)
        val dataToHmac = tokenBytes.copyOfRange(0, tokenBytes.size - 32)

        verifyHmac(dataToHmac, receivedHmac, signingKey)
        return decryptAesCbc(ciphertext, encryptionKey, iv)
    }

    private fun decodeBase64Url(input: String): ByteArray {
        val base64 = input.replace('-', '+').replace('_', '/')
        val padding = (4 - base64.length % 4) % 4
        return Base64.decode(base64 + "=".repeat(padding), Base64.DEFAULT)
    }

    private fun verifyHmac(data: ByteArray, receivedHmac: ByteArray, hmacKey: ByteArray) {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        if (!mac.doFinal(data).contentEquals(receivedHmac)) throw SecurityException("HMAC verification failed")
    }

    private fun decryptAesCbc(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}