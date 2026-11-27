package hu.sigee.hiralearn.core

import hu.sigee.hiralearn.core.Fixtures.correctAnswer
import hu.sigee.hiralearn.core.Fixtures.learned
import hu.sigee.hiralearn.core.Fixtures.strokes
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** When the feedback after an answer moves on by itself (Settings → "Move on automatically"). */
class AutoAdvanceTest {
    private val later = 1_000 * Scheduler.DAY

    @Test
    fun rightAnswerWaitsThreeSecondsWhenTurnedOn() {
        assertEquals(3_000L, AutoAdvance.DELAY_MS)
        assertEquals(AutoAdvance.DELAY_MS, AutoAdvance.delayAfter(correct = true, enabled = true))
    }

    @Test
    fun rightAnswerWaitsForContinueWhenTurnedOff() {
        assertNull(AutoAdvance.delayAfter(correct = true, enabled = false))
    }

    @Test
    fun wrongAnswerAlwaysWaitsForContinue() {
        assertNull(AutoAdvance.delayAfter(correct = false, enabled = true))
        assertNull(AutoAdvance.delayAfter(correct = false, enabled = false))
    }

    /** Drawing tasks used to stop while the others moved on; now every question type behaves the same. */
    @Test
    fun everyKanaQuestionTypeBehavesAlike() {
        val seen = mutableSetOf<QuestionType>()
        repeat(20) { seed ->
            val states = learned("あ", "い", "う", "え", "お", "か", "き", "く", "け", "こ", due = later)
            val session = StudySession.practice(states, strokes, now = 0, random = Random(seed))!!
            var wrong = seed % 2 == 0
            while (!session.isFinished) {
                val q = session.current as Task.Question
                val evaluation = session.evaluate(if (wrong) Answer.DontKnow else correctAnswer(q))
                assertEquals(!wrong, evaluation.correct, "${q.type}")
                val expected = if (wrong) null else AutoAdvance.DELAY_MS
                assertEquals(expected, AutoAdvance.delayAfter(evaluation.correct, enabled = true), "${q.type}, right = ${!wrong}")
                assertNull(AutoAdvance.delayAfter(evaluation.correct, enabled = false), "${q.type}, turned off")
                seen += q.type
                session.commit(evaluation, 0)
                wrong = !wrong
            }
        }
        assertEquals(QuestionType.entries.toSet(), seen, "every question type was checked")
    }

    @Test
    fun everyWordQuestionTypeBehavesAlike() {
        val seen = mutableSetOf<QuestionType>()
        val states = learned("あ", "い", "う", "え", "お", "か", "き", "く", "け", "こ", "さ", "し", "す", "せ", "そ", "ね", "な")
        repeat(20) { seed ->
            val session = WordSession.start(states, strokes, limit = 50, random = Random(seed))!!
            var wrong = seed % 2 == 0
            while (!session.isFinished) {
                val q = session.current!!
                val evaluation = session.evaluate(if (wrong) WordAnswer.DontKnow else rightWordAnswer(q))
                assertEquals(!wrong, evaluation.correct, "${q.type} ${q.word.text}")
                val expected = if (wrong) null else AutoAdvance.DELAY_MS
                assertEquals(expected, AutoAdvance.delayAfter(evaluation.correct, enabled = true), "${q.type}, right = ${!wrong}")
                assertNull(AutoAdvance.delayAfter(evaluation.correct, enabled = false), "${q.type}, turned off")
                seen += q.type
                session.commit(evaluation, 0)
                wrong = !wrong
            }
        }
        assertEquals(QuestionType.entries.toSet(), seen, "every question type was checked")
    }
}
