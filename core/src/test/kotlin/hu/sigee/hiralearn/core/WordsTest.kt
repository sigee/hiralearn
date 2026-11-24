package hu.sigee.hiralearn.core

import hu.sigee.hiralearn.core.Fixtures.card
import hu.sigee.hiralearn.core.Fixtures.kana
import hu.sigee.hiralearn.core.Fixtures.learned
import hu.sigee.hiralearn.core.Fixtures.strokes
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordCatalogTest {
    private fun word(id: String) = Words.byId(id) ?: error("no word $id")

    @Test
    fun everyWordIsSplitIntoItsKana() {
        Words.all.forEach { w ->
            assertEquals(w.text, w.kana.joinToString("") { it.char }, w.id)
            assertTrue(w.kana.size >= 2, "${w.id} is a single kana")
        }
        assertEquals(Words.all.size, Words.all.map { it.text }.toSet().size, "duplicate word text")
    }

    @Test
    fun idsAreTheirReadingSoTranslationsStayFindable() {
        // Ids are permanent keys; they are the word's reading with di/du for ぢ/づ, like the kana ids.
        Words.all.forEach { w -> assertEquals(w.id, w.kana.joinToString("") { it.id }, w.text) }
    }

    @Test
    fun everyBasicLessonUnlocksWords() {
        var states = emptyMap<CardId, CardState>()
        Hiragana.lessons.forEach { lesson ->
            states = states + learned(*lesson.kana.map { it.char }.toTypedArray())
            val unlocked = Words.unlockedBy(lesson.kana, states)
            if (lesson.group != KanaGroup.YOON) assertTrue(unlocked.size >= 3, "${lesson.title}: ${unlocked.map { it.text }}")
            assertTrue(unlocked.isNotEmpty(), lesson.title)
        }
        assertEquals(Words.all, Words.readable(states))
    }

    @Test
    fun splitTakesYoonFirstAndRejectsUnknownCharacters() {
        assertEquals(listOf("kyo", "u"), Words.split("きょう")!!.map { it.id })
        assertEquals(listOf("ki", "yo", "u"), Words.split("きよう")!!.map { it.id })
        assertEquals(listOf("n"), Words.split("ん")!!.map { it.id })
        assertNull(Words.split("きって"))
        assertNull(Words.split("らーめん"))
        assertEquals(emptyList(), Words.split(""))
        assertNull(Words.byId("nope"))
    }

    @Test
    fun romajiMarksNBeforeVowelsAndY() {
        assertEquals("hon'ya", word("honya").romaji)
        assertEquals("konnyaku", word("konnyaku").romaji)
        assertEquals("hon", word("hon").romaji)
        assertEquals("sensei", word("sensei").romaji)
        assertEquals("kin'en", Word.romajiOf(Words.split("きんえん")!!))
        assertEquals("hanaji", word("hanadi").romaji)
        assertEquals("tsukue", word("tsukue").romaji)
    }

    @Test
    fun anyAcceptedSpellingOfEachKanaReadsTheWord() {
        assertTrue(word("tsukue").isReadAs("tsukue"))
        assertTrue(word("tsukue").isReadAs("tukue"))
        assertTrue(word("sushi").isReadAs(" SuSi "))
        assertTrue(word("honya").isReadAs("hon'ya"))
        assertTrue(word("honya").isReadAs("honnya"))
        assertTrue(word("honya").isReadAs("honya"))
        assertTrue(word("hanadi").isReadAs("hanadi"))
        assertTrue(word("hanadi").isReadAs("hanaji"))
        assertFalse(word("neko").isReadAs("nek"))
        assertFalse(word("neko").isReadAs("nekoo"))
        assertFalse(word("neko").isReadAs("reko"))
        assertFalse(word("neko").isReadAs(""))
    }

    @Test
    fun misreadFindsTheSwappedKana() {
        val known = Hiragana.all
        assertEquals(Misread(0, kana("れ")), word("neko").misreadIn("reko", known))
        assertEquals(Misread(1, kana("ち")), word("sakana").misreadIn("sachina", known))
        assertEquals(Misread(2, kana("た")), word("sakana").misreadIn("sakata", known))
        assertEquals(Misread(0, kana("き")), word("kyou").misreadIn("kiu", known))
        assertNull(word("neko").misreadIn("neko", known), "a right answer is no misread")
        assertNull(word("neko").misreadIn("inu", known), "more than one kana differs")
        assertNull(word("neko").misreadIn("nek", known), "a kana is missing")
        assertNull(word("neko").misreadIn("nexo", known), "x is no kana reading")
    }

    @Test
    fun misreadPrefersKnownKanaThenLookAlikes() {
        // "ko" for ぬ in いぬ: こ is the only kana read "ko".
        assertEquals(Misread(1, kana("こ")), word("inu").misreadIn("iko", emptyList()))
        // "o" fits お (look-alike of あ) and を (accepts "o"); お wins as look-alike, known or not.
        assertEquals(Misread(0, kana("お")), word("ao").misreadIn("oo", emptyList()))
        assertEquals(Misread(0, kana("お")), word("ao").misreadIn("oo", listOf(kana("を"), kana("お"))))
        // A known kana beats an unknown look-alike.
        assertEquals(Misread(0, kana("を")), word("ao").misreadIn("oo", listOf(kana("を"))))
        // Among unknown kana, the look-alike wins: "ji" for し is じ (look-alike) or ぢ (not one).
        assertEquals(Misread(0, kana("じ")), word("shio").misreadIn("jio", emptyList()))
        // Known and look-alike beats known only.
        assertEquals(Misread(0, kana("じ")), word("shio").misreadIn("jio", listOf(kana("ぢ"), kana("じ"))))
        assertEquals(Misread(0, kana("ぢ")), word("shio").misreadIn("jio", listOf(kana("ぢ"))))
        // Equally good explanations: the first kana in catalogue order wins (お before を; neither looks like え).
        assertEquals(Misread(1, kana("お")), word("ie").misreadIn("io", listOf(kana("を"), kana("お"))))
        assertEquals(Misread(1, kana("お")), word("ie").misreadIn("io", emptyList()))
    }

    @Test
    fun readableNeedsAReadingCardForEveryKana() {
        val neko = word("neko")
        assertFalse(Words.isReadable(neko, emptyMap()))
        assertFalse(Words.isReadable(neko, learned("ね")))
        val writingOnly = listOf(card("ね", Skill.WRITING), card("こ", Skill.WRITING)).associateBy { it.id }
        assertFalse(Words.isReadable(neko, writingOnly))
        val readingOnly = listOf(card("ね", Skill.READING), card("こ", Skill.READING)).associateBy { it.id }
        assertTrue(Words.isReadable(neko, readingOnly))
    }

    @Test
    fun unlockedWordsContainANewKana() {
        val states = learned("あ", "い", "う", "え", "お", "か", "き", "く", "け", "こ")
        val unlocked = Words.unlockedBy(listOf(kana("か"), kana("き"), kana("く"), kana("け"), kana("こ")), states)
        assertTrue(unlocked.none { it.id == "ai" })
        assertTrue(unlocked.any { it.id == "aka" })
        assertEquals(Words.readable(states).filter { w -> w.kana.any { it.lesson == 1 } }, unlocked)
        assertEquals(emptyList(), Words.unlockedBy(emptyList(), states))
    }

    @Test
    fun finishingALessonReportsTheWordsItUnlocked() {
        val vowels = learned("あ", "い", "う", "え", "お")
        val session = StudySession.learn(vowels, strokes, drawingEnabled = false, random = Random(1))!!
        Fixtures.runSession(session)
        val expected = Words.readable(vowels + learned("か", "き", "く", "け", "こ")).filter { w -> w.kana.any { it.lesson == 1 } }
        assertEquals(expected, session.summary.newWords)
        assertTrue(session.summary.newWords.isNotEmpty())
    }
}

