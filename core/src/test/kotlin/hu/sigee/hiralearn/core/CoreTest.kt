package hu.sigee.hiralearn.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogTest {
    @Test
    fun catalogueIsComplete() {
        assertEquals(46, Hiragana.all.count { it.group == KanaGroup.BASIC })
        assertEquals(25, Hiragana.all.count { it.group == KanaGroup.DAKUTEN })
        assertEquals(33, Hiragana.all.count { it.group == KanaGroup.YOON })
        assertEquals("di", Hiragana.byChar("ぢ")!!.id)
        assertTrue(Hiragana.byChar("ぢ")!!.soundsLike(Hiragana.byChar("じ")!!))
        assertTrue("si" in Hiragana.byChar("し")!!.accepted)
    }

    @Test
    fun lookAlikesAreSymmetric() {
        val a = Hiragana.byChar("あ")!!
        val o = Hiragana.byChar("お")!!
        assertTrue(o in Hiragana.lookAlikesOf(a))
        assertTrue(a in Hiragana.lookAlikesOf(o))
        assertTrue(Hiragana.byChar("ぱ")!! in Hiragana.lookAlikesOf(Hiragana.byChar("ば")!!))
    }

    @Test
    fun everyDrawableKanaHasStrokes() {
        val lib = StrokeLibrary.loadBundled()
        val drawable = Hiragana.all.filter { it.group != KanaGroup.YOON }
        drawable.forEach { assertNotNull(lib.strokesFor(it), "no strokes for ${it.char}") }
        assertEquals(3, lib.strokesFor(Hiragana.byChar("あ")!!)!!.size)
        assertEquals(1, lib.strokesFor(Hiragana.byChar("の")!!)!!.size)
    }
}

class SchedulerTest {
    private val s = Scheduler()
    private val id = CardId("a", Skill.READING)

    @Test
    fun correctAnswersPushFurtherOut() {
        var card = s.introduce(id, now = 0)
        assertEquals(1, card.level)
        assertEquals(4 * Scheduler.HOUR, card.dueAt)
        card = s.onCorrect(card, now = 1000)
        assertEquals(2, card.level)
        assertEquals(1000 + 12 * Scheduler.HOUR, card.dueAt)
        repeat(20) { card = s.onCorrect(card, 0) }
        assertEquals(s.maxLevel, card.level)
    }

    @Test
    fun wrongAnswerComesBackSoonAndDropsLevel() {
        var card = s.introduce(id, 0)
        repeat(5) { card = s.onCorrect(card, 0) }
        assertEquals(6, card.level)
        card = s.onWrong(card, now = 5000)
        assertEquals(4, card.level)
        assertEquals(5000 + 10 * Scheduler.MINUTE, card.dueAt)
        assertEquals(1, card.lapses)
        card = s.onWrong(s.onWrong(card, 0), 0)
        assertEquals(1, card.level)
    }
}

class StudySessionTest {
    private val lib = StrokeLibrary.loadBundled()
    private val scheduler = Scheduler()

    private fun correctAnswer(q: Task.Question): Answer = when (q.type) {
        QuestionType.READING_CHOICE, QuestionType.WRITING_CHOICE, QuestionType.LISTENING_CHOICE -> Answer.Choice(q.kana)
        QuestionType.READING_TYPING -> Answer.Typed(q.kana.romaji.uppercase() + " ")
        QuestionType.WRITING_DRAWING, QuestionType.LISTENING_DRAWING -> Answer.Drawn(q.reference!!)
    }

    /** Runs the session answering via [answer]; returns all updates. */
    private fun run(session: StudySession, now: Long = 0, answer: (Task.Question) -> Answer = ::correctAnswer): List<SessionUpdate> {
        val updates = mutableListOf<SessionUpdate>()
        var guard = 0
        while (!session.isFinished) {
            check(guard++ < 500) { "session does not terminate" }
            when (val task = session.current!!) {
                is Task.Introduce -> updates += session.acknowledgeIntroduction(now)
                is Task.Question -> updates += session.commit(session.evaluate(answer(task)), now)
            }
        }
        return updates
    }

    private fun learned(vararg chars: String, level: Int = 3, due: Long = 0): Map<CardId, CardState> =
        chars.flatMap { c ->
            val k = Hiragana.byChar(c)!!
            Skill.entries.map { CardState(CardId(k.id, it), level, due, 0, null, 0, 0, 0, 0) }
        }.associateBy { it.id }

