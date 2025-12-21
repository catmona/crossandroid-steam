package de.radicalfishgames.crosscode.preferences

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

sealed class SaveValidationResult {
    data object Valid : SaveValidationResult()

    data class Invalid(
        val reason: String,
    ) : SaveValidationResult()
}

object SaveValidator {
    private const val PREFIX = "[-!_0_!-]"
    private const val PASSWORD = ":_.NaN0"

    fun validate(content: String): SaveValidationResult {
        val trimmed = content.trim()

        if (trimmed.isEmpty()) {
            return SaveValidationResult.Invalid("Save string is empty")
        }

        if (!trimmed.startsWith(PREFIX)) {
            return SaveValidationResult.Invalid("Invalid save format: missing header")
        }

        val base64Part = trimmed.removePrefix(PREFIX)
        val decoded =
            try {
                Base64.decode(base64Part, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                return SaveValidationResult.Invalid("Invalid save format: corrupted data")
            }

        // CryptoJS format: "Salted__" + 8-byte salt + ciphertext
        if (decoded.size < 16 || String(decoded.sliceArray(0..7)) != "Salted__") {
            return SaveValidationResult.Invalid("Invalid save format: missing salt")
        }

        val salt = decoded.sliceArray(8..15)
        val ciphertext = decoded.sliceArray(16 until decoded.size)

        return try {
            val (key, iv) = evpBytesToKey(PASSWORD.toByteArray(), salt, 32, 16)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val decrypted = String(cipher.doFinal(ciphertext), Charsets.UTF_8)

            // Verify it's valid JSON
            org.json.JSONObject(decrypted)
            SaveValidationResult.Valid
        } catch (e: Exception) {
            SaveValidationResult.Invalid("Invalid save format: decryption failed")
        }
    }

    // OpenSSL EVP_BytesToKey with MD5
    private fun evpBytesToKey(
        password: ByteArray,
        salt: ByteArray,
        keyLen: Int,
        ivLen: Int,
    ): Pair<ByteArray, ByteArray> {
        val md5 = MessageDigest.getInstance("MD5")
        val result = ByteArray(keyLen + ivLen)
        var resultOffset = 0
        var prevHash: ByteArray? = null

        while (resultOffset < result.size) {
            md5.reset()
            prevHash?.let { md5.update(it) }
            md5.update(password)
            md5.update(salt)
            prevHash = md5.digest()

            val copyLen = minOf(prevHash.size, result.size - resultOffset)
            System.arraycopy(prevHash, 0, result, resultOffset, copyLen)
            resultOffset += copyLen
        }

        return Pair(result.sliceArray(0 until keyLen), result.sliceArray(keyLen until keyLen + ivLen))
    }
}
