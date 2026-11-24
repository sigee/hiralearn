package hu.sigee.hiralearn.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchedulerPropertiesTest {
    private val s = Scheduler()
    private val id = CardId("ka", Skill.WRITING)

    @Test
    fun introduceSetsEveryField() {
        val card = s.introduce(id, now = 1_234)
        assertEquals(CardState(id, 1, 1_234 + 4 * Scheduler.HOUR, 1_234, null, 0, 0, 0, 1_234), card)
    }

    @Test
    fun onCorrectUpdatesCountersAndTimesButKeepsTheRest() {
        val before = s.introduce(id, 0).copy(timesWrong = 2, lapses = 1)
        val after = s.onCorrect(before, now = 500)
        assertEquals(before.copy(level = 2, dueAt = 500 + 12 * Scheduler.HOUR, lastReviewedAt = 500, timesCorrect = 1, updatedAt = 500), after)
    }

    @Test
    fun onWrongUpdatesCountersAndTimesButKeepsTheRest() {
        val before = s.introduce(id, 0).copy(level = 5, timesCorrect = 7)
        val after = s.onWrong(before, now = 900)
        assertEquals(
            before.copy(level = 3, dueAt = 900 + 10 * Scheduler.MINUTE, lastReviewedAt = 900, timesWrong = 1, lapses = 1, updatedAt = 900),
            after,
        )
    }

    @Test
    fun everyLevelWaitsItsInterval() {
        var card = s.introduce(id, 0)
        for (level in 2..s.maxLevel) {
            card = s.onCorrect(card, now = 0)
            assertEquals(level, card.level)
            assertEquals(Scheduler.DEFAULT_INTERVALS[level], card.dueAt)
        }
        card = s.onCorrect(card, now = 0)
        assertEquals(s.maxLevel, card.level, "capped at the top level")
        assertEquals(240 * Scheduler.DAY, card.dueAt)
    }

    @Test
    fun intervalForClampsOutOfRangeLevels() {
        assertEquals(4 * Scheduler.HOUR, s.intervalFor(0))
        assertEquals(4 * Scheduler.HOUR, s.intervalFor(-5))
        assertEquals(240 * Scheduler.DAY, s.intervalFor(99))
        assertEquals(10, s.maxLevel)
    }

    @Test
    fun intervalsGrowStrictly() {
        Scheduler.DEFAULT_INTERVALS.drop(1).zipWithNext().forEach { (a, b) -> assertTrue(b > a) }
    }

    @Test
    fun wrongNeverDropsBelowLevelOne() {
        val low = s.introduce(id, 0)
        assertEquals(1, s.onWrong(low, 0).level)
        assertEquals(1, s.onWrong(low.copy(level = 2), 0).level)
        assertEquals(1, s.onWrong(low.copy(level = 3), 0).level)
        assertEquals(2, s.onWrong(low.copy(level = 4), 0).level)
    }

    @Test
    fun customSettingsAreHonoured() {
        val custom = Scheduler(intervalsMs = listOf(0L, 100L, 200L, 300L), levelDropOnFail = 1, relearnDelayMs = 7)
        assertEquals(3, custom.maxLevel)
        var card = custom.introduce(id, 10)
        assertEquals(110, card.dueAt)
        card = custom.onCorrect(card, 20)
        assertEquals(220, card.dueAt)
        card = custom.onWrong(card, 30)
        assertEquals(1, card.level)
        assertEquals(37, card.dueAt)
    }

    @Test
    fun isDueIsInclusive() {
        val card = s.introduce(id, 0)
        assertFalse(card.isDue(card.dueAt - 1))
        assertTrue(card.isDue(card.dueAt))
        assertTrue(card.isDue(card.dueAt + 1))
    }

    /** Property: for any sequence of answers, levels stay in range, a right answer never brings a card closer, a wrong one always does. */
    @Test
    fun randomAnswerSequencesKeepInvariants() {
        repeat(200) { seed ->
            val r = Random(seed)
            var now = r.nextLong(0, 1_000_000)
            var card = s.introduce(id, now)
            assertNull(card.lastReviewedAt)
            var correct = 0
            var wrong = 0
            repeat(30) {
                now += r.nextLong(0, 10 * Scheduler.DAY)
                val before = card
                if (r.nextBoolean()) {
                    card = s.onCorrect(card, now); correct++
                    assertTrue(card.level >= before.level)
                    assertTrue(card.dueAt - now >= s.intervalFor(before.level), "right answer waits at least as long as before")
                } else {
                    card = s.onWrong(card, now); wrong++
                    assertTrue(card.level <= before.level)
                    assertEquals(now + 10 * Scheduler.MINUTE, card.dueAt)
                }
                assertTrue(card.level in 1..s.maxLevel)
                assertEquals(now, card.updatedAt)
                assertEquals(now, card.lastReviewedAt)
                assertEquals(before.introducedAt, card.introducedAt)
                assertEquals(before.id, card.id)
            }
            assertEquals(correct, card.timesCorrect)
            assertEquals(wrong, card.timesWrong)
            assertEquals(wrong, card.lapses)
        }
    }
}
