package hu.sigee.hiralearn.core

import hu.sigee.hiralearn.core.Fixtures.kana
import hu.sigee.hiralearn.core.Fixtures.strokes
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrokeLibraryTest {

    @Test
    fun parseSkipsCommentsAndBlankLines() {
        val lib = StrokeLibrary.parse(
            """
            # header

            の	0,0 10,0 10,10
            く	1.5,2.5 3,4;5,6 7,8
            """.trimIndent(),
        )
        assertEquals(listOf(listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f))), lib.strokesFor(kana("の")))
        assertEquals(
            listOf(listOf(Point(1.5f, 2.5f), Point(3f, 4f)), listOf(Point(5f, 6f), Point(7f, 8f))),
            lib.strokesFor(kana("く")),
        )
        assertNull(lib.strokesFor(kana("あ")))
    }

    @Test
    fun canDrawNeedsStrokesAndExcludesYoon() {
        val lib = StrokeLibrary.parse("き\t0,0 1,1\nきゃ\t0,0 1,1")
        assertTrue(lib.canDraw(kana("き")))
        assertFalse(lib.canDraw(kana("か")), "no stroke data")
        assertFalse(lib.canDraw(kana("きゃ")), "yōon are written as two characters")
    }

    @Test
    fun bundledDataFitsTheBoxAndCoversExactlyTheDrawableKana() {
        val drawable = Hiragana.all.filter { strokes.canDraw(it) }
        assertEquals(Hiragana.all.filter { it.group != KanaGroup.YOON }, drawable)
        drawable.forEach { k ->
            val s = strokes.strokesFor(k)!!
            assertTrue(s.isNotEmpty())
            s.forEach { stroke ->
                assertTrue(stroke.size >= 2, "${k.char} has a one-point stroke")
                stroke.forEach { assertTrue(it.x in 0f..StrokeLibrary.BOX && it.y in 0f..StrokeLibrary.BOX, "${k.char} $it") }
            }
        }
        // Dakuten add two strokes, handakuten one, to the base kana.
        assertEquals(strokes.strokesFor(kana("か"))!!.size + 2, strokes.strokesFor(kana("が"))!!.size)
        assertEquals(strokes.strokesFor(kana("は"))!!.size + 1, strokes.strokesFor(kana("ぱ"))!!.size)
    }
}

class GeometryTest {
    private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    @Test
    fun normalizeCentresAndScalesLargerSideToOne() {
        val n = HandwritingGrader.normalize(listOf(listOf(Point(10f, 20f), Point(50f, 30f))))
        assertEquals(listOf(listOf(Point(-0.5f, -0.125f), Point(0.5f, 0.125f))), n)
    }

    @Test
    fun normalizeIsScaleAndTranslationInvariant() {
        val base = listOf(listOf(Point(0f, 0f), Point(3f, 7f)), listOf(Point(5f, 1f), Point(2f, 2f)))
        val moved = base.map { s -> s.map { Point(it.x * 4 + 100, it.y * 4 - 30) } }
        val a = HandwritingGrader.normalize(base).flatten()
        val b = HandwritingGrader.normalize(moved).flatten()
        a.zip(b).forEach { (p, q) -> assertTrue(dist(p, q) < 1e-5f, "$p vs $q") }
    }

    @Test
    fun normalizeSurvivesASinglePoint() {
        val n = HandwritingGrader.normalize(listOf(listOf(Point(5f, 5f))))
        assertEquals(listOf(listOf(Point(0f, 0f))), n)
    }

    @Test
    fun resampleSpacesPointsEvenlyAndKeepsEnds() {
        // An L-shaped line of total length 20.
        val stroke = listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f))
        val r = HandwritingGrader.resample(stroke, 5)
        assertEquals(listOf(Point(0f, 0f), Point(5f, 0f), Point(10f, 0f), Point(10f, 5f), Point(10f, 10f)), r)
    }

    @Test
    fun resampleHandlesDegenerateStrokes() {
        assertEquals(List(4) { Point(1f, 2f) }, HandwritingGrader.resample(listOf(Point(1f, 2f)), 4))
        assertEquals(List(3) { Point(1f, 2f) }, HandwritingGrader.resample(listOf(Point(1f, 2f), Point(1f, 2f)), 3))
        // Repeated points in the middle (finger rested) don't break the spacing.
        val r = HandwritingGrader.resample(listOf(Point(0f, 0f), Point(4f, 0f), Point(4f, 0f), Point(8f, 0f)), 3)
        assertEquals(listOf(Point(0f, 0f), Point(4f, 0f), Point(8f, 0f)), r)
    }

    @Test
    fun resampleOfRandomPolylinesHasEqualSteps() {
        repeat(50) { seed ->
            val r = Random(seed)
            val stroke = List(r.nextInt(2, 12)) { Point(r.nextFloat() * 100, r.nextFloat() * 100) }
            val out = HandwritingGrader.resample(stroke, 24)
            assertEquals(24, out.size)
            assertEquals(stroke.first(), out.first())
            assertTrue(dist(stroke.last(), out.last()) < 1e-3f)
        }
    }
}

