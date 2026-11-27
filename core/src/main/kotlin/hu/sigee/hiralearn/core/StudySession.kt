package hu.sigee.hiralearn.core

import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

enum class SessionMode {
    LEARN,
    REVIEW,

    /** Extra practice of learned kana that aren't due yet. Only mistakes affect the schedule. */
    PRACTICE,

    /** Reading real words (see [WordSession]). Only logged for kana mix-ups found in a word. */
    WORDS,
    /**
     * Side-by-side contrast questions for the pairs the learner mixes up (see [Confusions]). Scored like
     * PRACTICE: right answers don't move cards, mistakes and mix-ups do.
     */
    DRILL,
}

/**
 * Which question types the learner wants in Review and Practice. Every skill always keeps at least one type:
 * reading needs choice or typing, writing (and listening) needs choice or drawing (see [isValid]). Yōon can't be
 * drawn, so their writing and listening fall back to choice even when choice is off.
 *
 * [listening] turns the whole listening skill on or off: it needs audio, which the device may not have. While it is
 * off, listening cards are neither asked nor created, and they don't count as due.
 */
data class QuestionTypes(
    val choice: Boolean = true,
    val typing: Boolean = true,
    val drawing: Boolean = true,
    val listening: Boolean = true,
) {
    val isValid: Boolean get() = (choice || typing) && (choice || drawing)

    /** The skills that are asked (and scheduled) with these settings. */
    val skills: Set<Skill> get() = if (listening) Skill.entries.toSet() else Skill.entries.toSet() - Skill.LISTENING
}

enum class QuestionType(val skill: Skill) {
    /** Kana shown, pick its reading. */
    READING_CHOICE(Skill.READING),

    /** Kana shown, type its reading. */
    READING_TYPING(Skill.READING),

    /** Reading shown, pick the kana. */
    WRITING_CHOICE(Skill.WRITING),

    /** Reading shown, draw the kana. */
    WRITING_DRAWING(Skill.WRITING),

    /** Sound played, pick the kana. */
    LISTENING_CHOICE(Skill.LISTENING),

    /** Sound played, draw the kana. */
    LISTENING_DRAWING(Skill.LISTENING),
}

sealed interface Task {
    val kana: Kana

    /** Teach a new kana: show it, its reading and its stroke order. */
    data class Introduce(override val kana: Kana, val strokes: List<Stroke>?) : Task

    /**
     * @param options choices for the *_CHOICE types (includes [kana]).
     * @param guided for drawing: show the reference strokes faintly so the learner can trace them.
     * @param reference reference strokes for drawing questions.
     */
    data class Question(
        override val kana: Kana,
        val type: QuestionType,
        val options: List<Kana> = emptyList(),
        val guided: Boolean = false,
        val reference: List<Stroke>? = null,
    ) : Task
}

sealed interface Answer {
    data class Choice(val kana: Kana) : Answer
    data class Typed(val text: String) : Answer
    data class Drawn(val strokes: List<Stroke>) : Answer
    data object DontKnow : Answer
}

data class Evaluation(
    val question: Task.Question,
    val answer: Answer,
    val correct: Boolean,
    /** The kana the learner seems to have mixed this one up with. */
    val confusedWith: Kana?,
    val drawing: DrawingResult? = null,
)

/** Append-only answer log. Each event has a globally unique id so logs from several devices can be merged later. */
data class ReviewEvent(
    val id: String,
    val kanaId: String,
    val skill: Skill,
    val questionType: QuestionType,
    val correct: Boolean,
    val confusedWithKanaId: String?,
    val mode: SessionMode,
    val answeredAt: Long,
)

/** What must be persisted after a step. */
data class SessionUpdate(
    val cards: List<CardState> = emptyList(),
    val events: List<ReviewEvent> = emptyList(),
    val graduated: List<Kana> = emptyList(),
)

data class SessionSummary(
    val mode: SessionMode,
    val answered: Int,
    val correct: Int,
    val learned: List<Kana>,
    val troublesome: List<Kana>,
    /** Learn only: words the learner can read now thanks to the kana learned in this session. */
    val newWords: List<Word> = emptyList(),
)