class WordSessionTest {
    private val firstLessons = readingOnly("あ", "い", "う", "え", "お", "か", "き", "く", "け", "こ", "さ", "し", "す", "せ", "そ")

    private fun start(
        states: Map<CardId, CardState> = firstLessons,
        types: QuestionTypes = QuestionTypes(),
        limit: Int = WordSession.DEFAULT_LIMIT,
        seed: Int = 0,
    ) = WordSession.start(states, Fixtures.strokes, types, limit, random = Random(seed), newEventId = Fixtures.counterIds())

    private fun run(session: WordSession, answer: (WordQuestion) -> WordAnswer): List<SessionUpdate> {
        val updates = mutableListOf<SessionUpdate>()
        while (!session.isFinished) {
            check(updates.size < 200) { "session does not terminate" }
            updates += session.commit(session.evaluate(answer(session.current!!)), now = 100)
        }
        return updates
    }

    private fun right(q: WordQuestion): WordAnswer = rightWordAnswer(q)

    @Test
    fun noWordsBeforeAnyLessonIsLearned() {
        assertNull(start(emptyMap()))
        assertNull(start(readingOnly("あ")))
        assertNull(start(limit = 0))
    }

    @Test
    fun asksUpToTheLimitOfReadableWordsOnce() {
        val session = start(limit = 6)!!
        val asked = mutableListOf<Word>()
        val updates = run(session) { q -> asked += q.word; right(q) }
        assertEquals(6, asked.size)
        assertEquals(6, asked.toSet().size)
        assertTrue(asked.all { Words.isReadable(it, firstLessons) })
        assertTrue(updates.all { it.cards.isEmpty() && it.events.isEmpty() }, "right answers move no card")
        assertEquals(WordSummary(6, 6, asked, emptyList()), session.summary)
        assertEquals(1f, session.progress)
        assertNull(session.current)
    }

