package de.radicalfishgames.crosscode.preferences

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal LevelDB reader for extracting CrossCode saves from WebView's localStorage.
 * Implements just enough of the LevelDB format to read the cc.save key.
 */
class LocalStorageLevelDbReader(
    private val context: Context,
) {
    companion object {
        private const val TAG = "LevelDBReader"
        private const val ORIGIN = "https://appassets.androidplatform.net"
        private const val KEY = "cc.save"
        private const val ENCODING_LATIN1: Byte = 0x01

        // LevelDB log record types
        private const val RECORD_FULL = 1
        private const val RECORD_FIRST = 2
        private const val RECORD_MIDDLE = 3
        private const val RECORD_LAST = 4

        // LevelDB operation types
        private const val OP_DELETE = 0
        private const val OP_PUT = 1

        // Log file framing
        private const val BLOCK_SIZE = 32768
        private const val HEADER_SIZE = 7 // CRC(4) + length(2) + type(1)
    }

    private val targetKey: ByteArray by lazy { buildLocalStorageKey(ORIGIN, KEY) }

    private fun getLevelDbPath(): File = File(ContextCompat.getDataDir(context), "app_webview/Default/Local Storage/leveldb")

    /**
     * Reads all save slots from localStorage LevelDB.
     * @return List of save strings, empty list if no saves, or null if reading fails
     */
    fun readSaveSlots(): List<String>? {
        val dbPath = getLevelDbPath()
        if (!dbPath.exists()) return emptyList()

        try {
            // First try log files (most recent data)
            val logFiles =
                dbPath
                    .listFiles { f -> f.extension == "log" }
                    ?.sortedByDescending { it.name }
                    ?: emptyList()

            for (logFile in logFiles) {
                val value = readFromLogFile(logFile)
                if (value != null) return parseSlots(value)
            }

            // Then try .ldb files (SSTable format)
            val ldbFiles =
                dbPath
                    .listFiles { f -> f.extension == "ldb" }
                    ?.sortedByDescending { it.name }
                    ?: emptyList()

            for (ldbFile in ldbFiles) {
                val value = readFromLdbFile(ldbFile)
                if (value != null) return parseSlots(value)
            }

            return emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading LevelDB", e)
            return null
        }
    }

    private fun parseSlots(jsonString: String): List<String> {
        val json = JSONObject(jsonString)
        val slots = json.getJSONArray("slots")
        return (0 until slots.length())
            .map { slots.getString(it) }
            .filter { it.isNotEmpty() }
    }

    /**
     * Reads from a LevelDB log file (.log).
     * Log format: sequence of records in 32KB blocks. The log is append-only, so the last write wins.
     */
    private fun readFromLogFile(file: File): String? {
        var latest: String? = null
        RandomAccessFile(file, "r").use { raf ->
            val fileSize = raf.length()
            var position = 0L
            var currentRecord = ByteArray(0)

            while (position < fileSize) {
                val blockOffset = (position % BLOCK_SIZE).toInt()
                val remainingInBlock = BLOCK_SIZE - blockOffset

                if (remainingInBlock < HEADER_SIZE) {
                    // Block trailers too short for a header are zero padding
                    position += remainingInBlock
                    continue
                }

                raf.seek(position)

                val header = ByteArray(HEADER_SIZE)
                if (raf.read(header) != HEADER_SIZE) break

                val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                buf.int // CRC, not verified
                val length = buf.short.toInt() and 0xFFFF
                val type = buf.get().toInt() and 0xFF

                if (length == 0) {
                    position += HEADER_SIZE
                    continue
                }

                val data = ByteArray(length)
                if (raf.read(data) != length) break

                position += HEADER_SIZE + length

                when (type) {
                    RECORD_FULL -> {
                        parseLogBatch(data)?.let { latest = it }
                    }

                    RECORD_FIRST -> {
                        currentRecord = data
                    }

                    RECORD_MIDDLE -> {
                        currentRecord += data
                    }

                    RECORD_LAST -> {
                        currentRecord += data
                        parseLogBatch(currentRecord)?.let { latest = it }
                        currentRecord = ByteArray(0)
                    }
                }
            }
        }
        return latest
    }

    /**
     * Parses a batch of operations from the log.
     * Format: sequence(8) + count(4) + operations
     */
    private fun parseLogBatch(data: ByteArray): String? {
        if (data.size < 12) return null
        var latest: String? = null

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buf.long // sequence
        buf.int // count

        while (buf.hasRemaining()) {
            try {
                val opType = buf.get().toInt() and 0xFF

                val keyLen = readVarint(buf)
                if (keyLen < 0 || keyLen > buf.remaining()) break
                val key = ByteArray(keyLen)
                buf.get(key)

                if (opType == OP_PUT) {
                    val valueLen = readVarint(buf)
                    if (valueLen < 0 || valueLen > buf.remaining()) break
                    val value = ByteArray(valueLen)
                    buf.get(value)

                    if (key.contentEquals(targetKey)) {
                        latest = parseEncodingPrefixedString(value)
                    }
                }
            } catch (e: Exception) {
                break
            }
        }
        return latest
    }

    /**
     * Reads from a LevelDB SSTable (.ldb) by brute-force scanning for the key, ignoring block
     * structure, so a key inside a compressed block won't be found.
     */
    private fun readFromLdbFile(file: File): String? {
        try {
            val bytes = file.readBytes()
            val keyPattern = targetKey

            var i = 0
            while (i < bytes.size - keyPattern.size - 10) {
                if (matchesAt(bytes, i, keyPattern)) {
                    val valueStart = i + keyPattern.size
                    val value = extractValueAfterKey(bytes, valueStart)
                    if (value != null) return value
                }
                i++
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read ldb file: ${file.name}", e)
        }
        return null
    }

    private fun matchesAt(
        data: ByteArray,
        offset: Int,
        pattern: ByteArray,
    ): Boolean {
        if (offset + pattern.size > data.size) return false
        for (j in pattern.indices) {
            if (data[offset + j] != pattern[j]) return false
        }
        return true
    }

    /** Chrome localStorage values are prefixed with an encoding byte; only Latin-1 JSON is handled here. */
    private fun extractValueAfterKey(
        data: ByteArray,
        start: Int,
    ): String? {
        if (start >= data.size) return null

        // A few bytes of SSTable framing (value length etc.) sit between key and value
        for (offset in 0 until minOf(20, data.size - start)) {
            val pos = start + offset
            if (pos < data.size && data[pos] == ENCODING_LATIN1) {
                val jsonStart = pos + 1
                if (jsonStart < data.size && data[jsonStart] == '{'.code.toByte()) {
                    return extractJsonString(data, jsonStart)
                }
            }
        }
        return null
    }

    private fun extractJsonString(
        data: ByteArray,
        start: Int,
    ): String? {
        var depth = 0
        var i = start
        var inString = false

        while (i < data.size) {
            val c = data[i].toInt().toChar()

            if (inString) {
                when (c) {
                    '\\' -> i++ // Skip escaped character

                    '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> {
                        inString = true
                    }

                    '{' -> {
                        depth++
                    }

                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return String(data, start, i - start + 1, Charsets.ISO_8859_1)
                        }
                    }
                }
            }
            i++
        }
        return null
    }

    private fun readVarint(buf: ByteBuffer): Int {
        var result = 0
        var shift = 0
        while (buf.hasRemaining()) {
            val b = buf.get().toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift > 28) return -1
        }
        return result
    }

    private fun buildLocalStorageKey(
        origin: String,
        key: String,
    ): ByteArray {
        // Chrome localStorage key format: _ + origin + \x00 + encoding + key
        val prefix = byteArrayOf('_'.code.toByte())
        val originBytes = origin.toByteArray(Charsets.ISO_8859_1)
        val separator = byteArrayOf(0x00, ENCODING_LATIN1)
        val keyBytes = key.toByteArray(Charsets.ISO_8859_1)
        return prefix + originBytes + separator + keyBytes
    }

    private fun parseEncodingPrefixedString(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val encoding = data[0]
        val content = data.sliceArray(1 until data.size)
        return when (encoding) {
            0x00.toByte() -> String(content, Charsets.UTF_16LE)
            ENCODING_LATIN1 -> String(content, Charsets.ISO_8859_1)
            else -> String(content, Charsets.UTF_8)
        }
    }
}
