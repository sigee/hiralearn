package hu.sigee.hiralearn.ui.ink

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.sigee.hiralearn.ui.theme.InkColors
import hu.sigee.hiralearn.ui.theme.KanaFont
import hu.sigee.hiralearn.ui.theme.LocalInkColors
import hu.sigee.hiralearn.ui.theme.SerifFont
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/**
 * Organic, slightly wobbly closed outline: a squircle ([roundness] higher = boxier) whose edge is pushed in and
 * out by seeded noise ([wobble] as a fraction of the smaller side). Same seed = same shape on every frame.
 */
fun blobPath(size: Size, seed: Int, wobble: Float = 0.04f, roundness: Float = 6f, inset: Float = 0f, points: Int = 36): Path {
    val rnd = Random(seed)
    val cx = size.width / 2
    val cy = size.height / 2
    val a = size.width / 2 - inset
    val b = size.height / 2 - inset
    val minSide = minOf(size.width, size.height)
    // Two slow "bulges" plus small per-point jitter give a brushed rather than random look.
    val p1 = rnd.nextFloat() * 2 * PI.toFloat()
    val p2 = rnd.nextFloat() * 2 * PI.toFloat()
    val pts = List(points) { i ->
        val t = 2 * PI.toFloat() * i / points
        val c = cos(t)
        val s = sin(t)
        val x = sign(c) * abs(c).pow(2f / roundness)
        val y = sign(s) * abs(s).pow(2f / roundness)
        val noise = (0.55f * sin(2 * t + p1) + 0.35f * sin(3 * t + p2) + 0.3f * (rnd.nextFloat() - 0.5f)) * wobble * minSide
        val len = kotlin.math.sqrt(x * x + y * y).coerceAtLeast(1e-3f)
        Offset(cx + x * a + x / len * noise, cy + y * b + y / len * noise)
    }
    fun mid(p: Offset, q: Offset) = Offset((p.x + q.x) / 2, (p.y + q.y) / 2)
    return Path().apply {
        moveTo(mid(pts.last(), pts[0]).x, mid(pts.last(), pts[0]).y)
        for (i in pts.indices) {
            val p = pts[i]
            val m = mid(p, pts[(i + 1) % pts.size])
            quadraticTo(p.x, p.y, m.x, m.y)
        }
        close()
    }
}

/** Shape version of [blobPath], for buttons and clipping. */
class InkBlobShape(private val seed: Int, private val wobble: Float = 0.06f, private val roundness: Float = 2.4f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(blobPath(size, seed, wobble, roundness))
}

/**
 * Washi paper: warm gradient, uneven mottling, short paper fibres, a few long kozo strands and specks.
 * Generated from a seed and cached until the size changes.
 */
fun Modifier.washiPaper(seed: Int = 11): Modifier = composed {
    val c = LocalInkColors.current
    drawWithCache {
        val gradient = Brush.linearGradient(listOf(c.paperTop, c.paperBottom), Offset.Zero, Offset(size.width * 0.35f, size.height))
        val rnd = Random(seed)
        val area = size.width * size.height
        val blotches = List((area / 70_000f).toInt().coerceIn(6, 40)) {
            Triple(Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height), 80f + rnd.nextFloat() * 220f, rnd.nextFloat())
        }
        fun fiberPath(count: Int, minLen: Float, maxLen: Float) = Path().apply {
            repeat(count) {
                val x = rnd.nextFloat() * size.width
                val y = rnd.nextFloat() * size.height
                val len = minLen + rnd.nextFloat() * (maxLen - minLen)
                val ang = rnd.nextFloat() * 2 * PI.toFloat()
                val bend = (rnd.nextFloat() - 0.5f) * len * 0.7f
                val ex = x + cos(ang) * len
                val ey = y + sin(ang) * len
                moveTo(x, y)
                quadraticTo((x + ex) / 2 - sin(ang) * bend, (y + ey) / 2 + cos(ang) * bend, ex, ey)
            }
        }
        val fibers = fiberPath((area / 1500f).toInt().coerceIn(60, 2400), 6f, 30f)
        val strands = fiberPath((area / 25_000f).toInt().coerceIn(8, 120), 40f, 110f)
        val specks = List((area / 2200f).toInt().coerceIn(30, 1600)) { Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) }
        val bigSpecks = List((area / 30_000f).toInt().coerceIn(4, 90)) { Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) }
        onDrawBehind {
            drawRect(gradient)
            blotches.forEach { (center, radius, strength) ->
                drawCircle(
                    Brush.radialGradient(listOf(c.fiber.copy(alpha = 0.02f + 0.04f * strength), Color.Transparent), center, radius),
                    radius,
                    center,
                )
            }
            drawPath(fibers, c.fiber.copy(alpha = 0.10f), style = Stroke(width = 1.2f))
            drawPath(strands, c.fiber.copy(alpha = 0.13f), style = Stroke(width = 0.9f))
            drawPoints(specks, PointMode.Points, c.fiber.copy(alpha = 0.11f), strokeWidth = 2.8f, cap = StrokeCap.Round)
            drawPoints(bigSpecks, PointMode.Points, c.fiber.copy(alpha = 0.16f), strokeWidth = 4.5f, cap = StrokeCap.Round)
        }
    }
}