    @Test
    fun allReadableWordsWhenFewerThanTheLimit() {
        val vowels = readingOnly("あ", "い", "う", "え", "お")
        val session = start(vowels, limit = 100)!!
        val asked = mutableSetOf<String>()
        run(session) { q -> asked += q.word.id; right(q) }
        assertEquals(Words.readable(vowels).map { it.id }.toSet(), asked)
    }

    @Test
    fun questionTypesFollowTheSettings() {
        fun typesAsked(types: QuestionTypes) = (0 until 5).flatMap { seed ->
            val session = start(types = types, seed = seed)!!
            buildList { run(session) { q -> add(q.type); right(q) } }
        }.toSet()
        assertEquals(setOf(QuestionType.READING_CHOICE), typesAsked(QuestionTypes(typing = false)))
        assertEquals(setOf(QuestionType.READING_TYPING), typesAsked(QuestionTypes(choice = false)))
        assertEquals(setOf(QuestionType.READING_CHOICE, QuestionType.READING_TYPING), typesAsked(QuestionTypes()))
        assertEquals(setOf(QuestionType.READING_CHOICE, QuestionType.READING_TYPING), typesAsked(QuestionTypes(choice = false, typing = false)))
    }

    @Test
    fun choiceOptionsAreTheWordWithOneKanaSwapped() {
        val everything = readingOnly(*Hiragana.all.map { it.char }.toTypedArray())
        repeat(5) { seed ->
            val session = start(everything, QuestionTypes(typing = false), limit = 200, seed = seed)!!
            run(session) { q ->
                assertEquals(StudySession.OPTION_COUNT, q.options.size, q.word.text)
                assertEquals(q.options.size, q.options.map { it.romaji }.toSet().size)
                assertEquals(q.word.romaji, q.options.single { it.misread == null }.romaji)
                q.options.mapNotNull { it.misread }.forEach { m ->
                    val original = q.word.kana[m.position]
                    assertFalse(m.readAs.soundsLike(original), "${q.word.text}: ${original.char} vs ${m.readAs.char}")
                    assertEquals(original.char.length, m.readAs.char.length)
                    val option = q.options.single { it.misread == m }
                    assertEquals(Word.romajiOf(q.word.kana.toMutableList().also { it[m.position] = m.readAs }), option.romaji)
                    assertFalse(q.word.isReadAs(option.romaji))
                }
                right(q)
            }
        }
    }

