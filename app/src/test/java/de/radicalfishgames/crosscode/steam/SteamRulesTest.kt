package de.radicalfishgames.crosscode.steam

import `in`.dragonbra.javasteam.enums.EResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamRulesTest {
    private val schema = setOf("ACHIEVEMENT_1", "ACHIEVEMENT_2", "STEAM_ONLY")
    private val trophies = setOf("ACHIEVEMENT_1", "ACHIEVEMENT_2", "GAME_ONLY")

    private fun verdict(
        id: String,
        schemaIds: Set<String>?,
        trophyIds: Set<String>?,
    ) = SteamRules.achievementVerdict(id, schemaIds, trophyIds)

    @Test
    fun schemaDecidesWhenKnown() {
        assertEquals(SteamRules.Verdict.ACCEPT, verdict("ACHIEVEMENT_1", schema, trophies))
        assertEquals(SteamRules.Verdict.ACCEPT, verdict("STEAM_ONLY", schema, trophies))
        assertEquals(SteamRules.Verdict.REFUSE, verdict("GAME_ONLY", schema, trophies))
        assertEquals(SteamRules.Verdict.REFUSE, verdict("MADE_UP", schema, trophies))
        assertEquals(SteamRules.Verdict.ACCEPT, verdict("STEAM_ONLY", schema, null))
    }

    @Test
    fun trophyListDecidesWithoutSchema() {
        assertEquals(SteamRules.Verdict.ACCEPT, verdict("GAME_ONLY", null, trophies))
        assertEquals(SteamRules.Verdict.REFUSE, verdict("MADE_UP", null, trophies))
        assertEquals(SteamRules.Verdict.ACCEPT, verdict("GAME_ONLY", emptySet(), trophies))
    }

    @Test
    fun waitsWithNoList() {
        assertEquals(SteamRules.Verdict.WAIT, verdict("ACHIEVEMENT_1", null, null))
        assertEquals(SteamRules.Verdict.REFUSE, verdict("A B", null, null))
    }

    /** Ids from a real CrossCode save; Steam had them, the first allow-list refused the hyphen. */
    @Test
    fun acceptsRealCrossCodeIds() {
        val real = setOf("storyProgress-01_1", "storyProgress-02_1", "kill-boss-crab_1", "kill-boss-turret_1")
        for (id in real) {
            assertEquals(id, SteamRules.Verdict.ACCEPT, verdict(id, real + "other_1", null))
            assertEquals(id, SteamRules.Verdict.ACCEPT, verdict(id, null, real))
            assertEquals(id, SteamRules.Verdict.ACCEPT, verdict(id, real, real))
            assertEquals(id, SteamRules.Verdict.WAIT, verdict(id, null, null))
        }
    }

    @Test
    fun rejectsMalformedIds() {
        val all = setOf("", "A B", "A;DROP", "X".repeat(65), "ÄCH", "a\"b", "a.b")
        for (id in all) assertEquals(id, SteamRules.Verdict.REFUSE, verdict(id, all, all))
    }

    @Test
    fun notOwnedNeedsTwoRefusals() {
        assertTrue(SteamRules.looksNotOwned(EResult.AccessDenied, EResult.AccessDenied))
        assertFalse(SteamRules.looksNotOwned(EResult.AccessDenied, EResult.OK))
        assertFalse(SteamRules.looksNotOwned(EResult.AccessDenied, null))
        assertFalse(SteamRules.looksNotOwned(EResult.OK, EResult.AccessDenied))
        assertFalse(SteamRules.looksNotOwned(EResult.Timeout, EResult.AccessDenied))
        assertFalse(SteamRules.looksNotOwned(EResult.AccessDenied, EResult.ServiceUnavailable))
    }

    @Test
    fun retryBacksOffThenStops() {
        assertEquals(30_000L, SteamRules.retryDelayMs(0))
        assertEquals(60_000L, SteamRules.retryDelayMs(1))
        assertEquals(480_000L, SteamRules.retryDelayMs(4))
        assertEquals(600_000L, SteamRules.retryDelayMs(5))
        assertEquals(600_000L, SteamRules.retryDelayMs(SteamRules.RETRY_ATTEMPTS - 1))
        assertNull(SteamRules.retryDelayMs(SteamRules.RETRY_ATTEMPTS))
        assertNull(SteamRules.retryDelayMs(-1))
    }
}
