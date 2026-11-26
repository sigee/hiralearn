package hu.sigee.hiralearn.ui.components

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.sigee.hiralearn.R
import hu.sigee.hiralearn.core.Point
import hu.sigee.hiralearn.core.Stroke
import hu.sigee.hiralearn.core.StrokeLibrary
import hu.sigee.hiralearn.ui.ink.BrushStyle
import hu.sigee.hiralearn.ui.ink.drawBrushStrokes
import hu.sigee.hiralearn.ui.theme.InkColors
import hu.sigee.hiralearn.ui.theme.LocalInkColors
import hu.sigee.hiralearn.ui.theme.SerifFont
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.hypot

private const val BOX = StrokeLibrary.BOX

/** Base brush width of the learner's ink, as a fraction of the pad width. */
private const val HAND_WIDTH = 0.034f

/**
 * A manuscript-paper square to write on. Strokes are reported in the 109×109 reference box and drawn as
 * brush ink whose width follows the writing speed.
 */
@Composable
fun DrawingPad(
    strokes: List<Stroke>,
    onStrokesChange: (List<Stroke>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    guide: List<Stroke>? = null,
    overlay: List<Stroke>? = null,
) {
    val currentStrokes by rememberUpdatedState(strokes)
    val onChange by rememberUpdatedState(onStrokesChange)
    var inProgress by remember { mutableStateOf<List<Point>?>(null) }
    val c = LocalInkColors.current

    Canvas(
        modifier
            .aspectRatio(1f)
            .clipToBounds()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                // Clamped so a stroke dragged past the edge runs along it instead of leaving the paper.
                fun toBox(o: Offset) = Point(
                    (o.x / size.width * BOX).coerceIn(0f, BOX),
                    (o.y / size.height * BOX).coerceIn(0f, BOX),
                )
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val points = mutableListOf(toBox(down.position))
                    inProgress = points.toList()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.historical.forEach { points += toBox(it.position) }
                        points += toBox(change.position)
                        change.consume()
                        inProgress = points.toList()
                        if (!change.pressed) break
                    }
                    inProgress = null
                    onChange(currentStrokes + listOf(points.toList()))
                }
            },
    ) {
        drawManuscriptPaper(c)
        guide?.let { drawBrushStrokes(it, c.ink.copy(alpha = 0.10f), 0.075f, BrushStyle.REFERENCE) }
        drawBrushStrokes(strokes, c.ink, HAND_WIDTH, BrushStyle.HAND)
        inProgress?.let { drawBrushStrokes(listOf(it), c.ink, HAND_WIDTH, BrushStyle.HAND) }
        // The model shown over the learner's ink after checking: a thin indigo line.
        overlay?.let { drawPolylines(it, c.seal.copy(alpha = 0.75f), widthFraction = 0.018f) }
    }
}

/**
 * The reference strokes written one by one with a brush, with stroke numbers. Tap replay to watch again;
 * [loop] repeats it after a pause.
 */
@Composable
fun StrokeOrderView(strokes: List<Stroke>, modifier: Modifier = Modifier, loop: Boolean = false) {
    val timeline = remember(strokes) { StrokeTimeline(strokes) }
    val elapsed = remember(strokes) { Animatable(0f) }
    var replay by remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val c = LocalInkColors.current

    LaunchedEffect(strokes, replay) {
        do {
            elapsed.snapTo(0f)
            elapsed.animateTo(timeline.totalMs, tween(durationMillis = timeline.totalMs.toInt(), easing = LinearEasing))
            if (loop) kotlinx.coroutines.delay(1500)
        } while (loop)
    }

    Box(modifier.aspectRatio(1f)) {
        Canvas(Modifier.fillMaxSize()) {
            drawManuscriptPaper(c)
            drawBrushStrokes(strokes, c.ink.copy(alpha = 0.08f), 0.07f, BrushStyle.REFERENCE)
            val t = elapsed.value
            strokes.indices.forEach { i ->
                timeline.partial(i, t)?.let { drawBrushStrokes(listOf(it), c.ink, 0.07f, BrushStyle.REFERENCE) }
            }
            val unit = size.width / BOX
            strokes.forEachIndexed { i, stroke ->
                if (t > timeline.startMs[i]) {
                    val start = stroke.first()
                    drawText(
                        measurer, "${i + 1}",
                        topLeft = Offset(start.x * unit - 14.dp.toPx(), start.y * unit - 16.dp.toPx()),
                        style = TextStyle(color = c.seal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = SerifFont),
                    )
                }
            }
        }
        IconButton(onClick = { replay++ }, modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp)) {
            Icon(painterResource(R.drawable.ic_replay), contentDescription = stringResource(R.string.replay_stroke_order), tint = c.pencil)
        }
    }
}

/** Pen speed of the stroke-order animation, in milliseconds per unit of the 109-unit box. */
private const val MS_PER_UNIT = 9f

/** Shortest time a stroke takes, so tiny ticks are still visible. */
private const val MIN_STROKE_MS = 300f

/** Pen-up pause between strokes. */
private const val PAUSE_MS = 180f

/** Extra "distance" for a full reversal of direction, so the pen visibly dwells at sharp corners. */
private const val CORNER_UNITS = 14f

/** Turns gentler than this (radians) are part of a curve and cost nothing extra. */
private const val CORNER_THRESHOLD = 0.5f

/**
 * Timing of the stroke-order animation. Every stroke used to take the same time, so a short tick crawled while a long
 * stroke with several turns (the second stroke of え) flew by. Here the pen moves at one steady speed along the stroke,
 * slowing at sharp corners, so every stroke reads at the same pace.
 */
private class StrokeTimeline(private val strokes: List<Stroke>) {
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
    private val durations = distances.map { maxOf(MIN_STROKE_MS, (it.lastOrNull() ?: 0f) * MS_PER_UNIT) }
    val startMs: FloatArray = FloatArray(strokes.size).also { s ->
        for (i in 1 until strokes.size) s[i] = s[i - 1] + durations[i - 1] + PAUSE_MS
    }
    val totalMs: Float = if (strokes.isEmpty()) 0f else startMs.last() + durations.last()

    /** The part of stroke [index] written by [elapsedMs], ending at an interpolated point; null before it starts. */
    fun partial(index: Int, elapsedMs: Float): Stroke? {
        val stroke = strokes[index]
        val local = (elapsedMs - startMs[index]) / durations[index]
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
}

/** Sheet of manuscript paper: light sheet, dashed centre guides, ruled border. */
fun DrawScope.drawManuscriptPaper(c: InkColors) {
    drawRect(c.sheet)
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
    val w = 1.dp.toPx()
    drawLine(c.gridDash, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w, pathEffect = dash)
    drawLine(c.gridDash, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w, pathEffect = dash)
    drawRect(c.gridLine, style = DrawStroke(1.5.dp.toPx()))
}

/** Plain constant-width lines, used for the thin model overlay. */
fun DrawScope.drawPolylines(strokes: List<Stroke>, color: Color, widthFraction: Float) {
    val unit = size.width / BOX
    val style = DrawStroke(width = size.width * widthFraction, cap = StrokeCap.Round, join = StrokeJoin.Round)
    strokes.forEach { stroke ->
        if (stroke.size < 2) return@forEach
        val path = Path().apply {
            moveTo(stroke[0].x * unit, stroke[0].y * unit)
            for (i in 1 until stroke.size) lineTo(stroke[i].x * unit, stroke[i].y * unit)
        }
        drawPath(path, color, style = style)
    }
}
