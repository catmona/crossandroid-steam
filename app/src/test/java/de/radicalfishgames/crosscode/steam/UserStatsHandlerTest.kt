package de.radicalfishgames.crosscode.steam

import `in`.dragonbra.javasteam.types.KeyValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class UserStatsHandlerTest {
    private fun kv(
        name: String,
        vararg children: KeyValue,
    ) = KeyValue(name).apply { children.forEach { this.children.add(it) } }

    private fun leaf(
        name: String,
        value: String,
    ) = KeyValue(name, value)

    /** A stats block shaped like Steam's GetUserStats schema for CrossCode. */
    private fun stats() =
        kv(
            "stats",
            kv(
                "1",
                leaf("type", "4"),
                kv(
                    "bits",
                    kv(
                        "0",
                        leaf("name", "TROPHY_FIRST_STEPS"),
                        kv(
                            "display",
                            kv("name", leaf("english", "First Steps"), leaf("german", "Erste Schritte")),
                            kv("desc", leaf("english", "Finish the tutorial")),
                            leaf("icon", "abc123.jpg"),
                        ),
                    ),
                    kv(
                        "31",
                        leaf("name", "TROPHY_LAST_BIT"),
                        kv("display", leaf("name", "Plain Name")),
                    ),
                ),
            ),
            kv("7", kv("bits", kv("2", leaf("name", "TROPHY_NO_DISPLAY")))),
            kv("2", leaf("type", "1"), leaf("name", "PLAIN_INT_STAT")),
        )

    private fun bytes(root: KeyValue): ByteArray = ByteArrayOutputStream().also { root.saveToStream(it, true) }.toByteArray()

    @Test
    fun parsesStatsUnderTheRootNode() {
        val slots = UserStatsHandler.parseSchema(bytes(kv("368340", stats())))
        assertEquals(setOf("TROPHY_FIRST_STEPS", "TROPHY_LAST_BIT", "TROPHY_NO_DISPLAY"), slots.keys)
        val first = slots.getValue("TROPHY_FIRST_STEPS")
        assertEquals(1, first.statId)
        assertEquals(0, first.bit)
        assertEquals("First Steps", first.displayName)
        assertEquals("Finish the tutorial", first.description)
        assertEquals("abc123.jpg", first.icon)
        assertEquals(
            "https://cdn.akamai.steamstatic.com/steamcommunity/public/images/apps/368340/abc123.jpg",
            first.iconUrl(368340),
        )
    }

    @Test
    fun parsesStatsOneLevelDownUnderTheAppNode() {
        val slots = UserStatsHandler.parseSchema(bytes(kv("schema", kv("368340", stats()))))
        assertEquals(3, slots.size)
        assertEquals(1, slots.getValue("TROPHY_LAST_BIT").statId)
    }

    @Test
    fun takesAPlainDisplayNameAndToleratesMissingDisplay() {
        val slots = UserStatsHandler.parseSchema(bytes(kv("368340", stats())))
        val last = slots.getValue("TROPHY_LAST_BIT")
        assertEquals(31, last.bit)
        assertEquals("Plain Name", last.displayName)
        assertNull(last.description)
        assertNull(last.icon)
        assertNull(last.iconUrl(368340))

        val none = slots.getValue("TROPHY_NO_DISPLAY")
        assertEquals(7, none.statId)
        assertEquals(2, none.bit)
        assertNull(none.displayName)
    }

    @Test
    fun ignoresStatsWithoutBits() {
        val slots = UserStatsHandler.parseSchema(bytes(kv("368340", stats())))
        assertFalse(slots.values.any { it.statId == 2 })
    }

    @Test
    fun returnsEmptyForGarbageOrNoStats() {
        assertTrue(UserStatsHandler.parseSchema(byteArrayOf(1, 2, 3)).isEmpty())
        assertTrue(UserStatsHandler.parseSchema(ByteArray(0)).isEmpty())
        assertTrue(UserStatsHandler.parseSchema(bytes(kv("368340", leaf("version", "3")))).isEmpty())
    }

    @Test
    fun isSetInHandlesTheSignBit() {
        val slot31 = UserStatsHandler.AchievementSlot(statId = 1, bit = 31)
        val slot0 = UserStatsHandler.AchievementSlot(statId = 1, bit = 0)
        val values = mapOf(1 to Int.MIN_VALUE) // only bit 31 set
        assertTrue(slot31.isSetIn(values))
        assertFalse(slot0.isSetIn(values))
        assertFalse(slot31.isSetIn(emptyMap()))
    }
}