/**
 * One learning or review session.
 *
 * LEARN: each new kana is introduced, then tested repeatedly with growing gaps and different question
 * types (pick reading, pick kana, trace, pick what you hear, trace again, type, draw) interleaved with the other
 * new kana.
 * When all its steps are done it becomes learned cards (reading + writing + listening, unless listening is off)
 * in the spaced-repetition schedule.
 *
 * REVIEW: each due card is asked once; the answer moves it up or down the schedule.
 *
 * PRACTICE: like REVIEW, but for learned cards that aren't due yet (weakest first). A right answer doesn't
 * move the card further out, because answering early proves less than remembering after the full gap;
 * a wrong answer or mix-up still brings the card back sooner.
 *
 * DRILL: for each confused pair, both kana are asked in both directions with only the two of them as options,
 * so the learner has to tell exactly those two apart. Scored like PRACTICE.
 *
 * In both modes a wrong answer puts the item back into the session a few questions later (a failed drawing comes
 * back as a trace over the guide, then as a drawing from memory), and if the
 * learner mixed it up with another kana, that other kana is marked as not known too (its schedule is
 * reset and it is asked again in this session).
 *
 * Flow per step: read [current]; for an [Task.Introduce] call [acknowledgeIntroduction]; for a question call
 * [evaluate] (pure), show the feedback, then [commit] (optionally accepting a wrong answer as correct).
 */
