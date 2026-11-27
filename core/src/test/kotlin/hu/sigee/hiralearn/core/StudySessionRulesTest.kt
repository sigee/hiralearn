package hu.sigee.hiralearn.core

import hu.sigee.hiralearn.core.Fixtures.card
import hu.sigee.hiralearn.core.Fixtures.cards
import hu.sigee.hiralearn.core.Fixtures.correctAnswer
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Edge cases and API contracts of [StudySession]. */
class StudySessionRulesTest {
    private val later = 1_000 * Scheduler.DAY
    private val noDrawing = QuestionTypes(drawing = false)

    // ---- static helpers -------------------------------------------------------------------------------------

    @Test
    fun typedAnswersAreNormalised() {
        assertEquals("shi", StudySession.normalizeTyped("  SHI "))
        assertEquals("nn", StudySession.normalizeTyped("n'n"))
        assertEquals("kya", StudySession.normalizeTyped("k-y a"))
        assertEquals("", StudySession.normalizeTyped(" \t"))
    }

    @Test
    fun learnedMeansAnyCardExists() {
        val a = kana("あ")
        assertFalse(StudySession.isLearned(a, emptyMap()))
        assertTrue(StudySession.isLearned(a, mapOf(CardId("a", Skill.WRITING) to card("あ", Skill.WRITING))))
        assertTrue(StudySession.isLearned(a, mapOf(CardId("a", Skill.READING) to card("あ", Skill.READING))))
        assertFalse(StudySession.isLearned(a, learned("い")))
    }

    @Test
    fun nextNewKanaFinishesALessonBeforeMovingOn() {
        assertEquals(listOf("あ", "い", "う", "え", "お"), StudySession.nextNewKana(emptyMap()).map { it.char })
        assertEquals(listOf("い", "え"), StudySession.nextNewKana(learned("あ", "う", "お")).map { it.char })
        assertEquals(listOf("あ", "い"), StudySession.nextNewKana(emptyMap(), batchSize = 2).map { it.char })
        // A gap in an earlier lesson is filled first, even if later lessons were learned.
        assertEquals(listOf("い"), StudySession.nextNewKana(learned("あ", "う", "え", "お", "か")).map { it.char })
        val everything = learned(*Hiragana.all.map { it.char }.toTypedArray())
        assertEquals(emptyList(), StudySession.nextNewKana(everything))
        assertNull(StudySession.learn(everything, strokes))
    }

    @Test
    fun dueCardsAreOldestFirstAndIncludeNow() {
        val states = listOf(card("あ", Skill.READING, due = 30), card("い", Skill.READING, due = 10), card("う", Skill.READING, due = 31))
            .associateBy { it.id }
        assertEquals(listOf("i", "a"), StudySession.dueCards(states, now = 30).map { it.id.kanaId })
        assertEquals(emptyList(), StudySession.dueCards(states, now = 9))
    }

    @Test
    fun practiceCardsAreNotDueSortedByLevelThenDueTime() {
        val states = listOf(
            card("あ", Skill.READING, level = 2, due = 500),
            card("い", Skill.READING, level = 2, due = 200),
            card("う", Skill.READING, level = 1, due = 900),
            card("え", Skill.READING, level = 1, due = 100), // due: belongs to Review
        ).associateBy { it.id }
        assertEquals(listOf("u", "i", "a"), StudySession.practiceCards(states, now = 100).map { it.id.kanaId })
    }

    @Test
    fun emptySessionsAreNull() {
        assertNull(StudySession.review(emptyMap(), strokes, now = 0))
        assertNull(StudySession.review(learned("あ", due = later), strokes, now = 0))
        assertNull(StudySession.practice(emptyMap(), strokes, now = 0))
    }

    @Test
    fun reviewRespectsTheLimitAndTakesTheMostOverdue() {
        val states = (learned("あ", due = 5) + learned("い", due = 1) + learned("う", due = 3))
        val session = StudySession.review(states, strokes, now = 10, limit = 3, random = Random(0))!!
        val asked = runSession(session, now = 10).questions().map { it.kana.char to it.type.skill }.toSet()
        assertEquals(3, asked.size)
        assertFalse(asked.any { it.first == "あ" }, "あ is the least overdue: $asked")
    }

    @Test
    fun practiceRespectsTheLimit() {
        val states = learned("あ", "い", "う", due = later)
        val session = StudySession.practice(states, strokes, now = 0, limit = 2, random = Random(0))!!
        assertEquals(2, runSession(session).questions().size)
    }

