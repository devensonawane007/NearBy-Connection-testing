package com.example.samekanprivatetrekroom.data.local

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

object CryptoHelper {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    // Derive 256-bit AES key from room password
    fun deriveKey(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    // Encrypt plaintext using AES-256-GCM
    fun encrypt(plainText: String, secretKey: SecretKeySpec): String {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            val iv = ByteArray(IV_LENGTH_BYTE)
            SecureRandom().nextBytes(iv)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            
            // Combine IV and cipherText
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Logger.error("CryptoHelper", "Encryption failed: ${e.message}", e)
            plainText // Fallback to plain text in case of fatal error
        }
    }

    // Decrypt ciphertext using AES-256-GCM
    fun decrypt(combinedBase64: String, secretKey: SecretKeySpec): String {
        return try {
            val combined = android.util.Base64.decode(combinedBase64, android.util.Base64.NO_WRAP)
            if (combined.size < IV_LENGTH_BYTE) {
                return combinedBase64
            }
            val iv = ByteArray(IV_LENGTH_BYTE)
            System.arraycopy(combined, 0, iv, 0, iv.size)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            
            val cipherTextSize = combined.size - IV_LENGTH_BYTE
            val cipherText = ByteArray(cipherTextSize)
            System.arraycopy(combined, IV_LENGTH_BYTE, cipherText, 0, cipherTextSize)
            
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            // If decryption fails, it might be plaintext or wrong password. Log it.
            Logger.warn("CryptoHelper", "Decryption failed: ${e.message}. Possibly wrong password or unencrypted packet.")
            combinedBase64
        }
    }
}
