package hu.sigee.hiralearn.core

import hu.sigee.hiralearn.core.StrokeTimeline.Companion.CORNER_THRESHOLD
import hu.sigee.hiralearn.core.StrokeTimeline.Companion.CORNER_UNITS
import hu.sigee.hiralearn.core.StrokeTimeline.Companion.MIN_STROKE_MS
import hu.sigee.hiralearn.core.StrokeTimeline.Companion.MS_PER_UNIT
import hu.sigee.hiralearn.core.StrokeTimeline.Companion.PAUSE_MS
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The stroke-order animation writes every stroke at the same pen speed, not in the same time. */
class StrokeTimelineTest {
    private val eps = 0.01f

    private fun line(vararg xy: Float): Stroke = xy.toList().chunked(2).map { (x, y) -> Point(x, y) }

    /** Distance from the stroke's first point to the pen tip, measured along the drawn polyline. */
    private fun drawnLength(s: Stroke): Float = s.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()

    @Test
    fun durationFollowsStrokeLength() {
        val t = StrokeTimeline(listOf(line(0f, 0f, 40f, 0f), line(0f, 0f, 0f, 100f)))
        assertEquals(40 * MS_PER_UNIT, t.durationMs[0], eps)
        assertEquals(100 * MS_PER_UNIT, t.durationMs[1], eps)
    }

    /** The bug: a short and a long stroke used to get the same time, so the long one was drawn far faster. */
    @Test
    fun shortAndLongStrokesAreWrittenAtTheSameSpeed() {
        val t = StrokeTimeline(listOf(line(0f, 0f, 40f, 0f), line(0f, 20f, 120f, 20f)))
        val after = 200f
        val short = t.partial(0, t.startMs[0] + after)!!
        val long = t.partial(1, t.startMs[1] + after)!!
        assertEquals(after / MS_PER_UNIT, drawnLength(short), eps)
        assertEquals(after / MS_PER_UNIT, drawnLength(long), eps)
    }

    @Test
    fun longWindingStrokeOfETakesMuchLongerThanItsTick() {
        val e = StrokeLibrary.loadBundled().strokesFor(Hiragana.byChar("え")!!)!!
        val t = StrokeTimeline(e)
        assertTrue(t.durationMs[1] > 4 * t.durationMs[0], "durations ${t.durationMs}")
    }

    @Test
    fun speedIsSteadyWithinAStrokeWhosePointsAreUnevenlySpaced() {
        // Points at x = 0, 10, 100: by point count, half the time would end at x = 10.
        val t = StrokeTimeline(listOf(line(0f, 0f, 10f, 0f, 100f, 0f)))
        val half = t.partial(0, t.durationMs[0] / 2)!!
        assertEquals(line(0f, 0f, 10f, 0f, 50f, 0f), half)
        val early = t.partial(0, t.durationMs[0] * 0.05f)!!
        assertEquals(line(0f, 0f, 5f, 0f), early)
    }

    @Test
    fun tinyStrokesStillTakeTheMinimumTime() {
        val t = StrokeTimeline(listOf(line(0f, 0f, 3f, 0f), line(0f, 0f)))
        assertEquals(MIN_STROKE_MS, t.durationMs[0], eps)
        assertEquals(MIN_STROKE_MS, t.durationMs[1], eps)
    }

    @Test
    fun strokesFollowEachOtherWithAPenUpPause() {
        val t = StrokeTimeline(listOf(line(0f, 0f, 50f, 0f), line(0f, 0f, 60f, 0f), line(0f, 0f, 70f, 0f)))
        assertEquals(listOf(0f, 450f + PAUSE_MS, 450f + 540f + 2 * PAUSE_MS), t.startMs)
        assertEquals(t.startMs[2] + 630f, t.totalMs, eps)
    }

    @Test
    fun emptyTimelineTakesNoTime() {
        val t = StrokeTimeline(emptyList())
        assertEquals(0f, t.totalMs)
        assertEquals(emptyList(), t.startMs)
    }