    // ---- step protocol --------------------------------------------------------------------------------------

    @Test
    fun theProtocolRejectsOutOfOrderCalls() {
        val session = StudySession.learn(emptyMap(), strokes, random = Random(1))!!
        assertTrue(session.current is Task.Introduce)
        assertFailsWith<IllegalStateException> { session.evaluate(Answer.DontKnow) }
        session.acknowledgeIntroduction(0)
        while (session.current is Task.Introduce) session.acknowledgeIntroduction(0)
        assertFailsWith<IllegalArgumentException> { session.acknowledgeIntroduction(0) }

        // An evaluation from an earlier question can't be committed later.
        val stale = session.evaluate(correctAnswer(session.current as Task.Question))
        session.commit(stale, 0)
        while (session.current is Task.Introduce) session.acknowledgeIntroduction(0)
        assertFailsWith<IllegalArgumentException> { session.commit(stale, 0) }
    }

    @Test
    fun aFinishedSessionHasNoTaskAndRejectsEverything() {
        val session = StudySession.review(learned("あ").filterKeys { it.skill == Skill.READING }, strokes, now = 0)!!
        val eval = session.evaluate(correctAnswer(session.current as Task.Question))
        session.commit(eval, 0)
        assertTrue(session.isFinished)
        assertNull(session.current)
        assertEquals(1f, session.progress)
        assertFailsWith<IllegalStateException> { session.evaluate(Answer.DontKnow) }
        assertFailsWith<IllegalArgumentException> { session.commit(eval, 0) }
        assertFailsWith<IllegalArgumentException> { session.acknowledgeIntroduction(0) }
    }

    @Test
    fun currentIsStableUntilTheStepIsDone() {
        val session = StudySession.review(learned("あ", "い", "う", due = 0), strokes, now = 0, random = Random(3))!!
        val q = session.current
        assertSame(q, session.current, "same task object, so its shuffled options don't change on recomposition")
        session.evaluate(Answer.DontKnow)
        assertSame(q, session.current, "evaluate is pure")
    }

    @Test
    fun introductionCarriesStrokesAndChangesNothing() {
        val session = StudySession.learn(emptyMap(), strokes, random = Random(1))!!
        val intro = session.current as Task.Introduce
        assertEquals(strokes.strokesFor(kana("あ")), intro.strokes)
        assertEquals(SessionUpdate(), session.acknowledgeIntroduction(0))
    }

    @Test
    fun progressGrowsToOneAndRetestsExtendTheSession() {
        val session = StudySession.review(learned("か", "き", due = 0), strokes, now = 0, types = noDrawing, random = Random(2))!!
        assertEquals(0f, session.progress)
        val progress = mutableListOf<Float>()
        var failed = false
        runSession(session) { q ->
            progress += session.progress
            if (!failed) { failed = true; Answer.DontKnow } else correctAnswer(q)
        }
        assertEquals(7, progress.size, "6 primary questions + 1 retest")
        progress.zipWithNext().forEach { (a, b) -> assertTrue(b > a) }
        assertEquals(1f, session.progress)
    }

    @Test
    fun eventsCarryEverythingForSync() {
        val session = StudySession.review(
            learned("あ", due = 0), strokes, now = 77, types = noDrawing, random = Random(1), newEventId = counterIds(),
        )!!
        val steps = runSession(session, now = 77)
        val events = steps.events()
        assertEquals(listOf("e0", "e1", "e2"), events.map { it.id })
        events.zip(steps.questions()).forEach { (e, q) ->
            assertEquals(q.kana.id, e.kanaId)
            assertEquals(q.type, e.questionType)
            assertEquals(q.type.skill, e.skill)
            assertEquals(SessionMode.REVIEW, e.mode)
            assertEquals(77, e.answeredAt)
            assertTrue(e.correct)
            assertNull(e.confusedWithKanaId)
        }
    }

