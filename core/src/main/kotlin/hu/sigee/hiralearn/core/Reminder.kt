package hu.sigee.hiralearn.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Decisions behind the daily review reminder. The app schedules the job and shows the notification; what to say and
 * when to fire live here so they are tested on the JVM.
 */
object Reminder {
    /** Default reminder time: early evening, when most people have a few free minutes. */
    val DEFAULT_TIME: LocalTime = LocalTime.of(19, 0)

    /**
     * The next moment (epoch ms) at [time] of day in [zone], strictly after [now]. Using the wall-clock time of each
     * day (not "now + 24 h") keeps the reminder at the chosen hour across daylight-saving changes. If [time] does not
     * exist on a day (skipped by a DST jump), it fires just after the jump.
     */
    fun nextTrigger(now: Long, time: LocalTime, zone: ZoneId): Long {
        val current = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        var candidate = current.toLocalDate().atTime(time).atZone(zone)
        if (!candidate.isAfter(current)) candidate = current.toLocalDate().plusDays(1).atTime(time).atZone(zone)
        return candidate.toInstant().toEpochMilli()
    }

    /**
     * How many kana have at least one due card. The reminder counts kana rather than cards ("7 kana are waiting")
     * because reading and writing of the same kana are one thing to the learner.
     */
    fun dueKanaCount(states: Map<CardId, CardState>, now: Long): Int =
        states.values.filter { it.isDue(now) }.map { it.id.kanaId }.toSet().size
}
