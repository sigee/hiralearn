package hu.sigee.hiralearn.ui.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import hu.sigee.hiralearn.core.Stroke
import hu.sigee.hiralearn.core.StrokeLibrary
import kotlin.math.hypot
import kotlin.math.pow

/**
 * How a brush stroke's width is shaped.
 * HAND: from the learner's speed. Points of a fast movement are further apart, so the ink gets thinner
 * (a quick flick leaves a thin tail, a slow, pressed stroke stays thick).
 * REFERENCE: a calligraphic profile for the model strokes: firm landing, slightly lighter middle, tapered lift.
 */
enum class BrushStyle { HAND, REFERENCE }

/**
 * Filled outline of one brush stroke through [raw] (pixels) with base width [base].
 * The line is resampled evenly, widths are smoothed, and both ends get rounded, tapering caps.
 */
fun brushPath(raw: List<Offset>, base: Float, style: BrushStyle): Path {
    if (raw.isEmpty()) return Path()
    if (raw.size == 1 || pathLength(raw) < base * 0.3f) {
        return Path().apply { addOval(androidx.compose.ui.geometry.Rect(raw[0], base / 2)) }
    }

    // Per raw point: distance along the stroke and a speed factor.
    val cum = FloatArray(raw.size)
    for (i in 1 until raw.size) cum[i] = cum[i - 1] + dist(raw[i - 1], raw[i])
    val total = cum.last()
    val speedFactor = FloatArray(raw.size) { i ->
        if (style == BrushStyle.REFERENCE) 1f else {
            val prev = if (i > 0) dist(raw[i - 1], raw[i]) else dist(raw[0], raw[1])
            val next = if (i < raw.lastIndex) dist(raw[i], raw[i + 1]) else prev
            val v = (prev + next) / 2 / base
            (1.15f - 0.3f * v).coerceIn(0.5f, 1.15f)
        }
    }

    // Resample evenly so the outline is smooth regardless of input spacing.
    val step = (base * 0.3f).coerceAtLeast(1f)
    val n = (total / step).toInt().coerceAtLeast(2) + 1
    val pts = ArrayList<Offset>(n)
    val widths = FloatArray(n)
    var seg = 1
    for (k in 0 until n) {
        val d = total * k / (n - 1)
        while (seg < raw.lastIndex && cum[seg] < d) seg++
        val segLen = cum[seg] - cum[seg - 1]
        val t = if (segLen <= 0f) 0f else ((d - cum[seg - 1]) / segLen).coerceIn(0f, 1f)
        val a = raw[seg - 1]
        val b = raw[seg]
        pts += Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        val f = speedFactor[seg - 1] + (speedFactor[seg] - speedFactor[seg - 1]) * t
        val u = d / total
        val profile = when (style) {
            BrushStyle.HAND -> 1f
            BrushStyle.REFERENCE -> 1f - 0.12f * (4 * u * (1 - u)) // a touch lighter mid-stroke
        }
        val landing = if (u < 0.06f) 0.85f + 0.15f * (u / 0.06f) else 1f
        val lift = if (u > 0.78f) 1f - 0.72f * ((u - 0.78f) / 0.22f).pow(1.3f) else 1f
        widths[k] = base * f * profile * landing * lift
    }
    smooth(widths, if (style == BrushStyle.HAND) 4 else 2)

    // Offset both sides along the normal.
    val left = ArrayList<Offset>(n)
    val right = ArrayList<Offset>(n)
    for (i in 0 until n) {
        val a = pts[(i - 1).coerceAtLeast(0)]
        val b = pts[(i + 1).coerceAtMost(n - 1)]
        val len = dist(a, b).coerceAtLeast(1e-3f)
        val nx = -(b.y - a.y) / len
        val ny = (b.x - a.x) / len
        val h = widths[i] / 2
        left += Offset(pts[i].x + nx * h, pts[i].y + ny * h)
        right += Offset(pts[i].x - nx * h, pts[i].y - ny * h)
    }
    fun tangent(i: Int, j: Int): Offset {
        val l = dist(pts[i], pts[j]).coerceAtLeast(1e-3f)
        return Offset((pts[j].x - pts[i].x) / l, (pts[j].y - pts[i].y) / l)
    }
    val endDir = tangent(n - 2, n - 1)
    val startDir = tangent(1, 0)
    return Path().apply {
        moveTo(left[0].x, left[0].y)
        for (i in 1 until n) lineTo(left[i].x, left[i].y)
        val tip = pts[n - 1] + endDir * (widths[n - 1] * 0.9f)
        quadraticTo(tip.x, tip.y, right[n - 1].x, right[n - 1].y)
        for (i in n - 2 downTo 0) lineTo(right[i].x, right[i].y)
        val tail = pts[0] + startDir * (widths[0] * 0.9f)
        quadraticTo(tail.x, tail.y, left[0].x, left[0].y)
        close()
    }
}

/** Draws strokes given in the 109×109 reference box as brush ink scaled to this canvas. */
fun DrawScope.drawBrushStrokes(strokes: List<Stroke>, color: Color, widthFraction: Float, style: BrushStyle) {
    val unit = size.width / StrokeLibrary.BOX
    val base = size.width * widthFraction
    strokes.forEach { stroke ->
        if (stroke.isEmpty()) return@forEach
        drawPath(brushPath(stroke.map { Offset(it.x * unit, it.y * unit) }, base, style), color)
    }
}

private fun dist(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)

private fun pathLength(p: List<Offset>): Float {
    var l = 0f
    for (i in 1 until p.size) l += dist(p[i - 1], p[i])
    return l
}

private fun smooth(values: FloatArray, radius: Int) {
    if (values.size < 3 || radius <= 0) return
    val copy = values.copyOf()
    for (i in values.indices) {
        var sum = 0f
        var count = 0
        for (j in (i - radius).coerceAtLeast(0)..(i + radius).coerceAtMost(values.lastIndex)) {
            sum += copy[j]
            count++
        }
        values[i] = sum / count
    }
}
