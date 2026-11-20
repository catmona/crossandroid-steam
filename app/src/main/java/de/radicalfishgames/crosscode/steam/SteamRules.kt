package de.radicalfishgames.crosscode.steam

import `in`.dragonbra.javasteam.enums.EResult

/** Decisions behind [SteamBridge] that need no connection, kept apart so unit tests can cover them. */
object SteamRules {
    private val API_NAME = Regex("^[A-Za-z0-9_-]{1,64}$")

    /** Steam answers that mean "try again later", not "no". */
    private val TRANSIENT =
        setOf(
            EResult.Timeout,
            EResult.ServiceUnavailable,
            EResult.Busy,
            EResult.TryAnotherCM,
            EResult.RateLimitExceeded,
            EResult.NoConnection,
            EResult.Pending,
        )

    /** What to do with an achievement id the page sent. */
    enum class Verdict { ACCEPT, REFUSE, WAIT }

    /**
     * Whether [id] may be sent to Steam. It has to be a plain API name (CrossCode's look like
     * "storyProgress-01_1"). The Steam schema ([schemaIds]) decides when it is known; before that,
     * the game's own trophy list ([trophyIds]) does. With neither known yet the answer is [Verdict.WAIT]:
     * keep the id and ask again once a list arrives.
     */
    fun achievementVerdict(
        id: String,
        schemaIds: Set<String>?,
        trophyIds: Set<String>?,
    ): Verdict {
        if (!API_NAME.matches(id)) return Verdict.REFUSE
        val list = schemaIds?.takeIf { it.isNotEmpty() } ?: trophyIds ?: return Verdict.WAIT
        return if (id in list) Verdict.ACCEPT else Verdict.REFUSE
    }

    /** A definite refusal from Steam, as opposed to a timeout (null) or a transient error. */
    fun isRefusal(result: EResult?): Boolean = result != null && result != EResult.OK && result !in TRANSIENT

    /**
     * The account doesn't own the game when Steam refuses both the stats ([statsResult]) and the
     * ownership ticket ([ticketResult]). Requiring both keeps family-shared copies, whose stats
     * work, from being taken for not owned.
     */
    fun looksNotOwned(
        statsResult: EResult?,
        ticketResult: EResult?,
    ): Boolean = isRefusal(statsResult) && isRefusal(ticketResult)

    const val RETRY_BASE_MS = 30_000L
    const val RETRY_MAX_MS = 600_000L
    const val RETRY_ATTEMPTS = 8

    /** Wait before retry number [attempt] (0-based): doubling from 30 s up to 10 min; null = stop. */
    fun retryDelayMs(attempt: Int): Long? {
        if (attempt < 0 || attempt >= RETRY_ATTEMPTS) return null
        return (RETRY_BASE_MS shl attempt.coerceAtMost(10)).coerceAtMost(RETRY_MAX_MS)
    }
}
