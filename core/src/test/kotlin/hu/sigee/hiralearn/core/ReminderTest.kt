package hu.sigee.hiralearn.core

import hu.sigee.hiralearn.core.Fixtures.card
import hu.sigee.hiralearn.core.Fixtures.learned
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderTest {
    private val budapest = ZoneId.of("Europe/Budapest")

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(budapest).toInstant().toEpochMilli()

    private fun next(now: String, time: LocalTime = LocalTime.of(19, 0)) = Reminder.nextTrigger(at(now), time, budapest)

    @Test
    fun firesLaterTodayWhenTheTimeIsStillAhead() {
        assertEquals(at("2026-03-10T19:00"), next("2026-03-10T08:30"))
    }

    @Test
    fun firesTomorrowWhenTheTimeHasPassed() {
        assertEquals(at("2026-03-11T19:00"), next("2026-03-10T20:00"))
    }

    @Test
    fun exactlyAtTheTimeMeansTomorrowSoTheJobNeverFiresTwice() {
        assertEquals(at("2026-03-11T19:00"), next("2026-03-10T19:00"))
    }

    @Test
    fun aMomentBeforeTheTimeIsStillToday() {
        assertEquals(at("2026-03-10T19:00"), Reminder.nextTrigger(at("2026-03-10T19:00") - 1, LocalTime.of(19, 0), budapest))
    }

    @Test
    fun keepsTheWallClockTimeAcrossDaylightSavingChanges() {
        // Clocks go forward on 2026-03-29 in Europe: that day has 23 hours, so 20:00 to 19:00 is 22 h, not 23.
        val trigger = next("2026-03-28T20:00")
        assertEquals(at("2026-03-29T19:00"), trigger)
        assertEquals(22 * 3_600_000L, trigger - at("2026-03-28T20:00"))
    }

    @Test
    fun aTimeSkippedByTheDstJumpFiresJustAfterIt() {
        // 02:30 does not exist on 2026-03-29 (02:00 jumps to 03:00).
        assertEquals(at("2026-03-29T03:30"), next("2026-03-28T23:00", LocalTime.of(2, 30)))
    }

    @Test
    fun countsKanaNotCards() {
        val states = learned("あ", "い", due = 0) + learned("う", due = 1_000)
        assertEquals(2, Reminder.dueKanaCount(states, now = 500))
    }

    @Test
    fun aKanaWithOnlyOneDueSkillStillCounts() {
        val states = listOf(card("か", Skill.READING, due = 0), card("か", Skill.WRITING, due = 5_000)).associateBy { it.id }
        assertEquals(1, Reminder.dueKanaCount(states, now = 0))
        assertEquals(0, Reminder.dueKanaCount(states, now = -1))
    }

    @Test
    fun nothingLearnedMeansNothingDue() {
        assertEquals(0, Reminder.dueKanaCount(emptyMap(), now = 0))
    }
}