    @Test
    fun knownLookAlikesAreSwappedInFirst() {
        // ね is in ねこ; its look-alikes れ and わ are known, so a swapped ね shows up as one of them.
        val states = firstLessons + readingOnly("な", "に", "ぬ", "ね", "の", "ら", "り", "る", "れ", "ろ", "わ")
        repeat(10) { seed ->
            val session = WordSession.start(states, Fixtures.strokes, QuestionTypes(typing = false), limit = 200, random = Random(seed))!!
            run(session) { q ->
                q.options.mapNotNull { it.misread }.forEach { m ->
                    assertTrue(m.readAs in Words.split("あいうえおかきくけこさしすせそなにぬねのらりるれろわ")!!, "${q.word.text}: ${m.readAs.char}")
                }
                val alikes = q.word.kana.flatMap { k -> Hiragana.lookAlikesOf(k).filter { !it.soundsLike(k) && states.containsKey(CardId(it.id, Skill.READING)) } }
                if (alikes.isNotEmpty()) assertTrue(q.options.any { it.misread?.readAs in alikes }, q.word.text)
                right(q)
            }
        }
    }

    @Test
    fun withFewKnownKanaUnknownOnesFillTheOptions() {
        val vowels = readingOnly("あ", "い", "う", "え", "お")
        val session = start(vowels, QuestionTypes(typing = false), limit = 100)!!
        run(session) { q -> assertEquals(StudySession.OPTION_COUNT, q.options.size); right(q) }
    }

    @Test
    fun aDontKnowAsksTheWordAgainButResetsNoCard() {
        val session = start(limit = 1)!!
        val word = session.current!!.word
        val update = session.commit(session.evaluate(WordAnswer.DontKnow), now = 5)
        assertEquals(SessionUpdate(), update)
        assertEquals(word, session.current!!.word)
        assertEquals(0.5f, session.progress)
        // Two retests at most, then it gives up.
        session.commit(session.evaluate(WordAnswer.DontKnow), now = 5)
        session.commit(session.evaluate(WordAnswer.DontKnow), now = 5)
        assertTrue(session.isFinished)
        assertEquals(WordSummary(3, 0, emptyList(), emptyList()), session.summary)
    }

    @Test
    fun aWordReadRightOnlyOnRetestDoesNotCountAsRead() {
        val session = start(limit = 1)!!
        session.commit(session.evaluate(WordAnswer.DontKnow), now = 5)
        session.commit(session.evaluate(right(session.current!!)), now = 5)
        assertTrue(session.isFinished)
        assertEquals(WordSummary(2, 1, emptyList(), emptyList()), session.summary)
    }

    @Test
    fun aPickedMixUpResetsBothKanaAndAsksThemAgainInOtherWords() {
        val states = firstLessons.mapValues { it.value.copy(level = 5, dueAt = 9_999) }
        val session = WordSession.start(states, Fixtures.strokes, QuestionTypes(typing = false), random = Random(4), newEventId = Fixtures.counterIds())!!
        val q = session.current!!
        val wrong = q.options.first { it.misread != null && it.misread!!.readAs in Words.split("あいうえおかきくけこさしすせそ")!! }
        val misread = wrong.misread!!
        val target = q.word.kana[misread.position]
        val evaluation = session.evaluate(WordAnswer.Choice(wrong))
        assertFalse(evaluation.correct)
        assertEquals(misread, evaluation.misread)
        assertEquals(target, evaluation.misreadKana)

        val update = session.commit(evaluation, now = 1_000)
        val scheduler = Scheduler()
        assertEquals(
            listOf(
                scheduler.onWrong(states.getValue(CardId(target.id, Skill.READING)), 1_000),
                scheduler.onWrong(states.getValue(CardId(misread.readAs.id, Skill.READING)), 1_000),
            ),
            update.cards,
        )
        assertEquals(
            listOf(ReviewEvent("e0", target.id, Skill.READING, QuestionType.READING_CHOICE, false, misread.readAs.id, SessionMode.WORDS, 1_000)),
            update.events,
        )
        // The word comes back 4th, a word with the misread kana 6th, one with the other kana 8th.
        val upcoming = buildList { repeat(9) { add(session.current!!.word); session.commit(session.evaluate(right(session.current!!)), 1_000) } }
        assertEquals(q.word, upcoming[3])
        assertTrue(target in upcoming[5].kana && upcoming[5] != q.word, upcoming.map { it.text }.toString())
        assertTrue(misread.readAs in upcoming[7].kana && upcoming[7] != q.word, upcoming.map { it.text }.toString())
        assertEquals(listOf(target, misread.readAs), session.summary.troublesome)
    }