class StudySession private constructor(
    val mode: SessionMode,
    private val strokes: StrokeLibrary,
    initialStates: Map<CardId, CardState>,
    private val scheduler: Scheduler,
    private val random: Random,
    drawingEnabled: Boolean,
    private val skills: Set<Skill>,
    private val newEventId: () -> String,
) {
    private enum class StepKind(val skill: Skill) {
        INTRODUCE(Skill.READING),
        READING_CHOICE(Skill.READING),
        READING_TYPING(Skill.READING),
        WRITING_CHOICE(Skill.WRITING),
        WRITING_TRACE(Skill.WRITING),
        WRITING_DRAWING(Skill.WRITING),
        LISTENING_CHOICE(Skill.LISTENING),
        LISTENING_DRAWING(Skill.LISTENING),
    }

    /** [partner]: a contrast step (DRILL) whose only options are [kana] and [partner]. */
    private data class Step(val kana: Kana, val kind: StepKind, val partner: Kana? = null)

    private val states = initialStates.toMutableMap()
    private val queue = mutableListOf<Step>()
    private val grader = HandwritingGrader(strokes)
    private var drawingEnabled = drawingEnabled

    /** Kana introduced during this session. */
    private val seen = mutableSetOf<Kana>()

    /** Cards whose schedule was already changed in this session; later answers don't move them again. */
    private val graded = mutableSetOf<CardId>()
    private val retestCount = mutableMapOf<Pair<String, Skill>, Int>()
    private val graduated = mutableListOf<Kana>()
    private val troublesome = linkedSetOf<Kana>()
    private var answered = 0
    private var correctAnswers = 0
    private var stepsDone = 0
    private var cachedTask: Pair<Step, Task>? = null

    val isFinished: Boolean get() = queue.isEmpty()

    /** 0..1, grows as steps are done; retests make the remaining part longer. */
    val progress: Float get() = if (stepsDone + queue.size == 0) 1f else stepsDone.toFloat() / (stepsDone + queue.size)

    val current: Task?
        get() {
            val step = queue.firstOrNull() ?: return null
            cachedTask?.let { (s, task) -> if (s === step) return task }
            return buildTask(step).also { cachedTask = step to it }
        }

    val summary: SessionSummary
        get() = SessionSummary(
            mode, answered, correctAnswers, graduated.toList(), troublesome.toList(),
            newWords = Words.unlockedBy(graduated, states),
        )

    val canSkipDrawing: Boolean get() = drawingEnabled

    /** "I can't draw right now": turn this and all later drawing steps into multiple choice. */
    fun disableDrawing() {
        drawingEnabled = false
        val converted = queue.mapNotNull { step ->
            when (step.kind) {
                StepKind.WRITING_TRACE -> if (step === queue.first()) step.copy(kind = StepKind.WRITING_CHOICE) else null
                StepKind.WRITING_DRAWING -> step.copy(kind = StepKind.WRITING_CHOICE)
                StepKind.LISTENING_DRAWING -> step.copy(kind = StepKind.LISTENING_CHOICE)
                else -> step
            }
        }
        queue.clear()
        queue += converted
        cachedTask = null
    }

    fun acknowledgeIntroduction(now: Long): SessionUpdate {
        val step = queue.firstOrNull()
        require(step != null && step.kind == StepKind.INTRODUCE) { "Current step is not an introduction" }
        queue.removeAt(0)
        stepsDone++
        seen += step.kana
        return SessionUpdate()
    }

    fun evaluate(answer: Answer): Evaluation {
        val question = current as? Task.Question ?: error("Current step is not a question")
        val target = question.kana
        return when (answer) {
            Answer.DontKnow -> Evaluation(question, answer, correct = false, confusedWith = null)
            is Answer.Choice -> {
                val ok = answer.kana.id == target.id
                Evaluation(question, answer, ok, confusedWith = if (ok) null else answer.kana)
            }
            is Answer.Typed -> {
                val text = normalizeTyped(answer.text)
                val ok = text in target.accepted
                val confused = if (ok) null else pool().firstOrNull { it.id != target.id && text in it.accepted }
                Evaluation(question, answer, ok, confused)
            }
            is Answer.Drawn -> {
                val result = grader.grade(answer.strokes, target, pool())
                Evaluation(question, answer, result.correct, result.lookedLike, result)
            }
        }
    }

    /**
     * Applies an answer. [acceptAsCorrect] lets the learner overrule the grader (a typo or a
     * handwriting misread); the answer then counts as correct and no mix-up is recorded.
     */
    fun commit(evaluation: Evaluation, now: Long, acceptAsCorrect: Boolean = false): SessionUpdate {
        val step = queue.firstOrNull()
        require(step != null && step.kind != StepKind.INTRODUCE && cachedTask?.second == evaluation.question) {
            "Evaluation does not belong to the current step"
        }
        queue.removeAt(0)
        cachedTask = null
        stepsDone++
        answered++

        val correct = evaluation.correct || acceptAsCorrect
        val confused = if (correct) null else evaluation.confusedWith?.takeIf { it.id != step.kana.id }
        if (correct) correctAnswers++ else troublesome += step.kana
        val skill = step.kind.skill
        val changed = mutableListOf<CardState>()

        // 1. Schedule the answered card. Only its first answer counts: retests always follow a change that
        //    already put the card in [graded], so they never move it again.
        val cardId = CardId(step.kana.id, skill)
        val state = states[cardId]
        val countsWhenRight = mode == SessionMode.LEARN || mode == SessionMode.REVIEW
        if (state != null && cardId !in graded && (countsWhenRight || !correct)) {
            val updated = if (correct) scheduler.onCorrect(state, now) else scheduler.onWrong(state, now)
            states[cardId] = updated
            graded += cardId
            changed += updated
        }

        // 2. Wrong: ask it again soon in this session. A failed drawing is first traced over the guide (so the hand
        //    practises the right strokes), then drawn from memory again a little later.
        if (!correct) {
            if (step.kind == StepKind.WRITING_DRAWING && drawingEnabled) {
                scheduleRetest(step.kana, StepKind.WRITING_TRACE, position = 3, partner = null, then = StepKind.WRITING_DRAWING)
            } else {
                scheduleRetest(step.kana, step.kind, position = 3, partner = step.partner)
            }
        }

        // 3. Mixed up with another kana: that one is not known either.
        if (confused != null) {
            troublesome += confused
            val otherId = CardId(confused.id, skill)
            states[otherId]?.let { other ->
                if (otherId !in graded) {
                    val updated = scheduler.onWrong(other, now)
                    states[otherId] = updated
                    graded += otherId
                    changed += updated
                }
            }
            if (states.containsKey(otherId) || confused in seen) {
                // A contrast step stays a contrast of the same two kana.
                val partner = if (step.partner != null) step.kana else null
                scheduleRetest(confused, retestKindFor(confused, step.kind), position = 5, partner = partner)
            }
        }

        // 4. A new kana with no steps left is learned.
        val newCards = graduateIfDone(step.kana, now)
        changed += newCards

        val event = ReviewEvent(
            id = newEventId(),
            kanaId = step.kana.id,
            skill = skill,
            questionType = evaluation.question.type,
            correct = correct,
            confusedWithKanaId = confused?.id,
            mode = mode,
            answeredAt = now,
        )
        return SessionUpdate(cards = changed, events = listOf(event), graduated = if (newCards.isEmpty()) emptyList() else listOf(step.kana))
    }

    /** Returns the new cards if [kana] was just learned, otherwise an empty list. */
    private fun graduateIfDone(kana: Kana, now: Long): List<CardState> {
        if (mode != SessionMode.LEARN || kana in graduated || queue.any { it.kana == kana }) return emptyList()
        // A kana learned in an earlier session can be retested here after a mix-up; it keeps its schedule.
        if (isLearned(kana, states)) return emptyList()
        val cards = Skill.entries.filter { it in skills }.map { scheduler.introduce(CardId(kana.id, it), now) }
        cards.forEach { states[it.id] = it }
        // Cards are already scheduled from now; mark them graded so later retests in this session don't reset them.
        cards.forEach { graded += it.id }
        graduated += kana
        return cards
    }

    /** [then]: a follow-up step [RETEST_FOLLOW_UP_GAP] steps after the retest, counted as part of the same retest. */
    private fun scheduleRetest(kana: Kana, kind: StepKind, position: Int, partner: Kana?, then: StepKind? = null) {
        val key = kana.id to kind.skill
        val count = retestCount.getOrDefault(key, 0)
        if (count >= MAX_RETESTS) return
        // Already coming up soon? Don't pile on.
        if (queue.take(position + 1).any { it.kana == kana && it.kind.skill == kind.skill }) return
        retestCount[key] = count + 1
        val index = position.coerceAtMost(queue.size)
        queue.add(index, Step(kana, kind, partner))
        if (then != null) queue.add((index + 1 + RETEST_FOLLOW_UP_GAP).coerceAtMost(queue.size), Step(kana, then, partner))
        cachedTask = null
    }

    private fun retestKindFor(kana: Kana, kind: StepKind): StepKind = when (kind) {
        StepKind.WRITING_TRACE, StepKind.WRITING_DRAWING ->
            if (drawingEnabled && strokes.canDraw(kana)) StepKind.WRITING_DRAWING else StepKind.WRITING_CHOICE
        StepKind.LISTENING_DRAWING ->
            if (canDrawHeard(kana, drawingEnabled, strokes)) StepKind.LISTENING_DRAWING else StepKind.LISTENING_CHOICE
        else -> kind
    }

    /** Kana the learner has been taught: learned before, or introduced in this session. */
    private fun pool(): List<Kana> {
        val known = states.keys.mapNotNull { Hiragana.byId(it.kanaId) }.toSet()
        return (known + seen).toList()
    }

    private fun buildTask(step: Step): Task {
        val kana = step.kana
        return when (step.kind) {
            StepKind.INTRODUCE -> Task.Introduce(kana, strokes.strokesFor(kana))
            StepKind.READING_CHOICE -> Task.Question(kana, QuestionType.READING_CHOICE, optionsFor(step))
            StepKind.READING_TYPING -> Task.Question(kana, QuestionType.READING_TYPING)
            StepKind.WRITING_CHOICE -> Task.Question(kana, QuestionType.WRITING_CHOICE, optionsFor(step))
            StepKind.WRITING_TRACE, StepKind.WRITING_DRAWING -> Task.Question(
                kana, QuestionType.WRITING_DRAWING,
                guided = step.kind == StepKind.WRITING_TRACE,
                reference = strokes.strokesFor(kana),
            )
            StepKind.LISTENING_CHOICE -> Task.Question(kana, QuestionType.LISTENING_CHOICE, optionsFor(step))
            StepKind.LISTENING_DRAWING -> Task.Question(kana, QuestionType.LISTENING_DRAWING, reference = strokes.strokesFor(kana))
        }
    }

    /**
     * The correct kana plus [OPTION_COUNT]-1 distractors: look-alikes first (so the learner must really
     * tell them apart), then other taught kana, then nearby untaught kana if too few are known yet.
     * Options never share a reading with the answer (じ/ぢ would make the question ambiguous).
     */
    private fun optionsFor(step: Step): List<Kana> {
        step.partner?.let { return listOf(step.kana, it).shuffled(random) }
        val target = step.kana
        val pool = pool().filter { it.id != target.id }
        val picked = mutableListOf<Kana>()
        fun tryAdd(k: Kana) {
            if (picked.size >= OPTION_COUNT - 1 || k.id == target.id || k.soundsLike(target)) return
            if (picked.any { it.id == k.id || it.soundsLike(k) }) return
            picked += k
        }
        Hiragana.lookAlikesOf(target).filter { it in pool }.shuffled(random).take(2).forEach(::tryAdd)
        pool.filter { it.group == target.group }.shuffled(random).forEach(::tryAdd)
        pool.shuffled(random).forEach(::tryAdd)
        Hiragana.all.filter { it.group == target.group }.sortedBy { abs(it.lesson - target.lesson) }.forEach(::tryAdd)
        Hiragana.all.forEach(::tryAdd)
        return (picked + target).shuffled(random)
    }

    private fun addInitialSteps(steps: List<Step>) {
        queue += steps
        cachedTask = null
    }

    companion object {
        const val OPTION_COUNT = 4
        const val DEFAULT_LEARN_BATCH = 5
        const val DEFAULT_REVIEW_LIMIT = 20
        private const val MAX_RETESTS = 3
        private const val RETEST_FOLLOW_UP_GAP = 2
        private const val LISTENING_HEADSTART_DROP = 2

        /** When each step of a new kana happens relative to its introduction: growing gaps. */
        private val LEARN_OFFSETS = intArrayOf(0, 1, 4, 8, 13, 17, 22, 28)
        private const val LEARN_STAGGER = 4

        fun normalizeTyped(text: String): String =
            text.trim().lowercase().filterNot { it.isWhitespace() || it == '\'' || it == '-' }

        fun isLearned(kana: Kana, states: Map<CardId, CardState>): Boolean =
            Skill.entries.any { states.containsKey(CardId(kana.id, it)) }

        /** The next kana to learn: the not-yet-learned ones of the first unfinished lesson. */
        fun nextNewKana(states: Map<CardId, CardState>, batchSize: Int = DEFAULT_LEARN_BATCH): List<Kana> {
            val lesson = Hiragana.lessons.firstOrNull { l -> l.kana.any { !isLearned(it, states) } } ?: return emptyList()
            return lesson.kana.filter { !isLearned(it, states) }.take(batchSize)
        }

        /** Due cards of the given [skills] (by default all), most overdue first. */
        fun dueCards(states: Map<CardId, CardState>, now: Long, skills: Set<Skill> = Skill.entries.toSet()): List<CardState> =
            states.values.filter { it.isDue(now) && it.id.skill in skills }.sortedBy { it.dueAt }

        /**
         * Listening cards for kana learned before listening existed (or while it was off). The learner has never been
         * tested on the sound, so the card starts [LISTENING_HEADSTART_DROP] levels below the reading card (like a
         * lapse: a learner who reads it well climbs back in a few answers) and is due now. Due times follow the
         * catalogue order, so the oldest kana come first in Review.
         */
        fun missingListeningCards(states: Map<CardId, CardState>, now: Long): List<CardState> {
            val missing = Hiragana.all.filter { isLearned(it, states) && CardId(it.id, Skill.LISTENING) !in states }
            return missing.mapIndexed { i, kana ->
                val reading = states[CardId(kana.id, Skill.READING)]
                val level = ((reading?.level ?: 1) - LISTENING_HEADSTART_DROP).coerceAtLeast(1)
                // One millisecond apart, the last one exactly at [now].
                val due = now - missing.lastIndex + i
                CardState(CardId(kana.id, Skill.LISTENING), level, due, now, null, 0, 0, 0, now)
            }
        }

        /**
         * Hearing a kana and writing it needs a drawable kana with a unique sound: after hearing "ji" the learner
         * can't know whether じ or ぢ is wanted, so sound-alikes are only asked as choice (where options never sound
         * the same).
         */
        private fun canDrawHeard(kana: Kana, drawingEnabled: Boolean, strokes: StrokeLibrary): Boolean =
            drawingEnabled && strokes.canDraw(kana) && Hiragana.soundAlikesOf(kana).isEmpty()

        fun learn(
            states: Map<CardId, CardState>,
            strokes: StrokeLibrary,
            drawingEnabled: Boolean = true,
            listeningEnabled: Boolean = true,
            batchSize: Int = DEFAULT_LEARN_BATCH,
            scheduler: Scheduler = Scheduler(),
            random: Random = Random.Default,
            newEventId: () -> String = { UUID.randomUUID().toString() },
        ): StudySession? {
            val kana = nextNewKana(states, batchSize)
            if (kana.isEmpty()) return null
            val skills = QuestionTypes(listening = listeningEnabled).skills
            val session = StudySession(SessionMode.LEARN, strokes, states, scheduler, random, drawingEnabled, skills, newEventId)
            val timed = kana.flatMapIndexed { i, k ->
                val drawable = drawingEnabled && strokes.canDraw(k)
                val ladder = buildList {
                    add(StepKind.INTRODUCE)
                    add(StepKind.READING_CHOICE)
                    add(StepKind.WRITING_CHOICE)
                    if (drawable) add(StepKind.WRITING_TRACE)
                    if (listeningEnabled) add(StepKind.LISTENING_CHOICE)
                    // Traced twice, so the hand learns the strokes before drawing them from memory.
                    if (drawable) add(StepKind.WRITING_TRACE)
                    add(StepKind.READING_TYPING)
                    add(if (drawable) StepKind.WRITING_DRAWING else StepKind.WRITING_CHOICE)
                }
                ladder.mapIndexed { j, kind -> Triple(i * LEARN_STAGGER + LEARN_OFFSETS[j], i, Step(k, kind)) }
            }
            session.addInitialSteps(timed.sortedWith(compareBy({ it.first }, { it.second })).map { it.third })
            return session
        }

        fun review(
            states: Map<CardId, CardState>,
            strokes: StrokeLibrary,
            now: Long,
            types: QuestionTypes = QuestionTypes(),
            limit: Int = DEFAULT_REVIEW_LIMIT,
            scheduler: Scheduler = Scheduler(),
            random: Random = Random.Default,
            newEventId: () -> String = { UUID.randomUUID().toString() },
        ): StudySession? {
            val due = dueCards(states, now, types.skills).take(limit)
            return testSession(SessionMode.REVIEW, due, states, strokes, types, scheduler, random, newEventId)
        }

        /** Learned cards for extra practice: weakest first, then the ones due soonest. */
        fun practiceCards(states: Map<CardId, CardState>, now: Long, skills: Set<Skill> = Skill.entries.toSet()): List<CardState> =
            states.values.filter { !it.isDue(now) && it.id.skill in skills }.sortedWith(compareBy({ it.level }, { it.dueAt }))

        fun practice(
            states: Map<CardId, CardState>,
            strokes: StrokeLibrary,
            now: Long,
            types: QuestionTypes = QuestionTypes(),
            limit: Int = DEFAULT_REVIEW_LIMIT,
            scheduler: Scheduler = Scheduler(),
            random: Random = Random.Default,
            newEventId: () -> String = { UUID.randomUUID().toString() },
        ): StudySession? {
            val cards = practiceCards(states, now, types.skills).take(limit)
            return testSession(SessionMode.PRACTICE, cards, states, strokes, types, scheduler, random, newEventId)
        }

        /**
         * One test per card, using only the enabled [types]. Review ramps up (choice first, typing/drawing for
         * stronger cards); Practice picks randomly among the enabled types at any level.
         */
        private fun testSession(
            mode: SessionMode,
            cards: List<CardState>,
            states: Map<CardId, CardState>,
            strokes: StrokeLibrary,
            types: QuestionTypes,
            scheduler: Scheduler,
            random: Random,
            newEventId: () -> String,
        ): StudySession? {
            if (cards.isEmpty()) return null
            val t = if (types.isValid) types else QuestionTypes(listening = types.listening)
            val session = StudySession(mode, strokes, states, scheduler, random, t.drawing, t.skills, newEventId)
            val steps = cards.shuffled(random).mapNotNull { card ->
                val kana = Hiragana.byId(card.id.kanaId) ?: return@mapNotNull null
                val canDraw = t.drawing && strokes.canDraw(kana)
                val kind = when (card.id.skill) {
                    Skill.READING -> {
                        val harder = if (mode == SessionMode.PRACTICE) random.nextBoolean()
                        else card.level >= 3 && random.nextFloat() < 0.6f
                        if (t.typing && (harder || !t.choice)) StepKind.READING_TYPING else StepKind.READING_CHOICE
                    }
                    Skill.WRITING -> {
                        val harder = if (mode == SessionMode.PRACTICE) random.nextBoolean() else card.level >= 2
                        if (canDraw && (harder || !t.choice)) StepKind.WRITING_DRAWING else StepKind.WRITING_CHOICE
                    }
                    // Writing what you hear is the hardest question, so Review saves it for well-known cards.
                    Skill.LISTENING -> {
                        val harder = if (mode == SessionMode.PRACTICE) random.nextBoolean() else card.level >= 3
                        if (canDrawHeard(kana, t.drawing, strokes) && (harder || !t.choice)) StepKind.LISTENING_DRAWING
                        else StepKind.LISTENING_CHOICE
                    }
                }
                Step(kana, kind)
            }
            session.addInitialSteps(spreadOut(steps))
            return session
        }

        /**
         * Contrast drill of the [pairs] the learner confuses (see [Confusions.rank]): each kana of a pair is asked
         * once for reading and once for writing, with only the pair as options. Returns null if there are no pairs.
         */
        fun drill(
            states: Map<CardId, CardState>,
            strokes: StrokeLibrary,
            pairs: List<ConfusionPair>,
            scheduler: Scheduler = Scheduler(),
            random: Random = Random.Default,
            newEventId: () -> String = { UUID.randomUUID().toString() },
        ): StudySession? {
            if (pairs.isEmpty()) return null
            val session = StudySession(SessionMode.DRILL, strokes, states, scheduler, random, drawingEnabled = false, Confusions.DRILLED_SKILLS, newEventId)
            val contrasts = pairs.flatMap { listOf(it.first to it.second, it.second to it.first) }
            // Two rounds (writing, then reading), each kana once per round, so no kana is asked twice in a row.
            val writing = contrasts.shuffled(random)
            val reading = contrasts.shuffled(random).toMutableList()
            if (reading.first().first == writing.last().first) reading.add(reading.removeAt(0))
            session.addInitialSteps(
                writing.map { (k, other) -> Step(k, StepKind.WRITING_CHOICE, other) } +
                    reading.map { (k, other) -> Step(k, StepKind.READING_CHOICE, other) },
            )
            return session
        }

        /** Reorders so the same kana is not asked twice in a row where possible. */
        private fun spreadOut(steps: List<Step>): List<Step> {
            val remaining = steps.toMutableList()
            val out = mutableListOf<Step>()
            while (remaining.isNotEmpty()) {
                val idx = remaining.indexOfFirst { it.kana != out.lastOrNull()?.kana }.takeIf { it >= 0 } ?: 0
                out += remaining.removeAt(idx)
            }
            return out
        }
    }
}