/** A watercolour-like ink wash behind the content: two overlapping blobs for depth. */
fun Modifier.inkWash(color: Color, seed: Int): Modifier = drawWithCache {
    val outer = blobPath(size, seed, wobble = 0.05f, roundness = 7f)
    val inner = blobPath(size, seed + 101, wobble = 0.07f, roundness = 5f, inset = minOf(size.width, size.height) * 0.08f)
    onDrawBehind {
        drawPath(outer, color)
        drawPath(inner, color.copy(alpha = color.alpha * 0.55f))
    }
}

/** A hand-pressed seal: rough square border, faint fill, slightly rotated. Shows "OK". */
@Composable
fun Seal(modifier: Modifier = Modifier, size: Dp = 36.dp, text: String = "OK", seed: Int = 5, color: Color = LocalInkColors.current.seal) {
    Box(
        modifier
            .size(size)
            .rotate(-8f)
            .drawWithCache {
                val outline = blobPath(this.size, seed, wobble = 0.03f, roundness = 12f, inset = this.size.minDimension * 0.05f)
                val stroke = Stroke(width = this.size.minDimension * 0.075f)
                onDrawBehind {
                    drawPath(outline, color.copy(alpha = 0.08f))
                    drawPath(outline, color.copy(alpha = 0.9f), style = stroke)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = color, fontFamily = SerifFont, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.36f).sp, maxLines = 1)
    }
}

/** Ink-blob button: an elliptic, slightly uneven blot of sumi ink. [outlined] draws only the ink edge. */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    seed: Int = 1,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    val c = LocalInkColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = InkBlobShape(seed),
        colors = if (outlined) {
            ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = c.ink)
        } else {
            ButtonDefaults.buttonColors(
                containerColor = c.ink,
                contentColor = c.sheet,
                disabledContainerColor = c.ink.copy(alpha = 0.18f),
                disabledContentColor = c.ink.copy(alpha = 0.45f),
            )
        },
        border = if (outlined) BorderStroke(1.5.dp, c.ink.copy(alpha = if (enabled) 0.85f else 0.3f)) else null,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
    ) { Text(text, fontFamily = SerifFont) }
}

/**
 * Progress as a hand-drawn tally: one brush stroke per kana, in groups of five, inked when learned.
 * Each stroke leans, varies in length and thickness and tapers like a real brush mark.
 */
@Composable
fun InkTicks(learned: Int, total: Int, modifier: Modifier = Modifier, groupsPerRow: Int = 10) {
    val c = LocalInkColors.current
    val perRow = groupsPerRow * 5
    val rows = (total + perRow - 1) / perRow
    Canvas(modifier.fillMaxWidth().height((rows * 22).dp)) {
        val rnd = Random(17)
        val gap = size.width * 0.025f
        val groupWidth = (size.width - gap * (groupsPerRow - 1)) / groupsPerRow
        val spacing = groupWidth / 5
        val tickH = 14.dp.toPx()
        for (i in 0 until total) {
            val col = i % perRow
            val x = (col / 5) * (groupWidth + gap) + (col % 5 + 0.5f) * spacing
            val y = (i / perRow) * 22.dp.toPx() + 3.dp.toPx() + (rnd.nextFloat() - 0.5f) * 2.dp.toPx()
            val h = tickH * (0.78f + rnd.nextFloat() * 0.34f)
            val lean = (rnd.nextFloat() - 0.5f) * spacing * 0.6f
            val w = spacing * 0.5f * (0.8f + rnd.nextFloat() * 0.4f)
            val color = if (i < learned) c.ink.copy(alpha = 0.82f + rnd.nextFloat() * 0.18f) else c.gridDash
            val top = Offset(x + lean / 2, y)
            val bottom = Offset(x - lean / 2, y + h)
            // Tapered stroke: full width where the brush lands, thin where it lifts off.
            val path = Path().apply {
                moveTo(top.x - w / 2, top.y)
                lineTo(top.x + w / 2, top.y)
                lineTo(bottom.x + w * 0.2f, bottom.y)
                lineTo(bottom.x - w * 0.2f, bottom.y)
                close()
            }
            drawPath(path, color)
            drawCircle(color, w / 2, top)
        }
    }
}

