package hu.sigee.hiralearn.ui.session

import hu.sigee.hiralearn.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import hu.sigee.hiralearn.ui.ink.inkWash
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.sigee.hiralearn.core.Kana
import hu.sigee.hiralearn.ui.ink.BrushStyle
import hu.sigee.hiralearn.ui.ink.ManuscriptSquare
import hu.sigee.hiralearn.ui.ink.Seal
import hu.sigee.hiralearn.ui.ink.brushPath
import hu.sigee.hiralearn.ui.theme.KanaFont
import hu.sigee.hiralearn.ui.theme.LocalInkColors
import hu.sigee.hiralearn.ui.theme.SerifFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The teacher's mark, written with a brush as it appears: an indigo ○ (one sweeping stroke, like an ensō)
 * for right, a vermilion × (two strokes) for wrong.
 */
@Composable
fun BrushMark(correct: Boolean, modifier: Modifier = Modifier) {
    val c = LocalInkColors.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(if (correct) 480 else 420, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        val d = size.minDimension
        val p = progress.value
        if (correct) {
            val r = d * 0.36f
            val pts = (0..64).map { i ->
                val t = i / 64f
                val ang = ((-115f + 335f * t) * PI / 180f).toFloat()
                val rr = r * (1f + 0.05f * sin(t * 6f))
                Offset(center.x + rr * cos(ang), center.y + rr * sin(ang))
            }
            val count = (pts.size * p).toInt().coerceIn(2, pts.size)
            drawPath(brushPath(pts.take(count), d * 0.14f, BrushStyle.REFERENCE), c.seal)
        } else {
            fun line(a: Offset, b: Offset, bow: Float) = (0..20).map { i ->
                val t = i / 20f
                val bend = bow * 4 * t * (1 - t)
                Offset(a.x + (b.x - a.x) * t + bend, a.y + (b.y - a.y) * t - bend)
            }
            val first = line(Offset(d * 0.24f, d * 0.22f), Offset(d * 0.78f, d * 0.78f), d * 0.03f)
            val second = line(Offset(d * 0.78f, d * 0.24f), Offset(d * 0.22f, d * 0.8f), -d * 0.03f)
            val p1 = (p * 2).coerceIn(0f, 1f)
            val p2 = (p * 2 - 1).coerceIn(0f, 1f)
            if (p1 > 0) drawPath(brushPath(first.take((first.size * p1).toInt().coerceIn(2, first.size)), d * 0.13f, BrushStyle.REFERENCE), c.vermilion)
            if (p2 > 0) drawPath(brushPath(second.take((second.size * p2).toInt().coerceIn(2, second.size)), d * 0.13f, BrushStyle.REFERENCE), c.vermilion)
        }
    }
}

enum class OptionState { IDLE, RIGHT, WRONG, DIMMED }

/** An answer option on a small sheet of paper with a soft offset shadow. [fontSize] overrides the default size (long words). */
@Composable
fun PaperOption(
    text: String,
    kana: Boolean,
    state: OptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    val c = LocalInkColors.current
    val shape = RoundedCornerShape(topStart = 3.dp, topEnd = 10.dp, bottomEnd = 4.dp, bottomStart = 9.dp)
    val bg = when (state) {
        OptionState.RIGHT -> c.matchaWash.copy(alpha = 0.45f).compositeOver(c.sheet)
        OptionState.WRONG -> c.vermilion.copy(alpha = 0.22f).compositeOver(c.sheet)
        else -> c.sheet
    }
    val fg = if (state == OptionState.DIMMED) c.ink.copy(alpha = 0.45f) else c.ink
    Box(
        modifier
            .height(88.dp)
            .drawBehind {
                val o = 3.dp.toPx()
                drawRect(c.gridLine.copy(alpha = 0.35f), topLeft = Offset(o, o), size = size)
            }
            .background(bg, shape)
            .border(1.dp, c.gridLine, shape)
            .clip(shape)
            .clickable(enabled = state == OptionState.IDLE, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontFamily = if (kana) KanaFont else SerifFont, fontSize = if (fontSize.isSpecified) fontSize else if (kana) 42.sp else 26.sp, maxLines = 1, softWrap = false)
    }
}

/** Session progress as a brush line that grows across the page. */
@Composable
fun InkProgress(progress: Float, modifier: Modifier = Modifier) {
    val c = LocalInkColors.current
    val p by animateFloatAsState(progress, tween(500), label = "progress")
    Canvas(modifier.height(14.dp)) {
        val y = size.height / 2
        drawLine(c.gridDash, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
        if (p > 0.005f) {
            val end = size.width * p
            val pts = (0..40).map { i ->
                val x = end * i / 40f
                Offset(x, y + sin(x / size.width * 9f) * 1.2.dp.toPx())
            }
            drawPath(brushPath(pts, 6.dp.toPx(), BrushStyle.REFERENCE), c.ink)
        }
    }
}

/** The two kana that were mixed up, side by side on manuscript squares. */
@Composable
fun MixUpComparison(target: Kana, confused: Kana) {
    val c = LocalInkColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        ComparedKana(target, c.ink)
        Text("  " + stringResource(R.string.versus) + "  ", style = MaterialTheme.typography.bodyMedium, color = c.pencil)
        ComparedKana(confused, c.vermilion.copy(alpha = 0.85f))
    }
}

@Composable
private fun ComparedKana(kana: Kana, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ManuscriptSquare(Modifier.size(76.dp)) { Text(kana.char, fontFamily = KanaFont, fontSize = 46.sp, color = color) }
        Text(kana.romaji, style = MaterialTheme.typography.labelLarge)
    }
}

/** The OK seal pressed onto the page: drops in large, lands with a bounce and a short vibration. */
@Composable
fun StampedSeal(size: Dp, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(2.6f) }
    val alpha = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        delay(400)
        launch { alpha.animateTo(1f, tween(140)) }
        launch {
            delay(150)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
    }
    Box(modifier.size(size * 1.3f), contentAlignment = Alignment.Center) {
        Seal(
            Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            },
            size = size,
        )
    }
}

/**
 * The answer's verdict in an ink wash under the question. The learner can fold it down to a single line with the
 * buttons, so it doesn't cover the drawing pad (or the options) they want to compare with the model. It starts open
 * on every question: the explanation is the point of a wrong answer.
 */
@Composable
fun FeedbackPanel(
    correct: Boolean,
    seed: Int,
    summary: @Composable ColumnScope.() -> Unit,
    details: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val ink = LocalInkColors.current
    var collapsed by rememberSaveable { mutableStateOf(false) }
    val wash = if (correct) ink.matchaWash.copy(alpha = (ink.matchaWash.alpha * 1.6f).coerceAtMost(1f)) else ink.vermilion.copy(alpha = 0.17f)
    val toggleLabel = stringResource(if (collapsed) R.string.show_feedback else R.string.hide_feedback)
    Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).inkWash(wash, seed = seed)) {
        Column(Modifier.fillMaxWidth().animateContentSize().padding(horizontal = 22.dp, vertical = if (collapsed) 10.dp else 18.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = toggleLabel) { collapsed = !collapsed },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrushMark(correct, Modifier.size(if (collapsed) 32.dp else 56.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (correct) R.string.correct else R.string.not_quite),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!collapsed) summary()
                }
                Icon(
                    painterResource(if (collapsed) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                    contentDescription = toggleLabel,
                    tint = ink.pencil,
                    modifier = Modifier.padding(start = 8.dp).size(28.dp),
                )
            }
            if (!collapsed) details()
            Spacer(Modifier.height(if (collapsed) 8.dp else 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
        }
    }
}
