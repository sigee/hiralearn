package hu.sigee.hiralearn.core

import hu.sigee.hiralearn.core.Fixtures.correctAnswer
import hu.sigee.hiralearn.core.Fixtures.kana
import hu.sigee.hiralearn.core.Fixtures.learned
import hu.sigee.hiralearn.core.Fixtures.questions
import hu.sigee.hiralearn.core.Fixtures.runSession
import hu.sigee.hiralearn.core.Fixtures.strokes
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests written to kill mutants that survived `./gradlew :core:pitest`: each pins a decision the others only exercised. */
class DistractorChoiceTest {
    private val later = 1_000 * Scheduler.DAY
    private val choiceOnly = QuestionTypes(typing = false, drawing = false)

    /** Options of the first question of a review where only [due] (reading) is due and [known] are learned but not due. */
    private fun optionsFor(due: String, vararg known: String, seed: Int = 0): Set<String> {
        val states = learned(due, due = 0).filterKeys { it.skill == Skill.READING } + learned(*known, due = later)
        val session = StudySession.review(states, strokes, now = 0, types = choiceOnly, random = Random(seed))!!
        return (session.current as Task.Question).options.map { it.char }.toSet()
    }

    @Test
    fun knownKanaOfOtherGroupsComeBeforeUntaughtOnes() {
        assertEquals(setOf("あ", "が", "ぎ", "ぐ"), optionsFor("あ", "が", "ぎ", "ぐ"))
    }

    @Test
    fun withTooFewKnownKanaTheNearestLessonFillsIn() {
        // ま is in the M-row: the rest of that row is closer than the vowels at the start of the catalogue.
        repeat(5) { seed -> assertEquals(setOf("ま", "み", "む", "め"), optionsFor("ま", seed = seed)) }
    }

    @Test
    fun sameGroupBeatsOtherKnownGroups() {
        val dakuten = Hiragana.all.filter { it.group == KanaGroup.DAKUTEN }.map { it.char }
        repeat(10) { seed ->
            val options = optionsFor("ん", *(dakuten + listOf("な", "に", "ぬ", "ね", "の")).toTypedArray(), seed = seed)
            assertTrue(options.all { kana(it).group == KanaGroup.BASIC }, "seed $seed: $options")
        }
    }

    @Test
    fun lookAlikesAreAlwaysAmongTheDistractors() {
        val everything = learned(*Hiragana.all.map { it.char }.toTypedArray(), due = 0)
        repeat(10) { seed ->
            val session = StudySession.review(everything, strokes, now = 0, limit = 208, types = choiceOnly, random = Random(seed))!!
            runSession(session) { q ->
                val alikes = Hiragana.lookAlikesOf(q.kana).filter { !it.soundsLike(q.kana) }
                if (alikes.isNotEmpty()) {
                    assertTrue(q.options.any { it in alikes }, "${q.kana.char}: ${q.options.map { it.char }}")
                }
                // Distractors that aren't look-alikes come from the same group, since plenty are known.
                q.options.filter { it != q.kana && it !in alikes }.forEach {
                    assertEquals(q.kana.group, it.group, "${q.kana.char}: ${q.options.map { o -> o.char }}")
                }
                correctAnswer(q)
            }
        }
    }
}

class LearnOrderTest {
    @Test
    fun twoNewKanaInterleaveWithGrowingGaps() {
        val session = StudySession.learn(emptyMap(), strokes, batchSize = 2)!!
        val steps = runSession(session).map { step ->
            val t = step.task
            t.kana.char + ":" + when (t) {
                is Task.Introduce -> "intro"
                is Task.Question -> if (t.guided) "trace" else t.type.name.lowercase()
            }
        }
        assertEquals(
            listOf(
                "あ:intro", "あ:reading_choice", "あ:writing_choice", "い:intro", "い:reading_choice",
                "あ:trace", "い:writing_choice", "い:trace", "あ:listening_choice", "あ:trace", "い:listening_choice", "い:trace",
                "あ:reading_typing", "い:reading_typing",
                "あ:writing_drawing", "い:writing_drawing",
            ),
            steps,
        )
    }
}

class ProgressTest {
    @Test
    fun progressCountsIntroductionsAndAnswers() {
        val session = StudySession.learn(emptyMap(), strokes, batchSize = 1, drawingEnabled = false)!!
        assertEquals(0f, session.progress)
        session.acknowledgeIntroduction(0)
        assertEquals(1f / 6, session.progress)
    }

