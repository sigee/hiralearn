package hu.sigee.hiralearn.core

import hu.sigee.hiralearn.core.Fixtures.card
import hu.sigee.hiralearn.core.Fixtures.learned
import hu.sigee.hiralearn.core.Fixtures.strokes
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Practising words picked in the glossary. */
class ChosenWordsTest {
    private val everything = learned(*Hiragana.all.map { it.char }.toTypedArray())
    private fun word(id: String) = Words.byId(id)!!

    private fun start(words: List<Word>, states: Map<CardId, CardState> = everything, types: QuestionTypes = QuestionTypes(), seed: Int = 0) =
        WordSession.startWith(words, states, strokes, types, random = Random(seed), newEventId = Fixtures.counterIds())

    private fun drive(session: WordSession, answer: (WordQuestion) -> WordAnswer = ::rightWordAnswer): List<Pair<WordQuestion, SessionUpdate>> =
        buildList {
            while (!session.isFinished) {
                check(size < 100) { "session does not terminate" }
                val q = session.current!!
                add(q to session.commit(session.evaluate(answer(q)), now = 100))
            }
        }

    @Test
    fun aWordIsAsStrongAsItsWeakestKana() {
        val states = learned("ね", level = 6) + learned("こ", level = 2)
        assertEquals(2, Words.readingLevel(word("neko"), states))
        assertEquals(6, Words.readingLevel(word("neko"), learned("ね", "こ", level = 6)))
        assertNull(Words.readingLevel(word("inu"), states))
    }

    @Test
    fun aSingleWordIsAskedOnceInEverySkill() {
        val asked = drive(start(listOf(word("neko")))!!).map { it.first }
        assertEquals(setOf("neko"), asked.map { it.word.id }.toSet())
        assertEquals(Skill.entries.sorted(), asked.map { it.type.skill }.sorted())
    }

    @Test
    fun onlySkillsThatAreOnAndHaveCardsAreAsked() {
        val noListening = drive(start(listOf(word("neko")), types = QuestionTypes(listening = false))!!).map { it.first.type.skill }
        assertEquals(listOf(Skill.READING, Skill.WRITING), noListening.sorted())

        val readingOnly = listOf("ね", "こ").map { card(it, Skill.READING) }.associateBy { it.id }
        val asked = drive(start(listOf(word("neko")), readingOnly)!!).map { it.first.type.skill }
        assertEquals(listOf(Skill.READING), asked)
    }

    @Test
    fun severalWordsAreAllAskedAndDuplicatesOnce() {
        val words = listOf(word("neko"), word("inu"), word("neko"))
        val asked = drive(start(words, types = QuestionTypes(listening = false))!!).map { it.first }
        assertEquals(4, asked.size)
        assertEquals(setOf("neko", "inu"), asked.map { it.word.id }.toSet())
    }

    @Test
    fun aMissedWordComesBackEvenWithOtherWordsOfTheSameSkillQueued() {
        val readingOnly = listOf("ね", "こ", "い", "ぬ").map { card(it, Skill.READING) }.associateBy { it.id }
        val session = start(listOf(word("neko"), word("inu")), readingOnly)!!
        val missed = session.current!!.word
        session.commit(session.evaluate(WordAnswer.DontKnow), now = 100)
        assertTrue(drive(session).any { it.first.word == missed })
    }

    @Test
    fun wordsThatCantBeReadAreLeftOut() {
        val states = learned("ね", "こ")
        assertNull(start(listOf(word("inu")), states))
        assertNull(start(emptyList()))
        val asked = drive(start(listOf(word("inu"), word("neko")), states)!!).map { it.first.word.id }.toSet()
        assertEquals(setOf("neko"), asked)
    }

    @Test
    fun rightAnswersMoveNoCardAndAMixUpResetsBothKana() {
        assertTrue(drive(start(listOf(word("neko")))!!).all { it.second.cards.isEmpty() && it.second.events.isEmpty() })

        val session = start(listOf(word("neko")), types = QuestionTypes(typing = false, drawing = false))!!
        val q = session.current!!
        val wrong = q.options.first { it.misread != null }
        val update = session.commit(session.evaluate(WordAnswer.Choice(wrong)), now = 100)
        val reset = update.cards.map { it.id.kanaId }.toSet()
        assertEquals(setOf(q.word.kana[wrong.misread!!.position].id, wrong.misread.readAs.id), reset)
        // The missed word is asked again in the session.
        val later = drive(session).map { it.first }
        assertNotNull(later.firstOrNull { it.word.id == "neko" && it.type == q.type })
    }
}
