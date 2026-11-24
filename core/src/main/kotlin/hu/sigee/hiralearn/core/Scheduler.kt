package hu.sigee.hiralearn.core

/**
 * What is being tested: recognising a kana (reading), producing it (writing) or linking its sound to it (listening).
 * The names are database keys: add new skills, never rename existing ones.
 */
enum class Skill { READING, WRITING, LISTENING }

data class CardId(val kanaId: String, val skill: Skill)

/**
 * Spaced-repetition state of one (kana, skill) pair.
 *
 * All times are epoch milliseconds. [updatedAt] is kept so a future sync can resolve
 * conflicts (last-writer-wins per card) without a schema change.
 */
data class CardState(
    val id: CardId,
    val level: Int,
    val dueAt: Long,
    val introducedAt: Long,
    val lastReviewedAt: Long?,
    val timesCorrect: Int,
    val timesWrong: Int,
    val lapses: Int,
    val updatedAt: Long,
) {
    fun isDue(now: Long): Boolean = dueAt <= now
}

/**
 * Leitner-style scheduler, similar to Memrise:
 *  - a correct answer moves the card one level up, and each level waits longer before asking again;
 *  - a wrong answer (or being mixed up with another kana) drops the card [levelDropOnFail] levels and
 *    makes it due again after [relearnDelayMs], so it comes back soon.
 */
class Scheduler(
    private val intervalsMs: List<Long> = DEFAULT_INTERVALS,
    private val levelDropOnFail: Int = 2,
    private val relearnDelayMs: Long = 10 * MINUTE,
) {
    val maxLevel: Int get() = intervalsMs.lastIndex

    fun intervalFor(level: Int): Long = intervalsMs[level.coerceIn(1, maxLevel)]

    /** A freshly learned card starts at level 1. */
    fun introduce(id: CardId, now: Long): CardState = CardState(
        id = id,
        level = 1,
        dueAt = now + intervalFor(1),
        introducedAt = now,
        lastReviewedAt = null,
        timesCorrect = 0,
        timesWrong = 0,
        lapses = 0,
        updatedAt = now,
    )

    fun onCorrect(state: CardState, now: Long): CardState {
        val level = (state.level + 1).coerceAtMost(maxLevel)
        return state.copy(
            level = level,
            dueAt = now + intervalFor(level),
            lastReviewedAt = now,
            timesCorrect = state.timesCorrect + 1,
            updatedAt = now,
        )
    }

    fun onWrong(state: CardState, now: Long): CardState = state.copy(
        level = (state.level - levelDropOnFail).coerceAtLeast(1),
        dueAt = now + relearnDelayMs,
        lastReviewedAt = now,
        timesWrong = state.timesWrong + 1,
        lapses = state.lapses + 1,
        updatedAt = now,
    )

    companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR

        /** Index = level. Level 0 is unused (cards start at 1). */
        val DEFAULT_INTERVALS: List<Long> = listOf(
            0L, 4 * HOUR, 12 * HOUR, 1 * DAY, 3 * DAY, 7 * DAY,
            14 * DAY, 30 * DAY, 60 * DAY, 120 * DAY, 240 * DAY,
        )
    }
}
