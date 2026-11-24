package hu.sigee.hiralearn.core

import hu.sigee.hiralearn.core.Fixtures.cards
import hu.sigee.hiralearn.core.Fixtures.counterIds
import hu.sigee.hiralearn.core.Fixtures.events
import hu.sigee.hiralearn.core.Fixtures.kana
import hu.sigee.hiralearn.core.Fixtures.learned
import hu.sigee.hiralearn.core.Fixtures.questions
import hu.sigee.hiralearn.core.Fixtures.runSession
import hu.sigee.hiralearn.core.Fixtures.strokes
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfusionsTest {
    private val now = 100 * Scheduler.DAY
    private val hl = Confusions.HALF_LIFE

    private fun mix(asked: String, answered: String, at: Long = now) = MixUp(kana(asked).id, kana(answered).id, at)

    // ---- ranking --------------------------------------------------------------------------------------------

    @Test
    fun `both directions count as one pair, ordered by the catalogue`() {
        val pairs = Confusions.rank(listOf(mix("お", "あ"), mix("あ", "お", at = now - 1)), now)
        assertEquals(1, pairs.size)
        val p = pairs.single()
        assertEquals(kana("あ"), p.first)
        assertEquals(kana("お"), p.second)
        assertEquals(listOf(kana("あ"), kana("お")), p.kana)
        assertEquals(2, p.count)
        assertEquals(now, p.lastAt)
        assertEquals(2.0, p.score, 1e-3)
    }

    @Test
    fun `the most confused pair comes first`() {
        val pairs = Confusions.rank(listOf(mix("さ", "ち"), mix("あ", "お"), mix("ち", "さ")), now)
        assertEquals(listOf(kana("さ") to kana("ち"), kana("あ") to kana("お")), pairs.map { it.first to it.second })
    }

    @Test
    fun `equal scores put the more recent pair first`() {
        // Two half-weight mix-ups of あ/お score exactly as much as one fresh mix-up of さ/ち.
        val old = listOf(mix("あ", "お", now - hl), mix("お", "あ", now - hl))
        val fresh = listOf(mix("さ", "ち"))
        for (input in listOf(old + fresh, fresh + old)) {
            val pairs = Confusions.rank(input, now)
            assertEquals(listOf(kana("さ"), kana("あ")), pairs.map { it.first })
            assertEquals(pairs[0].score, pairs[1].score, 1e-9)
        }
    }

    @Test
    fun `old mix-ups weigh less than recent ones`() {
        // Two mix-ups six weeks ago (2 * 1/8) lose against one from today.
        val pairs = Confusions.rank(listOf(mix("さ", "ち", now - 3 * hl), mix("さ", "ち", now - 3 * hl), mix("あ", "お")), now)
        assertEquals(kana("あ"), pairs.first().first)
        assertEquals(2, pairs.size)
    }

    @Test
    fun `a pair fades out once its weight drops below the minimum`() {
        assertEquals(1, Confusions.rank(listOf(mix("あ", "お", now - 2 * hl)), now).size)
        assertEquals(0, Confusions.rank(listOf(mix("あ", "お", now - 2 * hl - Scheduler.DAY)), now).size)
        assertEquals(0.25, Confusions.rank(listOf(mix("あ", "お", now - 2 * hl)), now).single().score, 1e-9)
    }

    @Test
    fun `mix-ups from the future count as now`() {
        assertEquals(1.0, Confusions.rank(listOf(mix("あ", "お", now + hl)), now).single().score, 1e-9)
    }

    @Test
    fun `same-reading pairs, self mix-ups and unknown ids are ignored`() {
        val pairs = Confusions.rank(
            listOf(mix("じ", "ぢ"), mix("あ", "あ"), MixUp("nope", kana("あ").id, now), MixUp(kana("あ").id, "nope", now)),
            now,
        )
        assertTrue(pairs.isEmpty())
    }

    @Test
    fun `only the top pairs are returned`() {
        val many = listOf("あお", "さち", "ぬめ", "われ", "はほ", "るろ").map { mix(it.substring(0, 1), it.substring(1)) }
        assertEquals(Confusions.TOP_PAIRS, Confusions.rank(many, now).size)
        assertEquals(2, Confusions.rank(many, now, limit = 2).size)
    }

    // ---- clearing by drill ----------------------------------------------------------------------------------

    private fun drilled(chars: String, at: Long, skills: List<Skill> = Confusions.DRILLED_SKILLS.toList()) =
        chars.map { kana(it.toString()).id }.flatMap { id -> skills.map { DrillAnswer(id, it, at) } }

    @Test
    fun `a clean drill of both kana in both skills clears the pair`() {
        val mixUps = listOf(mix("あ", "お", now - 2), mix("さ", "ち", now - 2))
        val pairs = Confusions.rank(mixUps, now, drilled("あお", now - 1))
        assertEquals(listOf(kana("さ")), pairs.map { it.first })
    }

    @Test
    fun `a partial drill does not clear the pair`() {
        val mixUps = listOf(mix("あ", "お", now - 2))
        assertEquals(1, Confusions.rank(mixUps, now, drilled("あ", now - 1)).size)
        assertEquals(1, Confusions.rank(mixUps, now, drilled("あお", now - 1, listOf(Skill.READING))).size)
        // Listening answers are not part of a drill and never complete one.
        assertEquals(1, Confusions.rank(mixUps, now, drilled("あお", now - 1, listOf(Skill.READING, Skill.LISTENING))).size)
        // Answers about other kana don't help either.
        assertEquals(1, Confusions.rank(mixUps, now, drilled("あさ", now - 1) + drilled("ちお", now - 1, listOf(Skill.READING))).size)
    }

    @Test
    fun `drill answers before the mix-up, or at the same moment, don't clear it`() {
        assertEquals(1, Confusions.rank(listOf(mix("あ", "お", now - 1)), now, drilled("あお", now - 2)).size)
        assertEquals(1, Confusions.rank(listOf(mix("あ", "お", now - 1)), now, drilled("あお", now - 1)).size)
    }

    @Test
    fun `a mix-up during the drill restarts it, and only later mix-ups count once the pair returns`() {
        // Half the drill, then a mix-up, then the other half: not clean.
        val half = drilled("あ", now - 8) + drilled("お", now - 6)
        val interrupted = Confusions.rank(listOf(mix("あ", "お", now - 9), mix("お", "あ", now - 7)), now, half)
        assertEquals(2, interrupted.single().count)

        // Cleared at now - 5, mixed up again at now - 3: only the new mix-up counts.
        val again = Confusions.rank(listOf(mix("あ", "お", now - 9), mix("あ", "お", now - 8), mix("お", "あ", now - 3)), now, drilled("あお", now - 5))
        assertEquals(1, again.single().count)
        assertEquals(now - 3, again.single().lastAt)
    }

    // ---- drill ----------------------------------------------------------------------------------------------

    private val ao = ConfusionPair(kana("あ"), kana("お"), 3, now, 3.0)
    private val sachi = ConfusionPair(kana("さ"), kana("ち"), 2, now, 2.0)

    @Test
    fun `no pairs, no drill`() {
        assertNull(StudySession.drill(learned("あ"), strokes, emptyList()))
    }

    @Test
    fun `each kana of a pair is asked for reading and writing with only the pair as options`() {
        val session = StudySession.drill(learned("あ", "お", "さ", "ち"), strokes, listOf(ao, sachi), random = Random(1))!!
        assertEquals(SessionMode.DRILL, session.mode)
        assertEquals(false, session.canSkipDrawing)
        val questions = runSession(session).questions()
        assertEquals(8, questions.size)
        val asked = questions.map { it.kana.char to it.type }.toSet()
        for (c in listOf("あ", "お", "さ", "ち")) {
            assertTrue(c to QuestionType.READING_CHOICE in asked)
            assertTrue(c to QuestionType.WRITING_CHOICE in asked)
        }
        questions.forEach { q ->
            val pair = if (q.kana in ao.kana) ao.kana else sachi.kana
            assertEquals(pair.toSet(), q.options.toSet())
            assertEquals(2, q.options.size)
        }
        // Writing round first, then reading.
        assertTrue(questions.take(4).all { it.type == QuestionType.WRITING_CHOICE })
    }

    @Test
    fun `the same kana is never asked twice in a row`() {
        for (seed in 0 until 40) {
            for (pairs in listOf(listOf(ao), listOf(ao, sachi))) {
                val questions = runSession(StudySession.drill(learned("あ", "お", "さ", "ち"), strokes, pairs, random = Random(seed))!!).questions()
                assertTrue(questions.zipWithNext().none { (a, b) -> a.kana == b.kana }, "seed $seed: ${questions.map { it.kana.char }}")
            }
        }
    }

    @Test
    fun `right answers in a drill don't move cards`() {
        val steps = runSession(StudySession.drill(learned("あ", "お"), strokes, listOf(ao), newEventId = counterIds())!!)
        assertTrue(steps.cards().isEmpty())
        assertEquals(4, steps.events().size)
        assertTrue(steps.events().all { it.mode == SessionMode.DRILL && it.correct })
    }

    @Test
    fun `a mix-up in a drill resets both cards and re-asks both as the same contrast`() {
        val states = learned("あ", "お", level = 5, due = now + 30 * Scheduler.DAY)
        val session = StudySession.drill(states, strokes, listOf(ao), random = Random(3))!!
        val first = session.current as Task.Question
        val other = first.options.single { it != first.kana }
        val update = session.commit(session.evaluate(Answer.Choice(other)), now)

        assertEquals(other.id, update.events.single().confusedWithKanaId)
        assertEquals(setOf(first.kana.id, other.id), update.cards.map { it.id.kanaId }.toSet())
        assertTrue(update.cards.all { it.level < 5 && it.id.skill == first.type.skill })

        // The asked kana comes back (the other one is already queued for the same skill), still as a two-option contrast.
        val rest = runSession(session).questions()
        assertEquals(4, rest.size)
        assertTrue(rest.all { it.options.toSet() == ao.kana.toSet() })
        assertEquals(1, rest.count { it.type == first.type && it.kana == first.kana })
    }

    @Test
    fun `a mixed-up kana's retest is also a contrast of the same pair`() {
        // Answer the second question about the partner wrong after its own same-skill question is gone, so the
        // partner's retest is really added.
        val session = StudySession.drill(learned("あ", "お"), strokes, listOf(ao), random = Random(5))!!
        val asked = mutableListOf<Task.Question>()
        var wrongDone = false
        while (!session.isFinished) {
            val q = session.current as Task.Question
            val partner = q.options.single { it != q.kana }
            val partnerAlreadyAsked = asked.any { it.kana == partner && it.type == q.type }
            val answer = if (!wrongDone && partnerAlreadyAsked) Answer.Choice(partner).also { wrongDone = true } else Answer.Choice(q.kana)
            asked += q
            session.commit(session.evaluate(answer), now)
        }
        assertTrue(wrongDone)
        assertEquals(6, asked.size)
        assertTrue(asked.all { it.options.size == 2 && it.options.toSet() == ao.kana.toSet() })
    }
}
