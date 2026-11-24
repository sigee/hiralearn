package hu.sigee.hiralearn.core

import hu.sigee.hiralearn.core.Fixtures.kana
import hu.sigee.hiralearn.core.Fixtures.learned
import hu.sigee.hiralearn.core.Fixtures.strokes
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Words asked for writing and listening, like kana cards. */
class WordSkillsTest {
    private val everything = learned(*Hiragana.all.map { it.char }.toTypedArray())

    private fun start(
        states: Map<CardId, CardState> = everything,
        types: QuestionTypes = QuestionTypes(),
        limit: Int = WordSession.DEFAULT_LIMIT,
        seed: Int = 0,
    ) = WordSession.start(states, strokes, types, limit, random = Random(seed), newEventId = Fixtures.counterIds())!!

    /** Every question of a session, answered with [answer] (right by default), in order. */
    private fun questions(session: WordSession, answer: (WordQuestion) -> WordAnswer = ::rightWordAnswer): List<WordQuestion> = buildList {
        while (!session.isFinished) {
            check(size < 500) { "session does not terminate" }
            val q = session.current!!
            add(q)
            session.commit(session.evaluate(answer(q)), now = 100)
        }
    }

    private fun typesAsked(types: QuestionTypes, states: Map<CardId, CardState> = everything) =
        (0 until 8).flatMap { seed -> questions(start(states, types, limit = 200, seed = seed)).map { it.type } }.toSet()

    private fun hasRareSpelling(word: Word) = word.kana.any { it.char == "ぢ" || it.char == "づ" || it.char == "を" }

    @Test
    fun everyQuestionTypeOfTheKanaIsAskedForWords() {
        assertEquals(QuestionType.entries.toSet(), typesAsked(QuestionTypes()))
    }

    @Test
    fun theSettingsDecideTheTypes() {
        assertEquals(
            setOf(QuestionType.READING_CHOICE, QuestionType.READING_TYPING, QuestionType.WRITING_CHOICE, QuestionType.WRITING_DRAWING),
            typesAsked(QuestionTypes(listening = false)),
        )
        assertEquals(
            setOf(QuestionType.READING_CHOICE, QuestionType.READING_TYPING, QuestionType.WRITING_CHOICE, QuestionType.LISTENING_CHOICE),
            typesAsked(QuestionTypes(drawing = false)),
        )
        // Without choice, writing and listening are drawn, except words that can't be (yōon; ぢ/づ/を when heard).
        val noChoice = (0 until 8).flatMap { seed -> questions(start(types = QuestionTypes(choice = false), limit = 200, seed = seed)) }
        noChoice.filter { it.type == QuestionType.WRITING_CHOICE }.forEach { q ->
            assertTrue(q.word.kana.any { it.group == KanaGroup.YOON }, q.word.text)
        }
        noChoice.filter { it.type == QuestionType.LISTENING_CHOICE }.forEach { q ->
            assertTrue(q.word.kana.any { it.group == KanaGroup.YOON } || hasRareSpelling(q.word), q.word.text)
        }
        assertTrue(noChoice.none { it.type == QuestionType.READING_CHOICE })
        // Typed and drawn questions offer nothing to pick from.
        assertTrue(noChoice.filter { it.type.name.endsWith("CHOICE").not() }.all { it.options.isEmpty() })
        assertTrue(noChoice.any { it.type == QuestionType.WRITING_DRAWING } && noChoice.any { it.type == QuestionType.LISTENING_DRAWING })
    }

    @Test
    fun aWordIsOnlyAskedForASkillAllItsKanaHaveCardsFor() {
        // The K-row has no writing or listening cards yet.
        val states = learned("あ", "い", "う", "え", "お") + readingOnly("か", "き", "く", "け", "こ")
        val asked = (0 until 8).flatMap { seed -> questions(start(states, limit = 100, seed = seed)) }
        asked.filter { it.type.skill != Skill.READING }.forEach { q -> assertTrue(q.word.kana.none { it.lesson == 1 }, "${q.word.text} ${q.type}") }
        assertTrue(asked.any { it.type.skill == Skill.WRITING } && asked.any { it.type.skill == Skill.LISTENING })
    }

    @Test
    fun theSkillsTakeTurns() {
        repeat(5) { seed ->
            val asked = questions(start(limit = 9, seed = seed)).map { it.type.skill }
            assertEquals(9, asked.size)
            assertEquals(Skill.entries.associateWith { 3 }, asked.groupingBy { it }.eachCount())
            assertTrue(asked.zipWithNext().none { (a, b) -> a == b }, asked.toString())
        }
    }

    @Test
    fun heardWordsWithARareSpellingAreNeverDrawn() {
        val heard = (0 until 20).flatMap { seed ->
            questions(start(types = QuestionTypes(choice = false), limit = 200, seed = seed)).filter { it.type.skill == Skill.LISTENING }
        }
        val drawn = heard.filter { it.type == QuestionType.LISTENING_DRAWING }.map { it.word }
        assertTrue(drawn.any { w -> w.kana.any { it.char == "ず" || it.char == "じ" || it.char == "お" } }, "the usual spelling can be drawn")
        assertTrue(drawn.none(::hasRareSpelling), drawn.map { it.text }.toString())
        assertTrue(heard.any { hasRareSpelling(it.word) }, "rare spellings are still heard, as choice")
    }