    @Test
    fun sharpCornersAddADwell() {
        val straight = StrokeTimeline(listOf(line(0f, 0f, 100f, 0f))).durationMs[0]
        val corner = StrokeTimeline(listOf(line(0f, 0f, 50f, 0f, 50f, 50f))).durationMs[0]
        val dwell = (PI.toFloat() / 2 - CORNER_THRESHOLD) / PI.toFloat() * CORNER_UNITS
        assertEquals(straight + dwell * MS_PER_UNIT, corner, eps)
    }

    @Test
    fun reversalDwellsLongest() {
        val hook = StrokeTimeline(listOf(line(0f, 0f, 50f, 0f, 0f, 0f))).durationMs[0]
        val dwell = (PI.toFloat() - CORNER_THRESHOLD) / PI.toFloat() * CORNER_UNITS
        assertEquals((100 + dwell) * MS_PER_UNIT, hook, 0.05f)
    }

    @Test
    fun cornerDwellDependsOnlyOnTheTurnAngle() {
        // A right-angle corner, off the origin and diagonal, costs the same dwell as an axis-aligned one.
        val corner = StrokeTimeline(listOf(line(20f, 30f, 50f, 60f, 80f, 30f))).durationMs[0]
        val dwell = (PI.toFloat() / 2 - CORNER_THRESHOLD) / PI.toFloat() * CORNER_UNITS
        assertEquals((2 * hypot(30f, 30f) + dwell) * MS_PER_UNIT, corner, 0.05f)
    }

    @Test
    fun obliqueCornerBetweenUnequalSegmentsDwellsByItsAngle() {
        val corner = StrokeTimeline(listOf(line(20f, 30f, 50f, 60f, 50f, 90f))).durationMs[0]
        val dwell = (PI.toFloat() / 4 - CORNER_THRESHOLD) / PI.toFloat() * CORNER_UNITS
        assertEquals((hypot(30f, 30f) + 30f + dwell) * MS_PER_UNIT, corner, 0.05f)
    }

    @Test
    fun penTipMovesAlongDiagonals() {
        val t = StrokeTimeline(listOf(line(10f, 20f, 50f, 50f)))
        assertEquals(line(10f, 20f, 30f, 35f), t.partial(0, t.durationMs[0] / 2))
    }

    @Test
    fun penTipLandingExactlyOnAPointDoesNotDuplicateIt() {
        val t = StrokeTimeline(listOf(line(0f, 0f, 50f, 0f, 100f, 0f)))
        assertEquals(line(0f, 0f, 50f, 0f), t.partial(0, t.durationMs[0] / 2))
    }

    @Test
    fun zeroLengthStrokeIsDrawnAsADot() {
        val t = StrokeTimeline(listOf(line(5f, 5f, 5f, 5f)))
        assertEquals(MIN_STROKE_MS, t.durationMs[0])
        assertEquals(line(5f, 5f, 5f, 5f), t.partial(0, MIN_STROKE_MS / 2))
    }

    @Test
    fun gentleTurnsCostNothing() {
        val a = CORNER_THRESHOLD * 0.8f
        val bend = StrokeTimeline(listOf(line(0f, 0f, 20f, 0f, 20f + 20f * cos(a), 20f * sin(a)))).durationMs[0]
        assertEquals(40 * MS_PER_UNIT, bend, eps)
    }

    @Test
    fun repeatedPointsDoNotBreakTiming() {
        val t = StrokeTimeline(listOf(line(0f, 0f, 0f, 0f, 50f, 0f, 50f, 0f)))
        assertEquals(50 * MS_PER_UNIT, t.durationMs[0], eps)
        assertEquals(25f, t.partial(0, t.durationMs[0] / 2)!!.last().x, eps)
    }

    @Test
    fun strokeIsHiddenBeforeItStartsAndWholeAfterItEnds() {
        val first = line(0f, 0f, 50f, 0f)
        val second = line(0f, 0f, 0f, 50f)
        val t = StrokeTimeline(listOf(first, second))
        assertNull(t.partial(0, 0f))
        assertNull(t.partial(1, t.startMs[1]))
        assertNull(t.partial(1, t.durationMs[0] + PAUSE_MS / 2))
        assertSame(first, t.partial(0, t.durationMs[0]))
        assertSame(second, t.partial(1, t.totalMs + 1000f))
        assertNull(StrokeTimeline(listOf(line(5f, 5f))).partial(0, 10_000f))
    }
}