    @Test
    fun aTypedMisreadIsFoundAndEachCardIsResetOncePerSession() {
        val session = start(readingOnly("あ", "い", "う", "え", "お"), QuestionTypes(choice = false), limit = 100, seed = 2)!!
        val events = mutableListOf<ReviewEvent>()
        val cards = mutableListOf<CardState>()
        run(session) { q ->
            // Read あ as お every time.
            val text = q.word.kana.joinToString("") { if (it.char == "あ") "o" else it.romaji }
            WordAnswer.Typed(text)
        }.forEach { events += it.events; cards += it.cards }
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.kanaId == "a" && it.confusedWithKanaId == "o" && it.questionType == QuestionType.READING_TYPING })
        assertEquals(listOf(CardId("a", Skill.READING), CardId("o", Skill.READING)), cards.map { it.id })
        assertEquals(listOf(kana("あ"), kana("お")), session.summary.troublesome)
    }

    @Test
    fun aMisreadAsAnUnknownKanaOnlyResetsTheWordsKana() {
        val vowels = readingOnly("あ", "い", "う", "え", "お")
        val session = start(vowels, QuestionTypes(choice = false), limit = 100, seed = 4)!!
        val word = session.current!!.word
        val text = word.kana.joinToString("") { it.romaji }.replaceFirst(word.kana[0].romaji, "nu")
        val evaluation = session.evaluate(WordAnswer.Typed(text))
        assertEquals(Misread(0, kana("ぬ")), evaluation.misread)
        val update = session.commit(evaluation, now = 1)
        assertEquals(listOf(CardId(word.kana[0].id, Skill.READING)), update.cards.map { it.id })
        assertEquals(listOf(word.kana[0]), session.summary.troublesome)
    }

    @Test
    fun overrulingATypoCountsAsRight() {
        val session = start(types = QuestionTypes(choice = false), limit = 1)!!
        val q = session.current!!
        val text = q.word.kana.mapIndexed { i, k -> if (i == 0) "x" else k.romaji }.joinToString("")
        val evaluation = session.evaluate(WordAnswer.Typed(text))
        assertFalse(evaluation.correct)
        assertNull(evaluation.misread)
        assertEquals(SessionUpdate(), session.commit(evaluation, now = 1, acceptAsCorrect = true))
        assertTrue(session.isFinished)
        assertEquals(WordSummary(1, 1, listOf(q.word), emptyList()), session.summary)
    }

    @Test
    fun typedRightAnswersAreRight() {
        val session = start(types = QuestionTypes(choice = false), limit = 1)!!
        val evaluation = session.evaluate(WordAnswer.Typed(session.current!!.word.romaji.uppercase()))
        assertTrue(evaluation.correct)
        assertNull(evaluation.misread)
        assertNull(evaluation.misreadKana)
    }

    @Test
    fun commitRejectsAStaleEvaluation() {
        val session = start(limit = 2)!!
        val evaluation = session.evaluate(WordAnswer.DontKnow)
        session.commit(evaluation, now = 1)
        assertFailsWith<IllegalArgumentException> { session.commit(evaluation, now = 1) }
        run(session, ::right)
        assertFailsWith<IllegalStateException> { session.evaluate(WordAnswer.DontKnow) }
        assertFailsWith<IllegalArgumentException> { session.commit(evaluation, now = 1) }
    }

    @Test
    fun theCurrentQuestionIsStableUntilAnswered() {
        val session = start(types = QuestionTypes(typing = false))!!
        assertTrue(session.current === session.current)
        assertEquals(0f, session.progress)
    }

    @Test
    fun weakestAndNewestKanaComeFirst() {
        val old = readingOnly("あ", "い", "う", "え", "お", level = 6)
        val newer = readingOnly("か", "き", "く", "け", "こ", level = 1).mapValues { it.value.copy(introducedAt = 50) }
        val newest = readingOnly("さ", "し", "す", "せ", "そ", level = 1).mapValues { it.value.copy(introducedAt = 90) }
        val states = old + newer + newest
        repeat(5) { seed ->
            val words = WordSession.wordsToRead(states, Random(seed))
            assertEquals(Words.readable(states).toSet(), words.toSet())
            val rank = words.map { w -> if (w.kana.any { it.lesson == 2 }) 0 else if (w.kana.any { it.lesson == 1 }) 1 else 2 }
            assertEquals(rank.sorted(), rank, words.map { it.text }.toString())
        }
        val session = WordSession.start(states, Fixtures.strokes, limit = 3, random = Random(0))!!
        assertTrue(session.current!!.word.kana.any { it.lesson == 2 })
    }

    @Test
    fun startDoesNotChangeTheCallersStates() {
        val states = firstLessons.toMutableMap()
        val before = states.toMap()
        val session = WordSession.start(states, Fixtures.strokes, QuestionTypes(typing = false), random = Random(1))!!
        val wrong = session.current!!.options.first { it.misread != null }
        session.commit(session.evaluate(WordAnswer.Choice(wrong)), now = 7)
        assertEquals(before, states)
        assertNotNull(session.current)
    }

    @Test
    fun theWeakestKanaOutrankTheNewest() {
        // Vowels are weaker (level 1) but were learned long before the K-row (level 4).
        val states = readingOnly("あ", "い", "う", "え", "お", level = 1) +
            readingOnly("か", "き", "く", "け", "こ", level = 4).mapValues { it.value.copy(introducedAt = 100) }
        repeat(5) { seed ->
            val words = WordSession.wordsToRead(states, Random(seed))
            val rank = words.map { w -> if (w.kana.any { it.lesson == 0 }) 0 else 1 }
            assertEquals(rank.sorted(), rank, words.map { it.text }.toString())
            assertEquals(1, rank.last())
        }
    }

    @Test
    fun aWordAlreadyComingUpIsNotQueuedTwice() {
        // Vowels only: あい いえ うえ あお いいえ. The first word has あ and is misread; the other words with あ and お
        // are all still queued, so only the misread word itself is added again.
        val vowels = readingOnly("あ", "い", "う", "え", "お")
        val seed = (0 until 100).first { s -> kana("あ") in start(vowels, QuestionTypes(choice = false), 100, s)!!.current!!.word.kana }
        val session = start(vowels, QuestionTypes(choice = false), 100, seed)!!
        val word = session.current!!.word
        val text = word.kana.joinToString("") { if (it.char == "あ") "o" else it.romaji }
        session.commit(session.evaluate(WordAnswer.Typed(text)), now = 1)
        val rest = buildList { while (!session.isFinished) { add(session.current!!.word); session.commit(session.evaluate(right(session.current!!)), 1) } }
        assertEquals(Words.readable(vowels).size, rest.size, rest.map { it.text }.toString())
        assertEquals(1, rest.count { it == word })
        val summary = session.summary
        assertEquals(6, summary.answered)
        assertEquals(5, summary.correct)
        assertEquals(Words.readable(vowels).size - 1, summary.read.size)
    }
}
