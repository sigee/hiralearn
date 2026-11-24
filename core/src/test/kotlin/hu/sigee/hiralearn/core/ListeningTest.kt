package hu.sigee.hiralearn.core

import hu.sigee.hiralearn.core.Fixtures.card
import hu.sigee.hiralearn.core.Fixtures.cards
import hu.sigee.hiralearn.core.Fixtures.correctAnswer
import hu.sigee.hiralearn.core.Fixtures.events
import hu.sigee.hiralearn.core.Fixtures.kana
import hu.sigee.hiralearn.core.Fixtures.learned
import hu.sigee.hiralearn.core.Fixtures.questions
import hu.sigee.hiralearn.core.Fixtures.runSession
import hu.sigee.hiralearn.core.Fixtures.strokes
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The listening skill: hear a kana, then pick or write it. */
class ListeningTest {
    private val later = 1_000 * Scheduler.DAY
    private val listeningOnly = Skill.entries.toSet() - Skill.READING - Skill.WRITING

    private fun Map<CardId, CardState>.only(skill: Skill) = filterKeys { it.skill == skill }

    @Test
    fun questionTypesNameTheirSkill() {
        assertEquals(Skill.LISTENING, QuestionType.LISTENING_CHOICE.skill)
        assertEquals(Skill.LISTENING, QuestionType.LISTENING_DRAWING.skill)
        assertTrue(QuestionTypes().listening, "listening is on by default")
        assertFalse(QuestionTypes(listening = false).listening)
        assertEquals(Skill.entries.toSet(), QuestionTypes().skills)
        assertEquals(setOf(Skill.READING, Skill.WRITING), QuestionTypes(listening = false).skills)
    }

    @Test
    fun soundAlikesAreTheKanaSharingAReading() {
        assertEquals(listOf(kana("ぢ")), Hiragana.soundAlikesOf(kana("じ")))
        assertEquals(listOf(kana("ず")), Hiragana.soundAlikesOf(kana("づ")))
        assertEquals(listOf(kana("を")), Hiragana.soundAlikesOf(kana("お")))
        assertTrue(Hiragana.soundAlikesOf(kana("あ")).isEmpty())
    }

    // ---- learn ----------------------------------------------------------------------------------------------

    @Test
    fun learnWithoutListeningNeitherAsksNorCreatesListeningCards() {
        val session = StudySession.learn(emptyMap(), strokes, listeningEnabled = false, batchSize = 1)!!
        val steps = runSession(session)
        assertTrue(steps.questions().none { it.type.skill == Skill.LISTENING })
        assertEquals(setOf(Skill.READING, Skill.WRITING), steps.cards().map { it.id.skill }.toSet())
        assertEquals(2, steps.cards().size)
    }

    @Test
    fun learnAsksListeningByChoiceAndCreatesItsCard() {
        val session = StudySession.learn(emptyMap(), strokes, batchSize = 1, random = Random(0))!!
        val steps = runSession(session)
        val listening = steps.questions().filter { it.type.skill == Skill.LISTENING }
        assertEquals(listOf(QuestionType.LISTENING_CHOICE), listening.map { it.type })
        assertEquals(StudySession.OPTION_COUNT, listening.single().options.size)
        assertTrue(CardId("a", Skill.LISTENING) in steps.cards().map { it.id })
    }

    // ---- review and practice -------------------------------------------------------------------------------

    @Test
    fun listeningOffLeavesListeningCardsAlone() {
        val states = learned("あ", "い", due = 0)
        assertEquals(6, StudySession.dueCards(states, now = 0).size)
        assertEquals(4, StudySession.dueCards(states, now = 0, QuestionTypes(listening = false).skills).size)
        assertEquals(2, StudySession.dueCards(states, now = 0, listeningOnly).size)
        val review = StudySession.review(states, strokes, now = 0, types = QuestionTypes(listening = false))!!
        assertTrue(runSession(review).questions().none { it.type.skill == Skill.LISTENING })
        assertNull(StudySession.review(states.only(Skill.LISTENING), strokes, now = 0, types = QuestionTypes(listening = false)))

        val notDue = learned("あ", due = later)
        assertEquals(3, StudySession.practiceCards(notDue, now = 0).size)
        assertEquals(listOf(Skill.LISTENING), StudySession.practiceCards(notDue, now = 0, listeningOnly).map { it.id.skill })
        assertNull(StudySession.practice(notDue.only(Skill.LISTENING), strokes, now = 0, types = QuestionTypes(listening = false)))
    }