class GraderTest {
    private val grader = HandwritingGrader(strokes)
    private val drawable = Hiragana.all.filter { strokes.canDraw(it) }
    private fun ref(c: String) = strokes.strokesFor(kana(c))!!

    @Test
    fun emptyDrawingIsWrongWithScoreZero() {
        listOf(emptyList(), listOf(emptyList<Point>())).forEach { drawn ->
            val r = grader.grade(drawn, kana("あ"), drawable)
            assertEquals(DrawingResult(false, 0f, DrawingProblem.SHAPE, 3, 0, null), r)
        }
    }

    @Test
    fun emptyStrokesAreIgnored() {
        val r = grader.grade(listOf(emptyList<Point>()) + ref("け") + listOf(emptyList()), kana("け"), drawable)
        assertTrue(r.correct)
        assertEquals(3, r.drawnStrokes)
    }

    @Test
    fun kanaWithoutStrokesCannotBeGraded() {
        assertFailsWith<IllegalStateException> { grader.grade(listOf(ref("あ")[0]), kana("きゃ"), drawable) }
    }

    @Test
    fun perfectDrawingScoresOneAndReportsCounts() {
        val r = grader.grade(ref("あ"), kana("あ"), drawable)
        assertEquals(DrawingResult(true, 1f, DrawingProblem.NONE, 3, 3, null), r)
    }

    @Test
    fun scoreFallsWithDistanceAndStaysInRange() {
        val target = kana("す")
        var last = 2f
        for (shift in listOf(0f, 3f, 6f, 10f, 20f, 40f)) {
            // Shift only the first stroke so normalisation can't undo it.
            val drawn = ref("す").mapIndexed { i, s -> if (i == 0) s.map { Point(it.x, it.y + shift) } else s }
            val score = grader.grade(drawn, target, emptyList()).score
            assertTrue(score in 0f..1f)
            assertTrue(score <= last, "score must not grow as the drawing gets worse ($shift: $score > $last)")
            last = score
        }
        assertTrue(last < 0.5f)
    }

    @Test
    fun wrongStrokeCountWithRightShapeIsReportedAsStrokeCount() {
        val ke = ref("け")
        val merged = listOf(ke[0], ke[1] + ke[2])
        val r = grader.grade(merged, kana("け"), drawable)
        assertEquals(DrawingProblem.STROKE_COUNT, r.problem)
        assertFalse(r.correct)
    }

    @Test
    fun wrongStrokeCountAndWrongShapeIsReportedAsShape() {
        // A single short scribble in one corner: wrong count and nothing like け.
        val r = grader.grade(listOf(listOf(Point(0f, 0f), Point(3f, 1f))), kana("け"), drawable)
        assertEquals(DrawingProblem.SHAPE, r.problem)
        assertFalse(r.correct)
        assertEquals(1, r.drawnStrokes)
        assertEquals(3, r.expectedStrokes)
    }

    @Test
    fun sameCountButDifferentShapeIsReportedAsShape() {
        // こ and い both have two strokes but look nothing alike.
        val r = grader.grade(ref("い"), kana("こ"), emptyList())
        assertEquals(DrawingProblem.SHAPE, r.problem)
    }

    @Test
    fun lookedLikeIsOnlyAnAllowedCandidateThatFitsBetter() {
        val o = kana("お")
        assertEquals(o, grader.grade(ref("お"), kana("あ"), listOf(o)).lookedLike)
        assertNull(grader.grade(ref("お"), kana("あ"), emptyList()).lookedLike, "only candidates are suggested")
        assertNull(grader.grade(ref("お"), kana("あ"), listOf(kana("あ"))).lookedLike, "never the target itself")
        assertNull(grader.grade(ref("お"), kana("あ"), listOf(kana("きゃ"))).lookedLike, "yōon can't be drawn")
        assertNull(grader.grade(ref("お"), kana("あ"), listOf(kana("の"))).lookedLike, "stroke count must match")
    }

    @Test
    fun theClosestCandidateWins() {
        val r2 = grader.grade(ref("ね"), kana("れ"), drawable)
        assertFalse(r2.correct)
        assertEquals(kana("ね"), r2.lookedLike)
    }

