package hu.sigee.hiralearn.ui.session

import hu.sigee.hiralearn.core.AutoAdvance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The timer behind "Move on automatically", on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoAdvanceTimerTest {
    private val scope = TestScope()
    private val timer = AutoAdvanceTimer(scope)
    private var moves = 0

    /** Moves the virtual clock forward by [ms] and runs everything due by then. */
    private fun elapse(ms: Long) {
        scope.advanceTimeBy(ms)
        scope.runCurrent()
    }

    @Test
    fun movesOnOnlyAfterTheFullDelay() {
        timer.schedule(AutoAdvance.DELAY_MS) { moves++ }
        assertTrue(timer.isPending)
        elapse(AutoAdvance.DELAY_MS - 1)
        assertEquals("not before 3 seconds", 0, moves)
        elapse(1)
        assertEquals(1, moves)
        assertFalse(timer.isPending)
    }

    @Test
    fun movesOnOnce() {
        timer.schedule(AutoAdvance.DELAY_MS) { moves++ }
        scope.advanceUntilIdle()
        assertEquals(1, moves)
    }

    @Test
    fun noDelayMeansWaitingForContinue() {
        timer.schedule(null) { moves++ }
        assertFalse(timer.isPending)
        scope.advanceUntilIdle()
        assertEquals(0, moves)
    }

    @Test
    fun continueCancelsThePendingMove() {
        timer.schedule(AutoAdvance.DELAY_MS) { moves++ }
        elapse(1_000)
        timer.cancel()
        assertFalse(timer.isPending)
        scope.advanceUntilIdle()
        assertEquals(0, moves)
    }

    /** Continue on one question, then a right answer on the next: the old timer must not cut the new feedback short. */
    @Test
    fun aNewScheduleReplacesThePendingOne() {
        val log = mutableListOf<String>()
        timer.schedule(AutoAdvance.DELAY_MS) { log += "first" }
        elapse(2_000)
        timer.schedule(AutoAdvance.DELAY_MS) { log += "second" }
        elapse(AutoAdvance.DELAY_MS - 1)
        assertEquals(emptyList<String>(), log)
        elapse(1)
        assertEquals(listOf("second"), log)
        scope.advanceUntilIdle()
        assertEquals(listOf("second"), log)
    }

    /** A wrong answer after a pending right one (can't happen today, but must not inherit the old timer). */
    @Test
    fun schedulingNothingCancelsThePendingMove() {
        timer.schedule(AutoAdvance.DELAY_MS) { moves++ }
        timer.schedule(null) { moves++ }
        assertFalse(timer.isPending)
        scope.advanceUntilIdle()
        assertEquals(0, moves)
    }

    /** The action is onContinue, which cancels the timer itself; that must not cancel the move in progress. */
    @Test
    fun actionMayCancelTheTimer() {
        timer.schedule(AutoAdvance.DELAY_MS) {
            timer.cancel()
            moves++
        }
        scope.advanceUntilIdle()
        assertEquals(1, moves)
    }

    /** The action may schedule the next move (a right answer right after moving on). */
    @Test
    fun actionMayScheduleTheNextMove() {
        timer.schedule(AutoAdvance.DELAY_MS) {
            moves++
            timer.schedule(AutoAdvance.DELAY_MS) { moves++ }
        }
        elapse(AutoAdvance.DELAY_MS)
        assertEquals(1, moves)
        assertTrue(timer.isPending)
        elapse(AutoAdvance.DELAY_MS)
        assertEquals(2, moves)
    }
}
