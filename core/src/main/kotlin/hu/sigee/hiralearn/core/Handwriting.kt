package hu.sigee.hiralearn.core

import kotlin.math.hypot
import kotlin.math.max

data class Point(val x: Float, val y: Float)

typealias Stroke = List<Point>

/**
 * Reference stroke data (KanjiVG, CC BY-SA 3.0), in a 109×109 box, strokes in the correct order
 * and direction. Only single-character kana have stroke data; yōon are written as two characters.
 */
class StrokeLibrary(private val byChar: Map<String, List<Stroke>>) {

    fun strokesFor(kana: Kana): List<Stroke>? = byChar[kana.char]

    fun canDraw(kana: Kana): Boolean = kana.group != KanaGroup.YOON && byChar.containsKey(kana.char)

    companion object {
        const val BOX = 109f

        fun parse(text: String): StrokeLibrary {
            val map = text.lineSequence()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .associate { line ->
                    val (char, data) = line.split('\t', limit = 2)
                    char to data.split(';').map { stroke ->
                        stroke.trim().split(' ').map { pair ->
                            val (x, y) = pair.split(',')
                            Point(x.toFloat(), y.toFloat())
                        }
                    }
                }
            return StrokeLibrary(map)
        }

        /** Loads the bundled resource; works on the JVM and on Android (Java resources are packaged in the APK). */
        fun loadBundled(): StrokeLibrary {
            val stream = StrokeLibrary::class.java.getResourceAsStream("/hiragana_strokes.txt")
                ?: error("hiragana_strokes.txt missing from resources")
            return parse(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }
    }
}

enum class DrawingProblem { NONE, STROKE_COUNT, STROKE_ORDER, STROKE_DIRECTION, SHAPE }

data class DrawingResult(
    val correct: Boolean,
    /** 0..1, how close the drawing is to the reference. */
    val score: Float,
    val problem: DrawingProblem,
    val expectedStrokes: Int,
    val drawnStrokes: Int,
    /** A different kana the drawing matches well — the learner probably mixed them up. */
    val lookedLike: Kana?,
)

/**
 * Grades a handwritten kana against reference strokes.
 *
 * Both drawings are normalised (centred, uniformly scaled to a unit box) so size and position on the
 * pad do not matter. With the right stroke count, each drawn stroke is compared point-by-point with
 * the reference stroke of the same index, which also catches wrong order or direction. With a
 * different stroke count only the overall shape is compared, and the answer counts as wrong.
 */
class HandwritingGrader(
    private val library: StrokeLibrary,
    private val passDistance: Float = 0.12f,
    /**
     * How close the overall shape must be to say "right shape, wrong stroke count". Point-cloud distance ignores
     * stroke structure, so it needs a much tighter bound than [passDistance]: at 0.12, over half of all drawings of a
     * *different* kana got the stroke-count hint. Half of it keeps every merged or split stroke (with wobble) and
     * misfires on ~2% of other kana, mostly a base kana drawn for its dakuten form, which is a stroke-count slip anyway.
     */
    private val strokeCountHintDistance: Float = passDistance / 2,
) {
    fun grade(drawn: List<Stroke>, target: Kana, candidates: Collection<Kana>): DrawingResult {
        val reference = library.strokesFor(target) ?: error("No strokes for ${target.char}")
        val clean = drawn.filter { it.isNotEmpty() }
        if (clean.isEmpty()) {
            return DrawingResult(false, 0f, DrawingProblem.SHAPE, reference.size, 0, null)
        }
        val user = normalize(clean)
        val ref = normalize(reference)

        val (distance, problem) = if (user.size == ref.size) {
            val ordered = orderedDistance(user, ref)
            when {
                ordered <= passDistance -> ordered to DrawingProblem.NONE
                // Every stroke is in the right place, just drawn backwards somewhere.
                directionFreeDistance(user, ref) <= passDistance -> ordered to DrawingProblem.STROKE_DIRECTION
                // Right strokes, wrong order.
                assignmentDistance(user, ref) <= passDistance -> ordered to DrawingProblem.STROKE_ORDER
                else -> ordered to DrawingProblem.SHAPE
            }
        } else {
            val shape = shapeDistance(user, ref)
            shape to if (shape <= strokeCountHintDistance) DrawingProblem.STROKE_COUNT else DrawingProblem.SHAPE
        }
        val correct = problem == DrawingProblem.NONE

        val lookedLike = if (correct) null else candidates
            .asSequence()
            .filter { it.id != target.id && library.canDraw(it) && it.char != target.char }
            .mapNotNull { other ->
                val otherRef = normalize(library.strokesFor(other) ?: return@mapNotNull null)
                val d = if (otherRef.size == user.size) orderedDistance(user, otherRef) else Float.MAX_VALUE
                if (d <= passDistance) other to d else null
            }
            .minByOrNull { it.second }
            ?.takeIf { it.second < distance }
            ?.first

        return DrawingResult(
            correct = correct,
            score = (1f - distance / (passDistance * 2.5f)).coerceIn(0f, 1f),
            problem = problem,
            expectedStrokes = reference.size,
            drawnStrokes = clean.size,
            lookedLike = lookedLike,
        )
    }

    private fun orderedDistance(user: List<Stroke>, ref: List<Stroke>): Float =
        user.indices.map { strokeDistance(user[it], ref[it]) }.average().toFloat()

    private fun directionFreeDistance(user: List<Stroke>, ref: List<Stroke>): Float =
        user.indices.map { minOf(strokeDistance(user[it], ref[it]), strokeDistance(user[it].reversed(), ref[it])) }
            .average().toFloat()

    /** Greedy best matching of drawn strokes to reference strokes, ignoring order. */
    private fun assignmentDistance(user: List<Stroke>, ref: List<Stroke>): Float {
        val pairs = user.indices.flatMap { u -> ref.indices.map { r -> Triple(u, r, strokeDistance(user[u], ref[r])) } }
            .sortedBy { it.third }
        val usedU = mutableSetOf<Int>()
        val usedR = mutableSetOf<Int>()
        var total = 0f
        for ((u, r, d) in pairs) {
            if (u in usedU || r in usedR) continue
            usedU += u; usedR += r; total += d
        }
        return total / user.size
    }

    private fun strokeDistance(a: Stroke, b: Stroke): Float {
        val ra = resample(a, SAMPLES)
        val rb = resample(b, SAMPLES)
        return ra.indices.map { dist(ra[it], rb[it]) }.average().toFloat()
    }

    /** Symmetric mean nearest-point distance between the two drawings as point clouds. */
    private fun shapeDistance(user: List<Stroke>, ref: List<Stroke>): Float {
        val u = user.flatMap { resample(it, SAMPLES) }
        val r = ref.flatMap { resample(it, SAMPLES) }
        fun oneWay(from: List<Point>, to: List<Point>) = from.map { p -> to.minOf { dist(p, it) } }.average()
        return ((oneWay(u, r) + oneWay(r, u)) / 2).toFloat()
    }

    companion object {
        private const val SAMPLES = 24

        private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

        /** Centre the drawing and scale its larger side to 1, keeping the aspect ratio. */
        fun normalize(strokes: List<Stroke>): List<Stroke> {
            val all = strokes.flatten()
            val minX = all.minOf { it.x }; val maxX = all.maxOf { it.x }
            val minY = all.minOf { it.y }; val maxY = all.maxOf { it.y }
            val size = max(max(maxX - minX, maxY - minY), 1e-3f)
            val cx = (minX + maxX) / 2; val cy = (minY + maxY) / 2
            return strokes.map { s -> s.map { Point((it.x - cx) / size, (it.y - cy) / size) } }
        }

        /** Resample a polyline to [n] points evenly spaced along its length. */
        fun resample(stroke: Stroke, n: Int): List<Point> {
            if (stroke.size == 1) return List(n) { stroke[0] }
            val lengths = FloatArray(stroke.size)
            for (i in 1 until stroke.size) lengths[i] = lengths[i - 1] + dist(stroke[i - 1], stroke[i])
            val total = lengths.last()
            if (total < 1e-6f) return List(n) { stroke[0] }
            val out = ArrayList<Point>(n)
            var seg = 1
            for (k in 0 until n) {
                val target = total * k / (n - 1)
                while (seg < stroke.lastIndex && lengths[seg] < target) seg++
                val segLen = lengths[seg] - lengths[seg - 1]
                val t = if (segLen < 1e-6f) 0f else ((target - lengths[seg - 1]) / segLen).coerceIn(0f, 1f)
                val a = stroke[seg - 1]; val b = stroke[seg]
                out += Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
            return out
        }
    }
}