    @Test
    fun spellingOptionsShowTheWordWithOneKanaSwapped() {
        repeat(5) { seed ->
            questions(start(types = QuestionTypes(typing = false, drawing = false), limit = 200, seed = seed)).forEach { q ->
                assertEquals(StudySession.OPTION_COUNT, q.options.size, q.word.text)
                val right = q.options.single { it.misread == null }
                assertEquals(q.word.text, right.text)
                assertEquals(q.word.romaji, right.romaji)
                // No two options sound the same, so a heard word has exactly one right spelling.
                assertEquals(q.options.size, q.options.map { it.romaji }.toSet().size)
                q.options.mapNotNull { it.misread }.forEach { m ->
                    val option = q.options.single { it.misread == m }
                    assertEquals(q.word.kana.toMutableList().also { it[m.position] = m.readAs }, option.kana)
                    assertEquals(option.kana.joinToString("") { it.char }, option.text)
                }
            }
        }
    }

    @Test
    fun aKanaDrawnLikeAnotherResetsTheirWritingCards() {
        val states = everything.mapValues { it.value.copy(level = 5, dueAt = 9_999) }
        val ne = kana("ね")
        val re = kana("れ")
        val session = (0 until 500).asSequence().map { seed -> start(states, QuestionTypes(choice = false, listening = false), 200, seed) }
            .first { s -> s.current!!.type == QuestionType.WRITING_DRAWING && ne in s.current!!.word.kana && re !in s.current!!.word.kana }
        val q = session.current!!
        val position = q.word.kana.indexOf(ne)
        val drawn = q.word.kana.mapIndexed { i, k -> strokes.strokesFor(if (i == position) re else k)!! }
        val evaluation = session.evaluate(WordAnswer.Drawn(drawn))
        assertFalse(evaluation.correct)
        assertEquals(Misread(position, re), evaluation.misread)
        assertEquals(ne, evaluation.misreadKana)
        assertEquals(q.word.kana.indices.map { it != position }, evaluation.drawings!!.map { it.correct })

        val update = session.commit(evaluation, now = 1_000)
        val scheduler = Scheduler()
        assertEquals(
            listOf(
                scheduler.onWrong(states.getValue(CardId(ne.id, Skill.WRITING)), 1_000),
                scheduler.onWrong(states.getValue(CardId(re.id, Skill.WRITING)), 1_000),
            ),
            update.cards,
        )
        assertEquals(
            listOf(ReviewEvent("e0", ne.id, Skill.WRITING, QuestionType.WRITING_DRAWING, false, re.id, SessionMode.WORDS, 1_000)),
            update.events,
        )
        // The word comes back 4th; words with ね and with れ come back 6th and 8th, to be written again.
        val upcoming = buildList {
            repeat(8) {
                add(session.current!!)
                session.commit(session.evaluate(rightWordAnswer(session.current!!)), 1_000)
            }
        }
        val texts = upcoming.map { it.word.text }.toString()
        assertEquals(q.word, upcoming[3].word)
        assertTrue(ne in upcoming[5].word.kana && upcoming[5].type.skill == Skill.WRITING, texts)
        assertTrue(re in upcoming[7].word.kana && upcoming[7].type.skill == Skill.WRITING, texts)
        assertEquals(listOf(ne, re), session.summary.troublesome)
    }

    @Test
    fun aRightDrawingIsRightAndABlankOneIsWrong() {
        val session = (0 until 100).asSequence().map { seed -> start(types = QuestionTypes(choice = false, listening = false), seed = seed) }
            .first { it.current!!.type == QuestionType.WRITING_DRAWING }
        val q = session.current!!
        val evaluation = session.evaluate(rightWordAnswer(q))
        assertTrue(evaluation.correct)
        assertNull(evaluation.misread)
        assertTrue(evaluation.drawings!!.all { it.correct })
        assertFailsWith<IllegalArgumentException> { session.evaluate(WordAnswer.Drawn(listOf(emptyList()))) }
        val blank = session.evaluate(WordAnswer.Drawn(q.word.kana.map { emptyList() }))
        assertFalse(blank.correct)
        assertNull(blank.misread)
        assertEquals(SessionUpdate(), session.commit(blank, now = 1))
    }

    @Test
    fun aPickedSpellingMixUpResetsTheListeningCards() {
        val session = (0 until 100).asSequence().map { seed -> start(types = QuestionTypes(drawing = false), seed = seed) }
            .first { it.current!!.type == QuestionType.LISTENING_CHOICE }
        val q = session.current!!
        val wrong = q.options.first { it.misread != null }
        val m = wrong.misread!!
        val update = session.commit(session.evaluate(WordAnswer.Choice(wrong)), now = 5)
        assertEquals(listOf(CardId(q.word.kana[m.position].id, Skill.LISTENING), CardId(m.readAs.id, Skill.LISTENING)), update.cards.map { it.id })
        assertEquals(QuestionType.LISTENING_CHOICE, update.events.single().questionType)
        assertEquals(Skill.LISTENING, update.events.single().skill)
    }

    @Test
    fun cantDrawTurnsEveryDrawingIntoChoice() {
        val session = (0 until 100).asSequence().map { seed -> start(types = QuestionTypes(choice = false), limit = 30, seed = seed) }
            .first { it.current!!.type == QuestionType.WRITING_DRAWING }
        assertTrue(session.canSkipDrawing)
        val first = session.current!!
        session.disableDrawing()
        assertFalse(session.canSkipDrawing)
        assertEquals(first.word, session.current!!.word)
        assertEquals(QuestionType.WRITING_CHOICE, session.current!!.type)
        val asked = questions(session)
        assertTrue(asked.none { it.type == QuestionType.WRITING_DRAWING || it.type == QuestionType.LISTENING_DRAWING })
        assertTrue(asked.any { it.type == QuestionType.LISTENING_CHOICE })
        assertFalse(start(types = QuestionTypes(drawing = false)).canSkipDrawing)
    }
}
