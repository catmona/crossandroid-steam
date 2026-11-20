package de.radicalfishgames.crosscode.steam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SecretBoxTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val box = SecretBox { key }
    private val token = "eyJhbGciOiJFZERTQSJ9.test-only-not-a-real-token.sig"

    @Test
    fun roundTrip() {
        val sealed = box.seal(token)
        assertTrue(sealed.startsWith("v1:"))
        assertTrue(!sealed.contains(token))
        assertEquals(token, box.open(sealed))
    }

    @Test
    fun freshIvEachTime() {
        assertNotEquals(box.seal(token), box.seal(token))
    }

    @Test
    fun tamperedIsRejected() {
        val bytes = Base64.getDecoder().decode(box.seal(token).removePrefix("v1:"))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        assertNull(box.open("v1:" + Base64.getEncoder().encodeToString(bytes)))
    }

    @Test
    fun wrongKeyIsRejected() {
        val other = SecretBox { newKey() }
        assertNull(other.open(box.seal(token)))
    }

    @Test
    fun malformedIsRejected() {
        assertNull(box.open(token)) // plaintext, no prefix
        assertNull(box.open("v1:"))
        assertNull(box.open("v1:not base64!"))
        assertNull(box.open("v1:" + Base64.getEncoder().encodeToString(ByteArray(12))))
    }
}