    @Test
    fun learnSessionTeachesFirstLessonAndSchedulesEverySkill() {
        val session = StudySession.learn(emptyMap(), lib, random = Random(1))!!
        val first = session.current
        assertTrue(first is Task.Introduce && first.kana.char == "あ")
        val updates = run(session, now = 100)
        val graduated = updates.flatMap { it.graduated }.map { it.char }
        assertEquals(listOf("あ", "い", "う", "え", "お").sorted(), graduated.sorted())
        val cards = updates.flatMap { it.cards }
        assertEquals(15, cards.size)
        assertTrue(cards.all { it.level == 1 && it.dueAt == 100 + 4 * Scheduler.HOUR })
        assertEquals(1f, session.progress)
        assertEquals(session.summary.answered, session.summary.correct)
    }

    @Test
    fun learnSessionUsesEveryQuestionType() {
        val session = StudySession.learn(emptyMap(), lib, random = Random(2))!!
        val types = mutableSetOf<QuestionType>()
        var sawGuided = false
        run(session) { q -> types += q.type; if (q.guided) sawGuided = true; correctAnswer(q) }
        // Writing what you hear is left to Review.
        assertEquals(QuestionType.entries.toSet() - QuestionType.LISTENING_DRAWING, types)
        assertTrue(sawGuided)
    }

    @Test
    fun nextLessonContinuesWhereLearnerIs() {
        val states = learned("あ", "い", "う", "え", "お")
        assertEquals(listOf("か", "き", "く", "け", "こ"), StudySession.nextNewKana(states).map { it.char })
    }

    @Test
    fun wrongAnswerIsAskedAgainInTheSession() {
        val states = learned("か", "き", due = 0)
        val session = StudySession.review(states, lib, now = 10, types = QuestionTypes(drawing = false), random = Random(3))!!
        var failedOnce = false
        var asked = 0
        val updates = run(session, now = 10) { q ->
            if (q.kana.char == "か") asked++
            if (!failedOnce && q.kana.char == "か" && q.type.skill == Skill.READING) {
                failedOnce = true
                Answer.DontKnow
            } else correctAnswer(q)
        }
        assertEquals(4, asked, "three primary questions (reading, writing, listening) + one retest")
        val ka = updates.flatMap { it.cards }.filter { it.id == CardId("ka", Skill.READING) }
        assertEquals(1, ka.size, "only the first answer moves the schedule")
        assertEquals(1, ka.single().level)
        assertEquals(10 + 10 * Scheduler.MINUTE, ka.single().dueAt)
    }

    @Test
    fun mixUpMarksTheOtherKanaAsNotKnown() {
        // あ is due; お was learned well and isn't due for a long time.
        val states = learned("あ", level = 2, due = 0) + learned("お", level = 6, due = 1_000 * Scheduler.DAY)
        val session = StudySession.review(states, lib, now = 10, types = QuestionTypes(drawing = false), random = Random(4))!!
        val o = Hiragana.byChar("お")!!
        var mixed = false
        val asked = mutableListOf<String>()
        val updates = run(session, now = 10) { q ->
            asked += q.kana.char + q.type.skill
            if (!mixed && q.kana.char == "あ" && q.type == QuestionType.READING_CHOICE) {
                assertTrue(o in q.options, "look-alike offered as distractor")
                mixed = true
                Answer.Choice(o)
            } else correctAnswer(q)
        }
        assertTrue(mixed)
        val oReading = updates.flatMap { it.cards }.single { it.id == CardId("o", Skill.READING) }
        assertEquals(4, oReading.level)
        assertEquals(10 + 10 * Scheduler.MINUTE, oReading.dueAt)
        assertTrue("お" + Skill.READING in asked, "お is asked in this session too")
        assertEquals("o", updates.flatMap { it.events }.first { !it.correct }.confusedWithKanaId)
        assertTrue(o in session.summary.troublesome)
    }

