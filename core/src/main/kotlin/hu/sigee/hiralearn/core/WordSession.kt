package hu.sigee.hiralearn.core

import java.util.UUID
import kotlin.random.Random

/**
 * One spelling offered for a word: its kana and reading. A wrong option is the word with one kana swapped, so picking
 * it names the mix-up.
 */
data class WordOption(val kana: List<Kana>, val misread: Misread?) {
    val text: String get() = kana.joinToString("") { it.char }
    val romaji: String get() = Word.romajiOf(kana)
}

/**
 * A word asked like a kana card, with the same [QuestionType]s: READING_* shows the word, WRITING_* shows its reading,
 * LISTENING_* only says it. *_CHOICE questions have [options] (the right one included).
 */
data class WordQuestion(val word: Word, val type: QuestionType, val options: List<WordOption> = emptyList())

sealed interface WordAnswer {
    data class Choice(val option: WordOption) : WordAnswer
    data class Typed(val text: String) : WordAnswer

    /** The word written by hand: one drawing per kana of the word, in order. */
    data class Drawn(val kana: List<List<Stroke>>) : WordAnswer
    data object DontKnow : WordAnswer
}

data class WordEvaluation(
    val question: WordQuestion,
    val answer: WordAnswer,
    val correct: Boolean,
    /** Which kana of the word was taken for which other kana, if the wrong answer shows it. */
    val misread: Misread?,
    /** For a drawn answer: the grade of each kana, in word order. */
    val drawings: List<DrawingResult>? = null,
) {
    /** The kana of the word that was mixed up. */
    val misreadKana: Kana? get() = misread?.let { question.word.kana[it.position] }
}

data class WordSummary(
    val answered: Int,
    val correct: Int,
    /** Words answered right the first time they were asked. */
    val read: List<Word>,
    /** Kana that were mixed up with another kana; their cards were reset. */
    val troublesome: List<Kana>,
)

/**
 * Real words made of kana the learner already knows, asked like kana: read them (pick or type the reading), write them
 * (pick the spelling or draw it kana by kana) and hear them (pick or draw what was said).
 *
 * Words have no schedule of their own: they exercise the kana cards of the question's skill. Like Practice, a right
 * answer moves no card further out. A wrong answer asks the word again later in the session; if the answer shows which
 * kana was mixed up (a picked option, a typed reading that differs in one kana, a drawn kana that looks like another),
 * that kana's card and the card of the kana it was taken for are reset like any other mix-up, and other words with
 * those kana are asked soon.
 *
 * Flow per step, as in [StudySession]: read [current], call [evaluate] (pure), show the feedback, then [commit].
 */