    @Test
    fun invalidTypesKeepTheListeningChoice() {
        val states = learned("あ", due = 0).only(Skill.LISTENING)
        assertNull(StudySession.review(states, strokes, now = 0, types = QuestionTypes(false, false, false, listening = false)))
        assertTrue(StudySession.review(states, strokes, now = 0, types = QuestionTypes(false, false, false)) != null)
    }

    @Test
    fun reviewWritesWhatYouHearOnlyForWellKnownCards() {
        fun typesAt(level: Int) = (0 until 10).flatMap { seed ->
            val states = learned("あ", "い", "う", level = level, due = 0).only(Skill.LISTENING)
            runSession(StudySession.review(states, strokes, now = 0, random = Random(seed))!!).questions().map { it.type }
        }.toSet()
        assertEquals(setOf(QuestionType.LISTENING_CHOICE), typesAt(2))
        assertEquals(setOf(QuestionType.LISTENING_DRAWING), typesAt(3))
    }

    @Test
    fun listeningDrawingIsUnguidedWithAReference() {
        val session = StudySession.review(learned("か", level = 5, due = 0).only(Skill.LISTENING), strokes, now = 0)!!
        val q = session.current as Task.Question
        assertEquals(QuestionType.LISTENING_DRAWING, q.type)
        assertFalse(q.guided)
        assertEquals(strokes.strokesFor(kana("か")), q.reference)
        assertTrue(q.options.isEmpty())
    }

    @Test
    fun soundAlikesAndYoonAreNeverWrittenFromSound() {
        val chars = arrayOf("じ", "ぢ", "ず", "づ", "お", "を", "きゃ")
        repeat(10) { seed ->
            val states = learned(*chars, level = 6, due = 0).only(Skill.LISTENING)
            val types = runSession(StudySession.review(states, strokes, now = 0, types = QuestionTypes(choice = false), random = Random(seed))!!)
                .questions().map { it.type }.toSet()
            assertEquals(setOf(QuestionType.LISTENING_CHOICE), types, "seed $seed")
        }
    }

    @Test
    fun listeningChoicesNeverSoundAlike() {
        val states = learned(*Hiragana.all.map { it.char }.toTypedArray(), due = 0).only(Skill.LISTENING)
        repeat(10) { seed ->
            runSession(StudySession.review(states, strokes, now = 0, limit = 200, random = Random(seed))!!) { q ->
                q.options.forEach { a -> if (a != q.kana) assertFalse(a.soundsLike(q.kana), "${q.kana.char}: ${a.char}") }
                correctAnswer(q)
            }
        }
    }

    @Test
    fun disablingDrawingTurnsListeningDrawingIntoChoice() {
        val session = StudySession.review(learned("か", "き", level = 5, due = 0).only(Skill.LISTENING), strokes, now = 0)!!
        assertEquals(QuestionType.LISTENING_DRAWING, (session.current as Task.Question).type)
        session.disableDrawing()
        assertEquals(listOf(QuestionType.LISTENING_CHOICE, QuestionType.LISTENING_CHOICE), runSession(session).questions().map { it.type })
    }

    // ---- mix-ups --------------------------------------------------------------------------------------------