    @Test
    fun typedReadingOfAnotherKanaIsAMixUp() {
        val states = learned("さ", "ち", due = 0).filterKeys { it.skill == Skill.READING }
        val session = StudySession.review(states, lib, now = 0, random = Random(5))!!
        val q = session.current as Task.Question
        val other = if (q.kana.char == "さ") "chi" else "sa"
        val eval = session.evaluate(Answer.Typed(other))
        assertFalse(eval.correct)
        assertNotNull(eval.confusedWith)
        assertTrue(session.evaluate(Answer.Typed(q.kana.accepted.last())).correct)
    }

    @Test
    fun acceptingAsCorrectOverrulesTheGrader() {
        val states = learned("ね", due = 0).filterKeys { it.skill == Skill.READING }
        val session = StudySession.review(states, lib, now = 0)!!
        val eval = session.evaluate(Answer.Typed("nw"))
        assertFalse(eval.correct)
        val update = session.commit(eval, now = 0, acceptAsCorrect = true)
        assertEquals(4, update.cards.single().level)
        assertTrue(session.isFinished)
    }

    @Test
    fun choiceOptionsAreUnambiguous() {
        val states = learned(*Hiragana.all.map { it.char }.toTypedArray(), due = 0)
        repeat(30) { seed ->
            val session = StudySession.review(states, lib, now = 0, limit = 200, random = Random(seed))!!
            run(session) { q ->
                if (q.options.isNotEmpty()) {
                    assertEquals(StudySession.OPTION_COUNT, q.options.size)
                    assertTrue(q.kana in q.options)
                    q.options.forEach { a -> q.options.forEach { b -> if (a != b) assertFalse(a.soundsLike(b), "${a.char}/${b.char}") } }
                }
                correctAnswer(q)
            }
        }
    }

    @Test
    fun practiceAsksNotYetDueCardsWeakestFirst() {
        val later = 1_000 * Scheduler.DAY
        val states = learned("か", level = 5, due = later) + learned("き", level = 2, due = later) + learned("く", due = 0)
        val order = StudySession.practiceCards(states, now = 10).map { it.id.kanaId }
        assertEquals(listOf("ki", "ki", "ki", "ka", "ka", "ka"), order, "due く is left to Review; weaker き first")
        assertNull(StudySession.practice(learned("く", due = 0), lib, now = 10))
    }

    @Test
    fun practiceMixesAllQuestionTypesEvenForNewCards() {
        val later = 1_000 * Scheduler.DAY
        val states = learned("あ", "い", "う", "え", "お", level = 1, due = later)
        val types = mutableSetOf<QuestionType>()
        repeat(5) { seed ->
            run(StudySession.practice(states, lib, now = 10, random = Random(seed))!!, now = 10) { q -> types += q.type; correctAnswer(q) }
        }
        assertEquals(QuestionType.entries.toSet(), types)
    }

    @Test
    fun practiceRightAnswersDontChangeScheduleButMistakesDo() {
        val later = 1_000 * Scheduler.DAY
        val states = learned("あ", level = 2, due = later) + learned("お", level = 6, due = later)
        val session = StudySession.practice(states, lib, now = 10, types = QuestionTypes(typing = false, drawing = false), random = Random(7))!!
        val o = Hiragana.byChar("お")!!
        var mixed = false
        val updates = run(session, now = 10) { q ->
            if (!mixed && q.kana.char == "あ" && q.type == QuestionType.READING_CHOICE) {
                mixed = true
                Answer.Choice(o)
            } else correctAnswer(q)
        }
        assertTrue(mixed)
        val changed = updates.flatMap { it.cards }.map { it.id }.toSet()
        assertEquals(setOf(CardId("a", Skill.READING), CardId("o", Skill.READING)), changed)
        assertTrue(updates.flatMap { it.cards }.all { it.dueAt == 10 + 10 * Scheduler.MINUTE })
        assertTrue(updates.flatMap { it.events }.all { it.mode == SessionMode.PRACTICE })
    }