    @Test
    fun halfwayIsOneHalf() {
        val states = learned("あ", due = 0).filterKeys { it.skill != Skill.LISTENING }
        val session = StudySession.review(states, strokes, now = 0)!!
        session.commit(session.evaluate(correctAnswer(session.current as Task.Question)), 0)
        assertEquals(0.5f, session.progress)
    }
}

class GraderScoreTest {
    private val grader = HandwritingGrader(strokes)
    private val drawable = Hiragana.all.filter { strokes.canDraw(it) }

    private fun wobbly(strokes: List<Stroke>, seed: Int, wobble: Float): List<Stroke> {
        val r = Random(seed)
        return strokes.map { s -> s.map { Point(it.x + (r.nextFloat() - 0.5f) * wobble, it.y + (r.nextFloat() - 0.5f) * wobble) } }
    }

    /** score = 1 - distance / (2.5 × pass), so the pass mark sits exactly at 0.6. */
    @Test
    fun passingDrawingsScoreAtLeastPointSixAndSameCountFailuresBelow() {
        var passes = 0
        var fails = 0
        drawable.forEachIndexed { i, k ->
            for (wobble in listOf(2f, 8f, 16f, 30f)) {
                val r = grader.grade(wobbly(strokes.strokesFor(k)!!, i, wobble), k, emptyList())
                if (r.correct) {
                    passes++
                    assertTrue(r.score >= 0.6f, "${k.char} wobble $wobble passed with score ${r.score}")
                } else if (r.drawnStrokes == r.expectedStrokes) {
                    fails++
                    assertTrue(r.score < 0.6f, "${k.char} wobble $wobble failed with score ${r.score}")
                }
            }
        }
        assertTrue(passes > 50 && fails > 20, "both sides exercised: $passes passes, $fails fails")
    }

    @Test
    fun wobblyStrokesInTheWrongOrderAreAnOrderProblem() {
        for (c in listOf("け", "さ", "い", "こ", "た")) {
            val ref = strokes.strokesFor(kana(c))!!
            val swapped = listOf(ref[1], ref[0]) + ref.drop(2)
            repeat(5) { seed ->
                val r = grader.grade(wobbly(swapped, seed, 6f), kana(c), drawable)
                assertEquals(DrawingProblem.STROKE_ORDER, r.problem, "$c seed $seed")
            }
        }
    }
}

class CardStateFieldsTest {
    @Test
    fun introducedAtIsKeptThroughReviews() {
        val s = Scheduler()
        val card = s.introduce(CardId("a", Skill.READING), now = 1_234)
        assertEquals(1_234, card.introducedAt)
        assertEquals(1_234, s.onWrong(s.onCorrect(card, 5_000), 9_000).introducedAt)
    }
}

class ReviewMixTest {
    /** Strong reading cards are typed about 60% of the time in Review, the rest are multiple choice. */
    @Test
    fun strongReadingCardsAreMostlyTyped() {
        val states = learned(*Hiragana.all.map { it.char }.toTypedArray(), level = 5, due = 0).filterKeys { it.skill == Skill.READING }
        var typed = 0
        var total = 0
        repeat(5) { seed ->
            val session = StudySession.review(states, strokes, now = 0, limit = 200, random = Random(seed))!!
            runSession(session) { q -> total++; if (q.type == QuestionType.READING_TYPING) typed++; correctAnswer(q) }
        }
        val ratio = typed.toFloat() / total
        assertTrue(ratio in 0.5f..0.7f, "typed ratio $ratio")
    }
}

class LearnMixUpWithOldKanaTest {
    /** Mixing a new kana up with one learned earlier resets the old card once; its retest must not "graduate" it again. */
    @Test
    fun anOldKanaRetestedInALearnSessionKeepsItsSchedule() {
        val vowels = learned("あ", "い", "う", "え", "お", level = 6, due = 1_000 * Scheduler.DAY)
        val session = StudySession.learn(vowels, strokes, drawingEnabled = false, random = Random(0))!!
        val a = kana("あ")
        var mixed = false
        val steps = runSession(session, now = 50) { q ->
            if (!mixed && q.type == QuestionType.READING_CHOICE) { mixed = true; Answer.Choice(a) } else correctAnswer(q)
        }
        assertTrue(mixed)
        assertTrue(steps.questions().any { it.kana == a }, "あ is retested in the session")
        val aCards = steps.flatMap { it.update.cards }.filter { it.id.kanaId == "a" }
        assertEquals(listOf(CardId("a", Skill.READING)), aCards.map { it.id }, "only the mixed-up skill is reset, once")
        assertEquals(4, aCards.single().level)
        assertTrue(a !in session.summary.learned)
        assertEquals(listOf("ka", "ki", "ku", "ke", "ko"), session.summary.learned.map { it.id })
    }
}