    @Test
    fun defaultEventIdsAreUuids() {
        val session = StudySession.review(learned("あ", due = 0), strokes, now = 0)!!
        val ids = runSession(session).events().map { it.id }
        ids.forEach { assertTrue(it.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")), it) }
        assertEquals(ids.size, ids.toSet().size)
    }

    // ---- grading --------------------------------------------------------------------------------------------

    @Test
    fun choiceEvaluation() {
        val session = StudySession.review(learned("あ", "お", due = 0), strokes, now = 0, types = QuestionTypes(typing = false, drawing = false))!!
        val q = session.current as Task.Question
        val other = q.options.first { it != q.kana }
        assertEquals(Evaluation(q, Answer.Choice(q.kana), true, null), session.evaluate(Answer.Choice(q.kana)))
        assertEquals(Evaluation(q, Answer.Choice(other), false, other), session.evaluate(Answer.Choice(other)))
        assertEquals(Evaluation(q, Answer.DontKnow, false, null), session.evaluate(Answer.DontKnow))
    }

    @Test
    fun typedEvaluationOnlyBlamesTaughtKana() {
        // Only さ is known; typing "chi" for it is wrong but ち was never taught, so no mix-up.
        val session = StudySession.review(learned("さ", due = 0).filterKeys { it.skill == Skill.READING }, strokes, now = 0)!!
        val wrong = session.evaluate(Answer.Typed("chi"))
        assertFalse(wrong.correct)
        assertNull(wrong.confusedWith)
        assertTrue(session.evaluate(Answer.Typed(" Sa ")).correct)
        assertNull(session.evaluate(Answer.Typed("sa")).confusedWith)
    }

    @Test
    fun drawnEvaluationReturnsTheGraderResult() {
        val states = learned("あ", "お", level = 5, due = 0).filterKeys { it.skill == Skill.WRITING && it.kanaId == "a" } +
            learned("お", level = 5, due = later)
        val session = StudySession.review(states, strokes, now = 0, types = QuestionTypes(choice = false))!!
        val q = session.current as Task.Question
        assertEquals(QuestionType.WRITING_DRAWING, q.type)
        assertFalse(q.guided, "review drawing is from memory")
        assertEquals(strokes.strokesFor(kana("あ")), q.reference)
        val good = session.evaluate(Answer.Drawn(q.reference!!))
        assertTrue(good.correct)
        assertNotNull(good.drawing)
        val mixed = session.evaluate(Answer.Drawn(strokes.strokesFor(kana("お"))!!))
        assertFalse(mixed.correct)
        assertEquals(kana("お"), mixed.confusedWith)
        assertEquals(kana("お"), mixed.drawing!!.lookedLike)
    }

    @Test
    fun choosingTheAnswerItselfIsNeverAMixUpEvenIfMarkedWrong() {
        val session = StudySession.review(learned("あ", due = 0).filterKeys { it.skill == Skill.READING }, strokes, now = 0, types = QuestionTypes(typing = false))!!
        val q = session.current as Task.Question
        // A caller-built evaluation claiming a mix-up with the target itself.
        val update = session.commit(Evaluation(q, Answer.DontKnow, correct = false, confusedWith = q.kana), 0)
        assertNull(update.events.single().confusedWithKanaId)
        assertEquals(1, update.cards.size)
    }

    // ---- scheduling rules -----------------------------------------------------------------------------------

    @Test
    fun acceptAsCorrectDropsTheMixUp() {
        val states = learned("あ", "お", level = 4, due = 0).filterKeys { it.skill == Skill.READING }
        val session = StudySession.review(states, strokes, now = 0, types = QuestionTypes(typing = false), random = Random(1))!!
        val q = session.current as Task.Question
        val other = q.options.first { it != q.kana && it in listOf(kana("あ"), kana("お")) }
        val update = session.commit(session.evaluate(Answer.Choice(other)), now = 5, acceptAsCorrect = true)
        assertEquals(listOf(5), update.cards.map { it.level })
        assertTrue(update.events.single().correct)
        assertNull(update.events.single().confusedWithKanaId)
        assertEquals(1, session.summary.correct)
        assertTrue(session.summary.troublesome.isEmpty())
    }

    @Test
    fun aWrongAnswerIsRetestedAtMostThreeTimes() {
        val session = StudySession.review(learned("か", due = 0).filterKeys { it.skill == Skill.READING }, strokes, now = 0, types = noDrawing)!!
        val steps = runSession(session) { Answer.DontKnow }
        assertEquals(4, steps.size, "1 primary + 3 retests")
        assertEquals(1, steps.cards().size, "only the first answer moves the schedule")
        assertEquals(SessionSummary(SessionMode.REVIEW, 4, 0, emptyList(), listOf(kana("か"))), session.summary)
    }

    @Test
    fun aRetestComesThreeQuestionsLater() {
        val states = learned("あ", "い", "う", "え", "お", due = 0).filterKeys { it.skill == Skill.READING }
        val session = StudySession.review(states, strokes, now = 0, types = noDrawing, random = Random(9))!!
        var failed: Kana? = null
        val order = runSession(session) { q ->
            if (failed == null) { failed = q.kana; Answer.DontKnow } else correctAnswer(q)
        }.questions().map { it.kana }
        assertEquals(6, order.size)
        assertEquals(failed, order[0])
        assertEquals(failed, order[4], "re-asked after three other questions: $order")
    }

    @Test
    fun aMixedUpKanaThatIsNeitherLearnedNorSeenIsNotScheduled() {
        // Only あ is learned; the learner picks an untaught distractor.
        val states = learned("あ", due = 0).filterKeys { it.skill == Skill.READING }
        val session = StudySession.review(states, strokes, now = 0, types = QuestionTypes(typing = false), random = Random(2))!!
        val q = session.current as Task.Question
        val stranger = q.options.first { it != q.kana }
        val steps = runSession(session) { if (it === q) Answer.Choice(stranger) else correctAnswer(it) }
        assertEquals(listOf(kana("あ")), steps.questions().map { it.kana }.distinct(), "the untaught kana is not asked")
        assertEquals(stranger.id, steps.events().first().confusedWithKanaId)
        assertEquals(setOf(CardId("a", Skill.READING)), steps.cards().map { it.id }.toSet())
        assertTrue(stranger in session.summary.troublesome)
    }

    @Test
    fun aMixUpOnlyResetsTheSameSkillOfTheOtherKana() {
        val states = learned("あ", level = 3, due = 0).filterKeys { it.skill == Skill.READING } + learned("お", level = 6, due = later)
        val session = StudySession.review(states, strokes, now = 0, types = QuestionTypes(typing = false), random = Random(4))!!
        val steps = runSession(session) { q -> if (q.kana.char == "あ" && q.type.skill == Skill.READING) Answer.Choice(kana("お")) else correctAnswer(q) }
        val changed = steps.cards().map { it.id }.toSet()
        assertTrue(CardId("o", Skill.READING) in changed)
        assertFalse(CardId("o", Skill.WRITING) in changed, "writing お wasn't involved")
    }

    @Test
    fun aCardIsRescheduledOnlyOncePerSession() {
        // お is mixed up twice (from あ's question and again from its retest): its card is reset once.
        val states = learned("あ", level = 3, due = 0).filterKeys { it.skill == Skill.READING } +
            learned("お", level = 6, due = later).filterKeys { it.skill == Skill.READING }
        val session = StudySession.review(states, strokes, now = 0, types = QuestionTypes(typing = false), random = Random(4))!!
        val steps = runSession(session) { q -> if (q.kana.char == "あ") Answer.Choice(kana("お")) else correctAnswer(q) }
        assertEquals(2, steps.cards().size, "あ once, お once: ${steps.cards()}")
        assertEquals(4, steps.cards().single { it.kanaId() == "o" }.level)
    }

    private fun CardState.kanaId() = id.kanaId

    @Test
    fun reviewOfAStrongCardMovesItUp() {
        val states = learned("な", level = 7, due = 0).filterKeys { it.skill == Skill.READING }
        val update = runSession(StudySession.review(states, strokes, now = 1_000)!!, now = 1_000).cards().single()
        assertEquals(8, update.level)
        assertEquals(1_000 + 60 * Scheduler.DAY, update.dueAt)
        assertEquals(1_000, update.updatedAt)
    }

    // ---- learn sessions -------------------------------------------------------------------------------------

    @Test
    fun learnIntroducesEachKanaBeforeTestingIt() {
        repeat(5) { seed ->
            val session = StudySession.learn(emptyMap(), strokes, random = Random(seed))!!
            val seen = mutableSetOf<Kana>()
            while (!session.isFinished) {
                when (val t = session.current!!) {
                    is Task.Introduce -> { seen += t.kana; session.acknowledgeIntroduction(0) }
                    is Task.Question -> {
                        assertTrue(t.kana in seen, "seed $seed: ${t.kana.char} tested before its introduction")
                        session.commit(session.evaluate(correctAnswer(t)), 0)
                    }
                }
            }
        }
    }

    @Test
    fun learnLadderPerKana() {
        val session = StudySession.learn(emptyMap(), strokes, batchSize = 1, random = Random(0))!!
        val steps = runSession(session)
        assertTrue(steps.first().task is Task.Introduce)
        val ladder = steps.questions().map { it.type to it.guided }
        assertEquals(
            listOf(
                QuestionType.READING_CHOICE to false,
                QuestionType.WRITING_CHOICE to false,
                QuestionType.WRITING_DRAWING to true,
                QuestionType.LISTENING_CHOICE to false,
                QuestionType.WRITING_DRAWING to true,
                QuestionType.READING_TYPING to false,
                QuestionType.WRITING_DRAWING to false,
            ),
            ladder,
        )
        assertEquals(listOf(kana("あ")), session.summary.learned)
    }

    @Test
    fun learnWithoutDrawingUsesChoiceAndSkipsTracing() {
        val session = StudySession.learn(emptyMap(), strokes, drawingEnabled = false, batchSize = 1)!!
        assertFalse(session.canSkipDrawing)
        val types = runSession(session).questions().map { it.type }
        assertEquals(
            listOf(
                QuestionType.READING_CHOICE, QuestionType.WRITING_CHOICE, QuestionType.LISTENING_CHOICE, QuestionType.READING_TYPING,
                QuestionType.WRITING_CHOICE,
            ),
            types,
        )
    }

    @Test
    fun yoonAreNeverDrawn() {
        val allBasicAndDakuten = learned(*Hiragana.all.filter { it.group != KanaGroup.YOON }.map { it.char }.toTypedArray(), due = later)
        val session = StudySession.learn(allBasicAndDakuten, strokes, random = Random(0))!!
        val questions = runSession(session).questions()
        assertTrue(questions.all { it.kana.group == KanaGroup.YOON })
        assertTrue(questions.none { it.type == QuestionType.WRITING_DRAWING })
        // Review with choice off still falls back to choice for yōon writing.
        val review = StudySession.review(learned("きゃ", level = 5, due = 0), strokes, now = 0, types = QuestionTypes(choice = false))!!
        val types = runSession(review).questions().map { it.type }.toSet()
        assertEquals(setOf(QuestionType.READING_TYPING, QuestionType.WRITING_CHOICE, QuestionType.LISTENING_CHOICE), types)
    }

    @Test
    fun learnMistakesDelayGraduationAndDontCreateCardsEarly() {
        val session = StudySession.learn(emptyMap(), strokes, batchSize = 1, drawingEnabled = false)!!
        var failures = 0
        val steps = runSession(session) { q -> if (failures < 2) { failures++; Answer.DontKnow } else correctAnswer(q) }
        val cardSteps = steps.filter { it.update.cards.isNotEmpty() }
        assertEquals(1, cardSteps.size, "cards are created once, at graduation")
        assertSame(steps.last(), cardSteps.single(), "graduation happens on the very last step")
        assertEquals(listOf(kana("あ")), steps.last().update.graduated)
        assertEquals(2, session.summary.answered - session.summary.correct)
        assertEquals(listOf(kana("あ")), session.summary.troublesome)
    }

    @Test
    fun learnSkipsAlreadyLearnedKanaInTheBatch() {
        val session = StudySession.learn(learned("い"), strokes, random = Random(0))!!
        val taught = runSession(session).mapNotNull { (it.task as? Task.Introduce)?.kana?.char }
        assertEquals(listOf("あ", "う", "え", "お"), taught)
    }

    @Test
    fun learnMixUpWithAKanaIntroducedThisSessionIsRecorded() {
        val session = StudySession.learn(emptyMap(), strokes, drawingEnabled = false, random = Random(5))!!
        val introduced = mutableSetOf<Kana>()
        var mixed: Pair<Kana, Kana>? = null
        val cards = mutableListOf<CardState>()
        val events = mutableListOf<ReviewEvent>()
        while (!session.isFinished) {
            when (val q = session.current!!) {
                is Task.Introduce -> { introduced += q.kana; session.acknowledgeIntroduction(0) }
                is Task.Question -> {
                    val other = q.options.firstOrNull { it != q.kana && it in introduced }
                    val answer = if (mixed == null && q.type == QuestionType.READING_CHOICE && other != null) {
                        mixed = q.kana to other
                        Answer.Choice(other)
                    } else correctAnswer(q)
                    val update = session.commit(session.evaluate(answer), 0)
                    cards += update.cards
                    events += update.events
                }
            }
        }
        val (asked, confused) = assertNotNull(mixed)
        assertEquals(confused.id, events.single { !it.correct }.confusedWithKanaId)
        assertEquals(listOf(asked, confused), session.summary.troublesome)
        // Everything still graduates exactly once with fresh level-1 cards: a new kana has no schedule to reset.
        assertEquals(15, cards.size)
        assertTrue(cards.all { it.level == 1 })
    }

    // ---- disabling drawing ----------------------------------------------------------------------------------

    @Test
    fun disablingDrawingConvertsCurrentTraceAndDropsLaterTraces() {
        val session = StudySession.learn(emptyMap(), strokes, batchSize = 1, random = Random(0))!!
        assertTrue(session.canSkipDrawing)
        // Advance to the trace step.
        while (!((session.current as? Task.Question)?.guided ?: false)) {
            when (val t = session.current!!) {
                is Task.Introduce -> session.acknowledgeIntroduction(0)
                is Task.Question -> session.commit(session.evaluate(correctAnswer(t)), 0)
            }
        }
        session.disableDrawing()
        assertFalse(session.canSkipDrawing)
        assertEquals(QuestionType.WRITING_CHOICE, (session.current as Task.Question).type, "the current trace becomes choice")
        val rest = runSession(session).questions().map { it.type }
        assertEquals(listOf(QuestionType.WRITING_CHOICE, QuestionType.LISTENING_CHOICE, QuestionType.READING_TYPING, QuestionType.WRITING_CHOICE), rest)
    }

    @Test
    fun laterTracesAreDroppedNotConverted() {
        val session = StudySession.learn(emptyMap(), strokes, batchSize = 1, random = Random(0))!!
        session.acknowledgeIntroduction(0)
        session.disableDrawing()
        val types = runSession(session).questions().map { it.type }
        val expected = listOf(
            QuestionType.READING_CHOICE, QuestionType.WRITING_CHOICE, QuestionType.LISTENING_CHOICE, QuestionType.READING_TYPING,
            QuestionType.WRITING_CHOICE,
        )
        assertEquals(expected, types)
    }

    @Test
    fun drawingRetestsFallBackToChoiceAfterDisabling() {
        val states = learned("あ", level = 5, due = 0).filterKeys { it.skill == Skill.WRITING } + learned("お", level = 6, due = later)
        val session = StudySession.review(states, strokes, now = 0, types = QuestionTypes(choice = false), random = Random(0))!!
        val q = session.current as Task.Question
        assertEquals(QuestionType.WRITING_DRAWING, q.type)
        session.commit(session.evaluate(Answer.Drawn(strokes.strokesFor(kana("お"))!!)), 0)
        session.disableDrawing()
        val rest = runSession(session).questions()
        assertTrue(rest.isNotEmpty())
        assertTrue(rest.all { it.type == QuestionType.WRITING_CHOICE }, rest.map { it.kana.char to it.type }.toString())
    }

    @Test
    fun aFailedDrawingIsTracedThenDrawnFromMemory() {
        val states = learned("あ", level = 5, due = 0).filterKeys { it.skill == Skill.WRITING }
        val session = StudySession.review(states, strokes, now = 0, types = QuestionTypes(choice = false), random = Random(0))!!
        session.commit(session.evaluate(Answer.DontKnow), 0)
        val rest = runSession(session).questions().map { Triple(it.kana, it.type, it.guided) }
        assertEquals(
            listOf(Triple(kana("あ"), QuestionType.WRITING_DRAWING, true), Triple(kana("あ"), QuestionType.WRITING_DRAWING, false)),
            rest,
        )
    }

    @Test
    fun theDrawingAfterTheTraceComesAFewStepsLater() {
        val states = learned("あ", "い", "う", "え", "お", "か", "き", level = 5, due = 0).filterKeys { it.skill == Skill.WRITING }
        val session = StudySession.review(states, strokes, now = 0, types = QuestionTypes(choice = false), random = Random(0))!!
        val failed = (session.current as Task.Question).kana
        session.commit(session.evaluate(Answer.DontKnow), 0)
        val rest = runSession(session).questions()
        val retests = rest.withIndex().filter { it.value.kana == failed }
        assertEquals(listOf(3 to true, 6 to false), retests.map { it.index to it.value.guided })
    }

    @Test
    fun aFailedLearnDrawingIsTracedBeforeTheKanaIsLearned() {
        val session = StudySession.learn(emptyMap(), strokes, batchSize = 1, random = Random(0))!!
        while (true) {
            val t = session.current!!
            if (t is Task.Question && t.type == QuestionType.WRITING_DRAWING && !t.guided) break
            if (t is Task.Introduce) session.acknowledgeIntroduction(0) else session.commit(session.evaluate(correctAnswer(t as Task.Question)), 0)
        }
        session.commit(session.evaluate(Answer.DontKnow), 0)
        assertTrue(session.summary.learned.isEmpty())
        val rest = runSession(session).questions().map { it.type to it.guided }
        assertEquals(listOf(QuestionType.WRITING_DRAWING to true, QuestionType.WRITING_DRAWING to false), rest)
        assertEquals(listOf(kana("あ")), session.summary.learned)
    }

    @Test
    fun aFailedListeningDrawingIsNotTraced() {
        val states = learned("あ", level = 5, due = 0).filterKeys { it.skill == Skill.LISTENING }
        val session = StudySession.review(states, strokes, now = 0, types = QuestionTypes(choice = false), random = Random(0))!!
        assertEquals(QuestionType.LISTENING_DRAWING, (session.current as Task.Question).type)
        session.commit(session.evaluate(Answer.DontKnow), 0)
        val rest = runSession(session).questions().map { it.type to it.guided }
        assertEquals(listOf(QuestionType.LISTENING_DRAWING to false), rest)
    }

    @Test
    fun aDrawingMixUpRetestsTheOtherKanaByDrawing() {
        val states = learned("あ", level = 5, due = 0).filterKeys { it.skill == Skill.WRITING } + learned("お", level = 6, due = later)
        val session = StudySession.review(states, strokes, now = 0, types = QuestionTypes(choice = false), random = Random(0))!!
        session.commit(session.evaluate(Answer.Drawn(strokes.strokesFor(kana("お"))!!)), 0)
        val rest = runSession(session).questions()
        val oRetest = rest.single { it.kana == kana("お") }
        assertEquals(QuestionType.WRITING_DRAWING, oRetest.type)
        assertFalse(oRetest.guided)
    }

    @Test
    fun invalidQuestionTypesFallBackToAll() {
        val states = learned("あ", "い", "う", "か", "き", level = 5, due = later)
        val seen = mutableSetOf<QuestionType>()
        repeat(10) { seed ->
            val s = StudySession.practice(states, strokes, now = 0, types = QuestionTypes(false, false, false), random = Random(seed))!!
            runSession(s) { q -> seen += q.type; correctAnswer(q) }
        }
        assertEquals(QuestionType.entries.toSet(), seen)
    }

    @Test
    fun reviewRampsUpWithLevel() {
        // Level 1 cards are always asked by choice in Review; strong ones get typing/drawing.
        fun typesAt(level: Int): Set<QuestionType> {
            val seen = mutableSetOf<QuestionType>()
            repeat(10) { seed ->
                val s = StudySession.review(learned("あ", "い", "う", "か", "き", level = level, due = 0), strokes, now = 0, random = Random(seed))!!
                runSession(s) { q -> seen += q.type; correctAnswer(q) }
            }
            return seen
        }
        assertEquals(setOf(QuestionType.READING_CHOICE, QuestionType.WRITING_CHOICE, QuestionType.LISTENING_CHOICE), typesAt(1))
        assertEquals(setOf(QuestionType.READING_CHOICE, QuestionType.WRITING_DRAWING, QuestionType.LISTENING_CHOICE), typesAt(2))
        assertEquals(
            setOf(QuestionType.READING_CHOICE, QuestionType.READING_TYPING, QuestionType.WRITING_DRAWING, QuestionType.LISTENING_DRAWING),
            typesAt(3),
        )
    }

    @Test
    fun repeatsOnlyHappenWhenNothingElseIsLeft() {
        repeat(30) { seed ->
            val s = StudySession.review(learned("あ", "い", "う", due = 0), strokes, now = 0, random = Random(seed))!!
            val order = runSession(s).questions().map { it.kana }
            order.zipWithNext().forEachIndexed { i, (a, b) ->
                if (a == b) assertTrue(order.drop(i).all { it == a }, "seed $seed: $order")
            }
        }
    }
}