    @Test
    fun onlyEnabledQuestionTypesAreUsed() {
        val later = 1_000 * Scheduler.DAY
        val learnedAll = learned("あ", "い", "う", "か", "き", level = 5, due = later)
        fun typesFor(types: QuestionTypes, practice: Boolean): Set<QuestionType> {
            val seen = mutableSetOf<QuestionType>()
            repeat(10) { seed ->
                val states = if (practice) learnedAll else learnedAll.mapValues { it.value.copy(dueAt = 0) }
                val session = if (practice) StudySession.practice(states, lib, now = 10, types = types, random = Random(seed))!!
                else StudySession.review(states, lib, now = 10, types = types, random = Random(seed))!!
                run(session, now = 10) { q -> seen += q.type; correctAnswer(q) }
            }
            return seen
        }
        for (practice in listOf(true, false)) {
            assertEquals(
                setOf(QuestionType.READING_CHOICE, QuestionType.WRITING_CHOICE, QuestionType.LISTENING_CHOICE),
                typesFor(QuestionTypes(typing = false, drawing = false), practice),
            )
            assertEquals(
                setOf(QuestionType.READING_TYPING, QuestionType.WRITING_DRAWING, QuestionType.LISTENING_DRAWING),
                typesFor(QuestionTypes(choice = false), practice),
            )
            assertEquals(
                setOf(QuestionType.READING_TYPING, QuestionType.WRITING_DRAWING),
                typesFor(QuestionTypes(choice = false, listening = false), practice),
            )
        }
        assertFalse(QuestionTypes(choice = false, typing = false).isValid)
        assertFalse(QuestionTypes(choice = false, drawing = false).isValid)
    }

    @Test
    fun drawingCanBeSwitchedOff() {
        val session = StudySession.learn(emptyMap(), lib, random = Random(6))!!
        session.disableDrawing()
        run(session) { q -> assertTrue(q.type != QuestionType.WRITING_DRAWING); correctAnswer(q) }
    }
}

class HandwritingTest {
    private val lib = StrokeLibrary.loadBundled()
    private val grader = HandwritingGrader(lib)
    private val all = Hiragana.all.filter { lib.canDraw(it) }
    private fun k(c: String) = Hiragana.byChar(c)!!

    /** Simulates a learner: different size and position on the pad, with wobble. */
    private fun handwritten(strokes: List<Stroke>, seed: Int, wobble: Float = 2.5f): List<Stroke> {
        val r = Random(seed)
        val scale = 1.8f + r.nextFloat()
        return strokes.map { s ->
            s.map { Point(it.x * scale + 40 + (r.nextFloat() - 0.5f) * wobble * scale, it.y * scale + 15 + (r.nextFloat() - 0.5f) * wobble * scale) }
        }
    }

    @Test
    fun referenceAndNeatHandwritingPassForEveryKana() {
        all.forEachIndexed { i, kana ->
            val ref = lib.strokesFor(kana)!!
            assertTrue(grader.grade(ref, kana, all).correct, "reference ${kana.char}")
            val result = grader.grade(handwritten(ref, i), kana, all)
            assertTrue(result.correct, "handwritten ${kana.char}: $result")
        }
    }

    @Test
    fun drawingALookAlikeIsDetectedAsMixUp() {
        val result = grader.grade(lib.strokesFor(k("お"))!!, k("あ"), all)
        assertFalse(result.correct)
        assertEquals(k("お"), result.lookedLike)
    }

    @Test
    fun mostKanaAreNotAcceptedForEachOther() {
        var wronglyAccepted = 0
        all.forEach { target ->
            all.filter { it != target }.forEach { other ->
                if (grader.grade(lib.strokesFor(other)!!, target, emptyList()).correct) wronglyAccepted++
            }
        }
        // Only near-identical pairs (e.g. dakuten vs handakuten with the same base) may slip through.
        assertTrue(wronglyAccepted <= 6, "wrongly accepted pairs: $wronglyAccepted")
    }

    @Test
    fun wrongDirectionOrderAndCountAreReported() {
        val ke = k("け")
        val ref = lib.strokesFor(ke)!!
        assertEquals(DrawingProblem.STROKE_DIRECTION, grader.grade(ref.mapIndexed { i, s -> if (i == 0) s.reversed() else s }, ke, all).problem)
        assertEquals(DrawingProblem.STROKE_ORDER, grader.grade(listOf(ref[1], ref[0], ref[2]), ke, all).problem)
        val merged = listOf(ref[0], ref[1] + ref[2])
        val r = grader.grade(merged, ke, all)
        assertFalse(r.correct)
        assertEquals(2, r.drawnStrokes)
        assertEquals(3, r.expectedStrokes)
        assertNull(grader.grade(ref, ke, all).lookedLike)
    }
}
