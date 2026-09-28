package de.radicalfishgames.crosscode.preferences

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.text.NumberFormat
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class SaveInfo(
    val level: Int,
    val credit: Int,
    val playtime: Long,
    val area: String?,
    val floor: String?,
    val chapter: String?,
) {
    fun formatStats(): String {
        val parts = mutableListOf<String>()
        parts.add("Lvl $level")
        chapter?.let { parts.add(it) }
        parts.add(SaveParser.formatPlaytime(playtime))
        return parts.joinToString(" · ")
    }

    fun formatLocation(): String {
        val parts = mutableListOf<String>()
        val location = listOfNotNull(area, floor).joinToString(" - ")
        if (location.isNotEmpty()) parts.add(location)
        parts.add("${SaveParser.formatCredit(credit)} Credits")
        return parts.joinToString(" · ")
    }
}

object SaveParser {
    const val PREFIX = "[-!_0_!-]"
    private const val PASSWORD = ":_.NaN0"

    private const val RAW_PREVIEW_LENGTH = 50

    /**
     * The (stats, location) lines shown for a save in a list. Falls back to the start of the raw
     * text when the save can't be parsed.
     */
    fun summary(saveString: String): Pair<String, String> =
        parse(saveString)?.let { it.formatStats() to it.formatLocation() }
            ?: (saveString.take(RAW_PREVIEW_LENGTH) + if (saveString.length > RAW_PREVIEW_LENGTH) "…" else "") to ""

    /** Extracts display info for the save picker; null if the save can't be decrypted or parsed. */
    fun parse(saveString: String): SaveInfo? {
        val json = decrypt(saveString) ?: return null
        return try {
            val player = json.optJSONObject("player")
            val plotLine =
                json
                    .optJSONObject("vars")
                    ?.optJSONObject("storage")
                    ?.optJSONObject("plot")
                    ?.optInt("line", 0) ?: 0
            val isDlc = plotLine >= 40000

            SaveInfo(
                level = player?.optInt("level", 0) ?: 0,
                credit = player?.optInt("credit", 0) ?: 0,
                playtime = json.optLong("playtime", 0),
                area = extractLocalizedString(json, "area")?.let { capitalize(it) },
                floor = extractLocation(json),
                chapter = player?.let { extractChapter(it, isDlc) },
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts English text from a localized JSON field like {"en_US":"Cargo Ship","de_DE":"Frachtschiff"}.
     */
    private fun extractLocalizedString(
        json: JSONObject,
        key: String,
    ): String? {
        val value = json.opt(key) ?: return null
        return when (value) {
            is JSONObject -> {
                value.optString("en_US").takeIf { it.isNotEmpty() }
                    ?: value.optString("en US").takeIf { it.isNotEmpty() }
            }

            is String -> {
                value.takeIf { it.isNotEmpty() }
            }

            else -> {
                null
            }
        }
    }

    private fun extractLocation(json: JSONObject): String? {
        // Mirrors the game: a localized specialMap name wins over the raw map path
        val specialMap = extractLocalizedString(json, "specialMap")
        if (specialMap != null) return capitalize(specialMap)

        val map = json.optString("map").takeIf { it.isNotEmpty() }
        return map?.let { capitalize(it.substringAfterLast("/").replace("-", " ")) }
    }

    /** The game shows chapter + 1, except chapter 10 is "Epilogue" without the DLC. */
    private fun extractChapter(
        player: JSONObject,
        isDlc: Boolean,
    ): String? {
        val chapter = player.optInt("chapter", -1)
        if (chapter < 0) return null
        val displayChapter = chapter + 1
        return when {
            displayChapter == 11 && !isDlc -> "Epilogue"
            else -> "Chapter $displayChapter"
        }
    }

    /**
     * Saves are [PREFIX] + base64 of an OpenSSL `Salted__` AES-256-CBC blob with a fixed passphrase.
     * Returns null if decryption fails.
     */
    fun decrypt(saveString: String): JSONObject? {
        val trimmed = saveString.trim()
        if (!trimmed.startsWith(PREFIX)) return null

        val base64Part = trimmed.removePrefix(PREFIX)
        val decoded =
            try {
                Base64.decode(base64Part, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                return null
            }

        if (decoded.size < 16 || String(decoded.sliceArray(0..7)) != "Salted__") {
            return null
        }

        val salt = decoded.sliceArray(8..15)
        val ciphertext = decoded.sliceArray(16 until decoded.size)

        return try {
            val (key, iv) = evpBytesToKey(PASSWORD.toByteArray(), salt, 32, 16)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val decrypted = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            JSONObject(decrypted)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Formats credit with thousands separator (e.g., 707,346).
     */
    fun formatCredit(credit: Int): String = NumberFormat.getNumberInstance(Locale.US).format(credit)

    /** Capitalizes each word, treating '-' and '_' as spaces (e.g., "rhombus-dng" -> "Rhombus Dng"). */
    fun capitalize(text: String): String =
        text
            .split(" ", "-", "_")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercaseChar() }
            }

    /**
     * Formats playtime from seconds to HHH:MM:SS (e.g., 224449 -> "062:20:49").
     */
    fun formatPlaytime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return "%03d:%02d:%02d".format(hours, minutes, secs)
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