/** A hanging scroll (kakejiku) showing one kana large. */
@Composable
fun HangingScroll(char: String, caption: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = LocalInkColors.current
    Column(modifier.width(150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Cord from the nail to the rod ends.
        Canvas(Modifier.width(110.dp).height(18.dp)) {
            val nail = Offset(size.width / 2, 2.dp.toPx())
            drawLine(c.woodDark.copy(alpha = 0.6f), nail, Offset(8.dp.toPx(), size.height), 1.5.dp.toPx())
            drawLine(c.woodDark.copy(alpha = 0.6f), nail, Offset(size.width - 8.dp.toPx(), size.height), 1.5.dp.toPx())
            drawCircle(c.woodDark, 2.5.dp.toPx(), nail)
        }
        ScrollRod(c)
        Box(
            Modifier
                .width(132.dp)
                .height(168.dp)
                .background(c.sheet)
                .drawBehind {
                    val w = 6.dp.toPx()
                    drawRect(c.mount, style = Stroke(width = w * 2))
                }
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(char, fontFamily = KanaFont, fontSize = if (char.length > 1) 64.sp else 88.sp, color = c.ink)
                Text(caption.uppercase(), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, letterSpacing = 3.sp, color = c.pencil)
            }
        }
        ScrollRod(c)
    }
}

@Composable
private fun ScrollRod(c: InkColors) {
    Box(
        Modifier
            .width(150.dp)
            .height(9.dp)
            .background(Brush.verticalGradient(listOf(c.woodLight, c.woodDark)), shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp)),
    )
}

/** One genkō yōshi (manuscript paper) square with the dashed centre guides used for practising kana. */
@Composable
fun ManuscriptSquare(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = LocalInkColors.current
    Box(
        modifier
            .background(c.sheet)
            .drawBehind {
                val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                val w = 1.dp.toPx()
                drawLine(c.gridDash, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), w, pathEffect = dash)
                drawLine(c.gridDash, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w, pathEffect = dash)
                drawRect(c.gridLine, style = Stroke(w))
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** How a kana is inked for a card level (null = not learned): faint outline → pencil → ink → full ink + seal. */
data class InkLevel(val color: Color, val weight: FontWeight, val sealed: Boolean)

@Composable
fun inkLevel(level: Int?): InkLevel {
    val c = LocalInkColors.current
    return when {
        level == null -> InkLevel(c.ink.copy(alpha = 0.13f), FontWeight.Normal, false)
        level <= 2 -> InkLevel(c.pencil.copy(alpha = 0.85f), FontWeight.Normal, false)
        level <= 5 -> InkLevel(c.ink.copy(alpha = 0.8f), FontWeight.Normal, false)
        else -> InkLevel(c.ink, FontWeight.SemiBold, true)
    }
}

/** A kana in a manuscript square, inked for its [level]; strong kana get a small OK seal, due ones a dot. */
@Composable
fun InkedKana(
    char: String,
    level: Int?,
    modifier: Modifier = Modifier,
    due: Boolean = false,
    fontSize: TextUnit = 28.sp,
    onClick: (() -> Unit)? = null,
) {
    val ink = inkLevel(level)
    val c = LocalInkColors.current
    ManuscriptSquare(modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Text(char, fontFamily = KanaFont, fontSize = fontSize, fontWeight = ink.weight, color = ink.color)
        if (ink.sealed) Seal(Modifier.align(Alignment.BottomEnd).padding(2.dp), size = 18.dp)
        if (due) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(6.dp).background(c.seal, androidx.compose.foundation.shape.CircleShape))
    }
}