    @Test
    fun aListeningMixUpResetsOnlyTheOtherListeningCard() {
        val states = learned("ぬ", level = 3, due = 0).only(Skill.LISTENING) + learned("め", level = 6, due = later)
        val session = StudySession.review(states, strokes, now = 5, types = QuestionTypes(drawing = false), random = Random(0))!!
        val q = session.current as Task.Question
        assertEquals(QuestionType.LISTENING_CHOICE, q.type)
        val update = session.commit(session.evaluate(Answer.Choice(kana("め"))), now = 5)
        assertEquals(setOf(CardId("nu", Skill.LISTENING), CardId("me", Skill.LISTENING)), update.cards.map { it.id }.toSet())
        assertEquals("me", update.events.single().confusedWithKanaId)
        assertEquals(Skill.LISTENING, update.events.single().skill)
        val rest = runSession(session, now = 5).questions()
        assertTrue(rest.any { it.kana == kana("め") && it.type == QuestionType.LISTENING_CHOICE }, "め is asked again by sound")
        assertTrue(rest.any { it.kana == kana("ぬ") }, "ぬ is asked again")
    }

    @Test
    fun aWrittenFromSoundMixUpRetestsByDrawingUnlessTheOtherHasASoundAlike() {
        fun retestOf(drawn: String): Task.Question {
            val states = learned("あ", level = 5, due = 0).only(Skill.LISTENING) + learned(drawn, level = 6, due = later)
            val session = StudySession.review(states, strokes, now = 0, types = QuestionTypes(choice = false), random = Random(0))!!
            assertEquals(QuestionType.LISTENING_DRAWING, (session.current as Task.Question).type)
            val evaluation = session.evaluate(Answer.Drawn(strokes.strokesFor(kana(drawn))!!))
            assertEquals(kana(drawn), evaluation.confusedWith)
            session.commit(evaluation, 0)
            return runSession(session).questions().first { it.kana == kana(drawn) }
        }
        assertEquals(QuestionType.LISTENING_DRAWING, retestOf("め").type)
        // お sounds like を, so it can't be asked by drawing from sound.
        assertEquals(QuestionType.LISTENING_CHOICE, retestOf("お").type)
    }

    @Test
    fun practiceRightListeningAnswersDontMoveTheCard() {
        val states = learned("か", level = 4, due = later).only(Skill.LISTENING)
        val steps = runSession(StudySession.practice(states, strokes, now = 0)!!)
        assertTrue(steps.cards().isEmpty())
        assertEquals(SessionMode.PRACTICE, steps.events().single().mode)
    }

    // ---- listening cards for kana learned before ------------------------------------------------------------

    @Test
    fun missingListeningCardsAreAddedForLearnedKanaOnly() {
        val states = learned("あ", "い", level = 6).filterKeys { it.skill != Skill.LISTENING } +
            listOf(card("う", Skill.WRITING, level = 2), card("え", Skill.READING, level = 1), card("お", Skill.LISTENING, level = 4))
                .associateBy { it.id }
        val added = StudySession.missingListeningCards(states, now = 1_000)
        assertEquals(listOf("a", "i", "u", "e"), added.map { it.id.kanaId }, "catalogue order; お already has one")
        assertTrue(added.all { it.id.skill == Skill.LISTENING })
        assertEquals(listOf(4, 4, 1, 1), added.map { it.level }, "two below reading, at least 1")
        assertEquals(listOf(997L, 998L, 999L, 1_000L), added.map { it.dueAt }, "all due, oldest kana first")
        assertTrue(added.all { it.introducedAt == 1_000L && it.updatedAt == 1_000L && it.lastReviewedAt == null })
        assertTrue(added.all { it.timesCorrect == 0 && it.timesWrong == 0 && it.lapses == 0 })
        assertTrue(StudySession.missingListeningCards(states + added.associateBy { it.id }, now = 2_000).isEmpty())
        assertTrue(StudySession.missingListeningCards(emptyMap(), now = 0).isEmpty())
    }

    @Test
    fun aKanaLearnedInThisSessionWithoutListeningGetsItsCardLater() {
        val steps = runSession(StudySession.learn(emptyMap(), strokes, listeningEnabled = false, batchSize = 1)!!, now = 50)
        val states = steps.cards().associateBy { it.id }
        val added = StudySession.missingListeningCards(states, now = 60)
        assertEquals(listOf(CardId("a", Skill.LISTENING)), added.map { it.id })
        assertEquals(1, added.single().level)
    }
}
