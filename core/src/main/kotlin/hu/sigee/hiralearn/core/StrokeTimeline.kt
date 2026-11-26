package hu.sigee.hiralearn.core

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.hypot

/**
 * Timing of the stroke-order animation. Every stroke used to take the same time, so a short tick crawled while a long
 * stroke with several turns (the second stroke of え) flew by. Here the pen moves at one steady speed along the stroke,
 * dwelling briefly at sharp corners, so every stroke reads at the same pace.
 */
class StrokeTimeline(private val strokes: List<Stroke>) {
    /** Per stroke, the cumulative pen "distance" at each point: arc length plus corner dwell. */
    private val distances: List<FloatArray> = strokes.map { stroke ->
        FloatArray(stroke.size).also { d ->
            for (i in 1 until stroke.size) {
                val a = stroke[i - 1]
                val b = stroke[i]
                var step = hypot(b.x - a.x, b.y - a.y)
                if (i >= 2) {
                    val p = stroke[i - 2]
                    val turn = angleBetween(a.x - p.x, a.y - p.y, b.x - a.x, b.y - a.y)
                    step += (turn - CORNER_THRESHOLD).coerceAtLeast(0f) / PI.toFloat() * CORNER_UNITS
                }
                d[i] = d[i - 1] + step
            }
        }
    }

    /** How long each stroke takes to write, in milliseconds. */
    val durationMs: List<Float> = distances.map { maxOf(MIN_STROKE_MS, (it.lastOrNull() ?: 0f) * MS_PER_UNIT) }

    /** When each stroke starts, in milliseconds from the start of the animation. */
    val startMs: List<Float> = strokes.indices.runningFold(0f) { start, i -> start + durationMs[i] + PAUSE_MS }
        .take(strokes.size)

    val totalMs: Float = if (strokes.isEmpty()) 0f else startMs.last() + durationMs.last()

    /** The part of stroke [index] written by [elapsedMs], ending at an interpolated pen tip; null before it starts. */
    fun partial(index: Int, elapsedMs: Float): Stroke? {
        val stroke = strokes[index]
        val local = (elapsedMs - startMs[index]) / durationMs[index]
        if (local <= 0f || stroke.size < 2) return null
        if (local >= 1f) return stroke
        val d = distances[index]
        val target = local * d.last()
        var k = 1
        while (k < d.size - 1 && d[k] < target) k++
        val span = d[k] - d[k - 1]
        val f = if (span > 0f) ((target - d[k - 1]) / span).coerceIn(0f, 1f) else 1f
        val a = stroke[k - 1]
        val b = stroke[k]
        return stroke.take(k) + Point(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
    }

    private fun angleBetween(ux: Float, uy: Float, vx: Float, vy: Float): Float {
        val lu = hypot(ux, uy)
        val lv = hypot(vx, vy)
        if (lu == 0f || lv == 0f) return 0f
        return acos(((ux * vx + uy * vy) / (lu * lv)).coerceIn(-1f, 1f))
    }

    companion object {
        /** Pen speed, in milliseconds per unit of the 109-unit box. */
        const val MS_PER_UNIT = 9f

        /** Shortest time a stroke takes, so tiny ticks are still visible. */
        const val MIN_STROKE_MS = 300f

        /** Pen-up pause between strokes. */
        const val PAUSE_MS = 180f

        /** Extra "distance" for a full reversal of direction, so the pen visibly dwells at sharp corners. */
        const val CORNER_UNITS = 14f

        /** Turns gentler than this (radians) are part of a curve and cost nothing extra. */
        const val CORNER_THRESHOLD = 0.5f
    }
}