    @Test
    fun aStricterPassDistanceRejectsSloppyDrawing() {
        val sloppy = ref("す").mapIndexed { i, s -> if (i == 0) s.map { Point(it.x, it.y + 4f) } else s }
        assertTrue(HandwritingGrader(strokes, passDistance = 0.5f).grade(sloppy, kana("す"), emptyList()).correct)
        assertFalse(HandwritingGrader(strokes, passDistance = 0.001f).grade(sloppy, kana("す"), emptyList()).correct)
    }

    @Test
    fun everyStrokeReversedIsADirectionProblemForMultiStrokeKana() {
        drawable.filter { k -> strokes.strokesFor(k)!!.size >= 2 }.forEach { k ->
            val reversed = strokes.strokesFor(k)!!.map { it.reversed() }
            val r = grader.grade(reversed, k, drawable)
            assertFalse(r.correct, k.char)
        }
    }

    @Test
    fun swappingTwoStrokesOfTheSameKanaIsAnOrderProblem() {
        val sa = ref("さ")
        val r = grader.grade(listOf(sa[1], sa[0], sa[2]), kana("さ"), drawable)
        assertEquals(DrawingProblem.STROKE_ORDER, r.problem)
        val i = ref("い")
        assertEquals(DrawingProblem.STROKE_ORDER, grader.grade(listOf(i[1], i[0]), kana("い"), drawable).problem)
    }

    @Test
    fun scoreIsLinearInDistance() {
        // score = 1 - d / (2.5 * pass); two drawings at different distances keep that ratio.
        val target = kana("こ")
        val a = grader.grade(ref("こ").mapIndexed { i, s -> if (i == 0) s.map { Point(it.x, it.y + 2f) } else s }, target, emptyList())
        val b = grader.grade(ref("こ").mapIndexed { i, s -> if (i == 0) s.map { Point(it.x, it.y + 4f) } else s }, target, emptyList())
        assertTrue(a.score > b.score)
        assertTrue(abs((1 - b.score) / (1 - a.score) - 2f) < 0.5f, "${a.score} ${b.score}")
    }
}

/** The "right shape, wrong stroke count" hint must fire for real stroke-count slips and almost never for another kana. */
class StrokeCountHintTest {
    private val grader = HandwritingGrader(strokes)
    private val drawable = Hiragana.all.filter { strokes.canDraw(it) }

    private fun wobbly(s: List<Stroke>, seed: Int, w: Float): List<Stroke> {
        val r = Random(seed)
        return s.map { st -> st.map { Point(it.x + (r.nextFloat() - 0.5f) * w, it.y + (r.nextFloat() - 0.5f) * w) } }
    }

    @Test
    fun mergedOrSplitStrokesGetTheHint() {
        drawable.forEachIndexed { i, k ->
            val ref = strokes.strokesFor(k)!!
            for (w in listOf(0f, 4f)) {
                val base = wobbly(ref, i, w)
                for (j in 0 until base.size - 1) {
                    val merged = base.take(j) + listOf(base[j] + base[j + 1]) + base.drop(j + 2)
                    assertEquals(DrawingProblem.STROKE_COUNT, grader.grade(merged, k, emptyList()).problem, "${k.char} merge $j wobble $w")
                }
                base.forEachIndexed { j, s ->
                    if (s.size >= 4) {
                        val split = base.take(j) + listOf(s.take(s.size / 2), s.drop(s.size / 2)) + base.drop(j + 1)
                        assertEquals(DrawingProblem.STROKE_COUNT, grader.grade(split, k, emptyList()).problem, "${k.char} split $j wobble $w")
                    }
                }
            }
        }
    }

    @Test
    fun anotherKanaWithADifferentStrokeCountRarelyGetsTheHint() {
        var pairs = 0
        var hinted = 0
        drawable.forEach { target ->
            drawable.forEach { other ->
                val drawn = strokes.strokesFor(other)!!
                if (drawn.size != strokes.strokesFor(target)!!.size) {
                    pairs++
                    if (grader.grade(drawn, target, emptyList()).problem == DrawingProblem.STROKE_COUNT) hinted++
                }
            }
        }
        assertTrue(hinted * 100 <= pairs * 3, "$hinted of $pairs drawings of another kana got the stroke-count hint")
        assertEquals(DrawingProblem.SHAPE, grader.grade(strokes.strokesFor(kana("の"))!!, kana("け"), emptyList()).problem)
        assertEquals(DrawingProblem.SHAPE, grader.grade(strokes.strokesFor(kana("う"))!!, kana("あ"), emptyList()).problem)
        // Forgetting the dakuten really is a stroke-count slip.
        assertEquals(DrawingProblem.STROKE_COUNT, grader.grade(strokes.strokesFor(kana("か"))!!, kana("が"), emptyList()).problem)
    }
}
