package hu.sigee.hiralearn.core

/** Shared fixtures for the core tests. */
internal object Fixtures {
    val strokes: StrokeLibrary by lazy { StrokeLibrary.loadBundled() }

    fun kana(char: String): Kana = Hiragana.byChar(char) ?: error("no kana $char")

    fun card(
        char: String,
        skill: Skill,
        level: Int = 3,
        due: Long = 0,
    ): CardState = CardState(CardId(kana(char).id, skill), level, due, 0, null, 0, 0, 0, 0)

    /** Both cards (reading + writing) of each kana, at [level] and due at [due]. */
    fun learned(vararg chars: String, level: Int = 3, due: Long = 0): Map<CardId, CardState> =
        chars.flatMap { c -> Skill.entries.map { card(c, it, level, due) } }.associateBy { it.id }

    fun correctAnswer(q: Task.Question): Answer = when (q.type) {
        QuestionType.READING_CHOICE, QuestionType.WRITING_CHOICE, QuestionType.LISTENING_CHOICE -> Answer.Choice(q.kana)
        QuestionType.READING_TYPING -> Answer.Typed(q.kana.romaji)
        QuestionType.WRITING_DRAWING, QuestionType.LISTENING_DRAWING -> Answer.Drawn(q.reference!!)
    }

    /** One finished step of a session, as seen by the caller. */
    data class Step(val task: Task, val evaluation: Evaluation?, val update: SessionUpdate)

    /** Drives [session] to the end, answering with [answer]; fails if it never ends. */
    fun runSession(
        session: StudySession,
        now: Long = 0,
        answer: (Task.Question) -> Answer = ::correctAnswer,
    ): List<Step> {
        val steps = mutableListOf<Step>()
        while (!session.isFinished) {
            check(steps.size < 1_000) { "session does not terminate" }
            when (val task = session.current!!) {
                is Task.Introduce -> steps += Step(task, null, session.acknowledgeIntroduction(now))
                is Task.Question -> {
                    val evaluation = session.evaluate(answer(task))
                    steps += Step(task, evaluation, session.commit(evaluation, now))
                }
            }
        }
        return steps
    }

    fun List<Step>.cards(): List<CardState> = flatMap { it.update.cards }
    fun List<Step>.events(): List<ReviewEvent> = flatMap { it.update.events }
    fun List<Step>.questions(): List<Task.Question> = mapNotNull { it.task as? Task.Question }

    /** Sequential ids, so tests can check exactly which events were produced. */
    fun counterIds(): () -> String {
        var n = 0
        return { "e${n++}" }
    }
}

/** Only the reading cards of [chars]: word sessions on these ask reading questions only. */
internal fun readingOnly(vararg chars: String, level: Int = 3): Map<CardId, CardState> =
    Fixtures.learned(*chars, level = level).filterKeys { it.skill == Skill.READING }

/** The right answer to a word question of any type. */
internal fun rightWordAnswer(q: WordQuestion): WordAnswer = when (q.type) {
    QuestionType.READING_CHOICE, QuestionType.WRITING_CHOICE, QuestionType.LISTENING_CHOICE ->
        WordAnswer.Choice(q.options.single { it.misread == null })
    QuestionType.READING_TYPING -> WordAnswer.Typed(q.word.romaji)
    QuestionType.WRITING_DRAWING, QuestionType.LISTENING_DRAWING ->
        WordAnswer.Drawn(q.word.kana.map { Fixtures.strokes.strokesFor(it)!! })
}