class WordSession private constructor(
    private val states: MutableMap<CardId, CardState>,
    private val strokes: StrokeLibrary,
    private var drawingEnabled: Boolean,
    private val scheduler: Scheduler,
    private val random: Random,
    private val newEventId: () -> String,
) {
    private data class Step(val word: Word, val type: QuestionType)

    private val queue = mutableListOf<Step>()
    private val grader = HandwritingGrader(strokes)
    private val asked = mutableSetOf<String>()
    private val graded = mutableSetOf<CardId>()
    private val retests = mutableMapOf<String, Int>()
    private val read = mutableListOf<Word>()
    private val troublesome = linkedSetOf<Kana>()
    private var answered = 0
    private var correctAnswers = 0
    private var stepsDone = 0
    private var cached: Pair<Step, WordQuestion>? = null

    /** Kana with a reading card: the ones the learner can be expected to know. */
    private val known: List<Kana> = Hiragana.all.filter { states.containsKey(CardId(it.id, Skill.READING)) }

    val isFinished: Boolean get() = queue.isEmpty()

    val progress: Float get() = if (stepsDone + queue.size == 0) 1f else stepsDone.toFloat() / (stepsDone + queue.size)

    val current: WordQuestion?
        get() {
            val step = queue.firstOrNull() ?: return null
            cached?.let { (s, q) -> if (s === step) return q }
            val options = if (step.type.isChoice) optionsFor(step.word) else emptyList()
            return WordQuestion(step.word, step.type, options).also { cached = step to it }
        }

    val summary: WordSummary get() = WordSummary(answered, correctAnswers, read.toList(), troublesome.toList())

    val canSkipDrawing: Boolean get() = drawingEnabled

    /** "I can't draw right now": this and all later drawing questions become multiple choice. */
    fun disableDrawing() {
        drawingEnabled = false
        val converted = queue.map { it.copy(type = typeFor(it.word, it.type)) }
        queue.clear()
        queue += converted
        cached = null
    }

    fun evaluate(answer: WordAnswer): WordEvaluation {
        val question = current ?: error("Session is finished")
        val word = question.word
        return when (answer) {
            WordAnswer.DontKnow -> WordEvaluation(question, answer, correct = false, misread = null)
            is WordAnswer.Choice -> WordEvaluation(question, answer, answer.option.misread == null, answer.option.misread)
            is WordAnswer.Typed -> {
                val ok = word.isReadAs(answer.text)
                WordEvaluation(question, answer, ok, if (ok) null else word.misreadIn(answer.text, known))
            }
            is WordAnswer.Drawn -> {
                require(answer.kana.size == word.kana.size) { "One drawing per kana of ${word.text}" }
                val results = word.kana.mapIndexed { i, k -> grader.grade(answer.kana[i], k, known) }
                val ok = results.all { it.correct }
                val misread = results.withIndex().firstNotNullOfOrNull { (i, r) -> r.lookedLike?.let { Misread(i, it) } }
                WordEvaluation(question, answer, ok, if (ok) null else misread, results)
            }
        }
    }

    /** Applies an answer. [acceptAsCorrect] = the learner overrules the check (a typo); no mix-up is recorded then. */
    fun commit(evaluation: WordEvaluation, now: Long, acceptAsCorrect: Boolean = false): SessionUpdate {
        val step = queue.firstOrNull()
        require(step != null && cached?.second == evaluation.question) { "Evaluation does not belong to the current step" }
        queue.removeAt(0)
        cached = null
        stepsDone++
        answered++
        val firstTime = asked.add(step.word.id)
        val correct = evaluation.correct || acceptAsCorrect
        if (correct) {
            correctAnswers++
            if (firstTime) read += step.word
            return SessionUpdate()
        }

        scheduleRetest(step, position = 3)
        val misread = evaluation.misread ?: return SessionUpdate()
        val skill = step.type.skill
        val target = step.word.kana[misread.position]
        val other = misread.readAs
        val changed = listOfNotNull(reset(target, skill, now), reset(other, skill, now))
        troublesome += target
        if (other in known) troublesome += other
        // Both kana are not known: ask them again soon, in other words.
        wordWith(target, skill, step.word)?.let { scheduleRetest(Step(it, typeFor(it, step.type)), position = 5) }
        if (other in known) wordWith(other, skill, step.word)?.let { scheduleRetest(Step(it, typeFor(it, step.type)), position = 7) }

        val event = ReviewEvent(
            id = newEventId(),
            kanaId = target.id,
            skill = skill,
            questionType = step.type,
            correct = false,
            confusedWithKanaId = other.id,
            mode = SessionMode.WORDS,
            answeredAt = now,
        )
        return SessionUpdate(cards = changed, events = listOf(event))
    }

    /** Marks the [skill] card of [kana] as not known, once per session. */
    private fun reset(kana: Kana, skill: Skill, now: Long): CardState? {
        val id = CardId(kana.id, skill)
        val state = states[id] ?: return null
        if (!graded.add(id)) return null
        return scheduler.onWrong(state, now).also { states[id] = it }
    }

    /** Another word containing [kana] that can be asked for [skill], preferring words not asked yet. */
    private fun wordWith(kana: Kana, skill: Skill, except: Word): Word? {
        val candidates = Words.all.filter { w -> w != except && kana in w.kana && Words.isReadable(w, states) && hasCards(w, skill, states) }
        return candidates.filter { it.id !in asked && queue.none { s -> s.word == it } }.randomOrNull(random)
            ?: candidates.randomOrNull(random)
    }

    private fun scheduleRetest(step: Step, position: Int) {
        val count = retests.getOrDefault(step.word.id, 0)
        if (count >= MAX_RETESTS) return
        // Same word *and* skill: a word picked alone is queued once per skill, and a miss must still come back.
        if (queue.take(position + 1).any { it.word == step.word && it.type.skill == step.type.skill }) return
        retests[step.word.id] = count + 1
        queue.add(position.coerceAtMost(queue.size), step)
        cached = null
    }

    /** [type] for [word], or the choice question of the same skill when [word] can't be drawn that way. */
    private fun typeFor(word: Word, type: QuestionType): QuestionType = when (type) {
        QuestionType.WRITING_DRAWING -> if (canDraw(word)) type else QuestionType.WRITING_CHOICE
        QuestionType.LISTENING_DRAWING -> if (canDrawHeard(word)) type else QuestionType.LISTENING_CHOICE
        else -> type
    }

    private fun canDraw(word: Word): Boolean = drawingEnabled && word.kana.all { strokes.canDraw(it) }

    /**
     * Writing a heard word needs every kana to be the usual spelling of its sound. Words mostly settle it (みず, not
     * みづ), but a word with ぢ, づ or を sounds just like one spelled with じ, ず or お, so those are only asked as choice
     * (where options never sound like the answer). A sound-alike from an earlier lesson marks the rarer spelling.
     */
    private fun canDrawHeard(word: Word): Boolean =
        canDraw(word) && word.kana.none { k -> Hiragana.soundAlikesOf(k).any { it.lesson <= k.lesson } }

    /** A question of [skill] for [word], drawn or typed at random when both that and choice are on. */
    private fun typeOf(word: Word, skill: Skill, types: QuestionTypes): QuestionType {
        fun harder(possible: Boolean) = possible && (!types.choice || random.nextBoolean())
        return when (skill) {
            Skill.READING -> if (harder(types.typing)) QuestionType.READING_TYPING else QuestionType.READING_CHOICE
            Skill.WRITING -> if (harder(canDraw(word))) QuestionType.WRITING_DRAWING else QuestionType.WRITING_CHOICE
            Skill.LISTENING -> if (harder(canDrawHeard(word))) QuestionType.LISTENING_DRAWING else QuestionType.LISTENING_CHOICE
        }
    }

    /**
     * The right spelling plus up to [StudySession.OPTION_COUNT]-1 wrong ones, each the word with one kana swapped:
     * for a look-alike the learner knows first, then another known kana, then any kana. A swapped kana never sounds
     * like the original (じ/ぢ would make two options the same reading), and no two options read the same, so the
     * options work for reading, writing and listening alike.
     */
    private fun optionsFor(word: Word): List<WordOption> {
        val tiers = word.kana.indices.map { i ->
            val original = word.kana[i]
            val sameShape = Hiragana.all.filter { it.char.length == original.char.length && !it.soundsLike(original) }
            val alike = Hiragana.lookAlikesOf(original).filter { it in known && it in sameShape }
            Triple(i, alike, sameShape)
        }
        val candidates = tiers.flatMap { (i, alike, _) -> alike.map { Misread(i, it) } }.shuffled(random) +
            tiers.flatMap { (i, _, same) -> same.filter { it in known }.map { Misread(i, it) } }.shuffled(random) +
            tiers.flatMap { (i, _, same) -> same.map { Misread(i, it) } }.shuffled(random)
        val options = mutableListOf(WordOption(word.kana, null))
        for (misread in candidates) {
            if (options.size >= StudySession.OPTION_COUNT) break
            val swapped = WordOption(word.kana.toMutableList().also { it[misread.position] = misread.readAs }, misread)
            if (options.any { it.romaji == swapped.romaji } || word.isReadAs(swapped.romaji)) continue
            options += swapped
        }
        return options.shuffled(random)
    }

    private fun addInitialSteps(steps: List<Step>) {
        queue += steps
    }

    companion object {
        const val DEFAULT_LIMIT = 10
        private const val MAX_RETESTS = 2

        private val QuestionType.isChoice: Boolean
            get() = this == QuestionType.READING_CHOICE || this == QuestionType.WRITING_CHOICE || this == QuestionType.LISTENING_CHOICE

        private fun hasCards(word: Word, skill: Skill, states: Map<CardId, CardState>): Boolean =
            word.kana.all { states.containsKey(CardId(it.id, skill)) }

        /** Readable words, weakest first: lowest reading level among their kana, then the most recently learned. */
        fun wordsToRead(states: Map<CardId, CardState>, random: Random = Random.Default): List<Word> {
            fun readingCards(w: Word) = w.kana.mapNotNull { states[CardId(it.id, Skill.READING)] }
            return Words.readable(states).shuffled(random).sortedWith(
                compareBy({ w -> readingCards(w).minOf { it.level } }, { w -> -readingCards(w).maxOf { it.introducedAt } }),
            )
        }

        /**
         * A session of up to [limit] readable words, or null if the learner can't read any word yet. The skills of
         * [types] take turns, so a session mixes reading, writing and listening (a word is only asked for a skill all
         * its kana have cards for). Within a skill, choice and typing/drawing follow [types] (a random mix when both
         * are on), as in Practice.
         */
        fun start(
            states: Map<CardId, CardState>,
            strokes: StrokeLibrary,
            types: QuestionTypes = QuestionTypes(),
            limit: Int = DEFAULT_LIMIT,
            scheduler: Scheduler = Scheduler(),
            random: Random = Random.Default,
            newEventId: () -> String = { UUID.randomUUID().toString() },
        ): WordSession? {
            val words = wordsToRead(states, random).take(limit)
            if (words.isEmpty()) return null
            val t = if (types.isValid) types else QuestionTypes(listening = types.listening)
            val session = WordSession(states.toMutableMap(), strokes, t.drawing, scheduler, random, newEventId)
            val skills = t.skills.toList().shuffled(random)
            session.addInitialSteps(
                words.shuffled(random).mapIndexed { i, word ->
                    // Reading is always among the skills, and the word is readable.
                    val skill = skills.indices.map { skills[(i + it) % skills.size] }.first { hasCards(word, it, states) }
                    Step(word, session.typeOf(word, skill, t))
                },
            )
            return session
        }

        /**
         * A session for words the learner picked (the glossary): each readable word of [words] is asked once in every
         * skill of [types] that all its kana have cards for, in random order. Null if none of them can be read yet.
         * Answers count as in [start]: a right one moves no card, a mix-up resets the kana involved.
         */
        fun startWith(
            words: Collection<Word>,
            states: Map<CardId, CardState>,
            strokes: StrokeLibrary,
            types: QuestionTypes = QuestionTypes(),
            scheduler: Scheduler = Scheduler(),
            random: Random = Random.Default,
            newEventId: () -> String = { UUID.randomUUID().toString() },
        ): WordSession? {
            val readable = words.distinct().filter { Words.isReadable(it, states) }
            if (readable.isEmpty()) return null
            val t = if (types.isValid) types else QuestionTypes(listening = types.listening)
            val session = WordSession(states.toMutableMap(), strokes, t.drawing, scheduler, random, newEventId)
            val steps = readable.flatMap { word ->
                Skill.entries.filter { it in t.skills && hasCards(word, it, states) }.map { Step(word, session.typeOf(word, it, t)) }
            }
            session.addInitialSteps(steps.shuffled(random))
            return session
        }
    }
}
