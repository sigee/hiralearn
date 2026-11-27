package hu.sigee.hiralearn.data

import android.content.Context
import androidx.core.content.edit
import hu.sigee.hiralearn.core.CardId
import hu.sigee.hiralearn.core.CardState
import hu.sigee.hiralearn.core.ConfusionPair
import hu.sigee.hiralearn.core.Confusions
import hu.sigee.hiralearn.core.DrillAnswer
import hu.sigee.hiralearn.core.Hiragana
import hu.sigee.hiralearn.core.QuestionType
import hu.sigee.hiralearn.core.Scheduler
import hu.sigee.hiralearn.core.SessionMode
import hu.sigee.hiralearn.core.Skill
import java.util.UUID
import hu.sigee.hiralearn.core.MixUp
import kotlinx.coroutines.flow.combine
import hu.sigee.hiralearn.core.QuestionTypes
import hu.sigee.hiralearn.core.Reminder
import hu.sigee.hiralearn.core.ReviewEvent
import hu.sigee.hiralearn.core.SessionUpdate
import java.time.LocalTime
import hu.sigee.hiralearn.core.StudySession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * The single source of truth for learning progress. Everything is stored locally first;
 * a sync layer can later push unsynced review events and pull cards without changing callers.
 */
class ProgressRepository(private val dao: ProgressDao) {

    val cards: Flow<Map<CardId, CardState>> = dao.observeCards().map { list -> list.associate { it.toCore().let { s -> s.id to s } } }

    suspend fun snapshot(): Map<CardId, CardState> = dao.cards().associate { it.toCore().let { s -> s.id to s } }

    fun answersSince(since: Long): Flow<Int> = dao.observeAnswerCountSince(since)

    /** The ranked confusion pairs at the time given by [clock]; updates whenever an answer is logged. */
    fun confusions(clock: Flow<Long>): Flow<List<ConfusionPair>> =
        combine(dao.observeMixUps(), dao.observeDrillAnswers(), clock) { mixUps, drilled, now ->
            Confusions.rank(mixUps.map { it.toCore() }, now, drilled.map { it.toCore() })
        }

    suspend fun confusionsSnapshot(now: Long): List<ConfusionPair> =
        Confusions.rank(dao.mixUps().map { it.toCore() }, now, dao.drillAnswers().map { it.toCore() })

    suspend fun save(update: SessionUpdate) {
        if (update.cards.isEmpty() && update.events.isEmpty()) return
        dao.save(update.cards.map { it.toEntity() }, update.events.map { it.toEntity() })
    }

    /**
     * Gives kana learned without listening (before it existed, or while it was off) their listening card.
     * Returns the cards after the change.
     */
    suspend fun addMissingListeningCards(now: Long): Map<CardId, CardState> {
        val states = snapshot()
        val added = StudySession.missingListeningCards(states, now)
        if (added.isEmpty()) return states
        dao.insertCardsIfAbsent(added.map { it.toEntity() })
        return snapshot()
    }

    suspend fun resetAll() = dao.deleteAll()

    /** Debug builds only: makes every learned card due now. */
    suspend fun makeAllDue(now: Long): Int = dao.makeAllDue(now)

    /**
     * Debug builds only: replaces all progress with that of a learner a few weeks in (store screenshots, manual
     * testing). Earlier lessons are stronger, the newest ones still pencil, some writing cards are due, and a few
     * classic look-alike pairs have been mixed up.
     */
    suspend fun seedDemo(now: Long) {
        val scheduler = Scheduler()
        val levels = listOf(8, 7, 6, 5, 3, 2, 1)
        val cards = Hiragana.lessons.take(levels.size).flatMap { lesson ->
            val level = levels[lesson.index]
            lesson.kana.flatMap { kana ->
                Skill.entries.map { skill ->
                    val due = level <= 3 && skill == Skill.WRITING
                    val interval = scheduler.intervalFor(level)
                    CardEntity(
                        kanaId = kana.id,
                        skill = skill,
                        level = if (skill == Skill.LISTENING) (level - 1).coerceAtLeast(1) else level,
                        dueAt = if (due) now - MINUTE else now + interval / 2,
                        introducedAt = now - (levels.size - lesson.index) * 4 * DAY,
                        lastReviewedAt = now - interval / 2,
                        timesCorrect = level * 2,
                        timesWrong = if (level < 4) 2 else 1,
                        lapses = if (level < 4) 1 else 0,
                        updatedAt = now,
                    )
                }
            }
        }
        val kanaByChar = Hiragana.all.associateBy { it.char }
        val mixUps = listOf("ぬ" to "め", "ぬ" to "め", "ぬ" to "め", "さ" to "ち", "さ" to "ち", "は" to "ほ", "あ" to "お")
        val events = mixUps.mapIndexed { i, (asked, picked) ->
            ReviewEventEntity(
                id = UUID.randomUUID().toString(),
                kanaId = kanaByChar.getValue(asked).id,
                skill = Skill.READING,
                questionType = QuestionType.READING_CHOICE,
                correct = false,
                confusedWithKanaId = kanaByChar.getValue(picked).id,
                mode = SessionMode.REVIEW,
                answeredAt = now - (i + 1) * 6 * HOUR,
            )
        }
        dao.deleteAll()
        dao.save(cards, events)
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}

private fun MixUpRow.toCore() = MixUp(kanaId, confusedWithKanaId, answeredAt)

private fun DrillAnswerRow.toCore() = DrillAnswer(kanaId, skill, answeredAt)

private fun CardEntity.toCore() = CardState(
    id = CardId(kanaId, skill),
    level = level,
    dueAt = dueAt,
    introducedAt = introducedAt,
    lastReviewedAt = lastReviewedAt,
    timesCorrect = timesCorrect,
    timesWrong = timesWrong,
    lapses = lapses,
    updatedAt = updatedAt,
)

private fun CardState.toEntity() = CardEntity(
    kanaId = id.kanaId,
    skill = id.skill,
    level = level,
    dueAt = dueAt,
    introducedAt = introducedAt,
    lastReviewedAt = lastReviewedAt,
    timesCorrect = timesCorrect,
    timesWrong = timesWrong,
    lapses = lapses,
    updatedAt = updatedAt,
)

private fun ReviewEvent.toEntity() = ReviewEventEntity(
    id = id,
    kanaId = kanaId,
    skill = skill,
    questionType = questionType,
    correct = correct,
    confusedWithKanaId = confusedWithKanaId,
    mode = mode,
    answeredAt = answeredAt,
)

/** Light or dark ("night ink") look; SYSTEM follows the phone setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How the answer message under a question starts: always open, always folded, or as the learner last left it. */
enum class FeedbackPanelMode {
    OPEN,
    FOLDED,
    REMEMBER,
    ;

    /** Whether the message starts folded, given whether the learner left the previous one [lastFolded]. */
    fun startsFolded(lastFolded: Boolean): Boolean = when (this) {
        OPEN -> false
        FOLDED -> true
        REMEMBER -> lastFolded
    }
}

/** Whether the daily review reminder is on, and at what time of day. */
data class ReminderSettings(val enabled: Boolean = false, val time: LocalTime = Reminder.DEFAULT_TIME)

/** Small user preferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _themeMode.value = mode
    }
    private val _questionTypes = MutableStateFlow(
        QuestionTypes(
            choice = prefs.getBoolean(KEY_CHOICE, true),
            typing = prefs.getBoolean(KEY_TYPING, true),
            drawing = prefs.getBoolean(KEY_DRAWING, true),
            listening = prefs.getBoolean(KEY_LISTENING, true),
        ).takeIf { it.isValid } ?: QuestionTypes(),
    )

    /**
     * Question types the learner chose for Review and Practice. Drawing and listening also control the matching steps
     * of Learn. Listening needs audio too: use [hu.sigee.hiralearn.AppContainer.questionTypes] for what is asked.
     * Invalid combinations (a skill with no type left) are rejected.
     */
    val questionTypes: StateFlow<QuestionTypes> = _questionTypes.asStateFlow()

    fun setQuestionTypes(types: QuestionTypes) {
        if (!types.isValid) return
        prefs.edit {
            putBoolean(KEY_CHOICE, types.choice)
            putBoolean(KEY_TYPING, types.typing)
            putBoolean(KEY_DRAWING, types.drawing)
            putBoolean(KEY_LISTENING, types.listening)
        }
        _questionTypes.value = types
    }

    private val _autoAdvance = MutableStateFlow(prefs.getBoolean(KEY_AUTO_ADVANCE, false))

    /**
     * Whether a right answer moves on to the next question by itself. Off by default, so every task ends the same way:
     * the learner reads the feedback and taps Continue. Wrong answers always wait.
     */
    val autoAdvance: StateFlow<Boolean> = _autoAdvance.asStateFlow()

    fun setAutoAdvance(on: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_ADVANCE, on) }
        _autoAdvance.value = on
    }

    private val _reminder = MutableStateFlow(
        ReminderSettings(
            enabled = prefs.getBoolean(KEY_REMINDER_ON, false),
            time = runCatching { LocalTime.ofSecondOfDay(prefs.getInt(KEY_REMINDER_MINUTE, -1) * 60L) }.getOrDefault(Reminder.DEFAULT_TIME),
        ),
    )

    /** Daily review reminder. Off by default: notifications are opt-in. */
    val reminder: StateFlow<ReminderSettings> = _reminder.asStateFlow()

    fun setReminder(settings: ReminderSettings) {
        prefs.edit {
            putBoolean(KEY_REMINDER_ON, settings.enabled)
            putInt(KEY_REMINDER_MINUTE, settings.time.hour * 60 + settings.time.minute)
        }
        _reminder.value = settings
    }

    private val _feedbackMode = MutableStateFlow(
        runCatching { FeedbackPanelMode.valueOf(prefs.getString(KEY_FEEDBACK_MODE, null) ?: "") }.getOrDefault(FeedbackPanelMode.REMEMBER),
    )
    val feedbackMode: StateFlow<FeedbackPanelMode> = _feedbackMode.asStateFlow()

    fun setFeedbackMode(mode: FeedbackPanelMode) {
        prefs.edit { putString(KEY_FEEDBACK_MODE, mode.name) }
        _feedbackMode.value = mode
    }

    /** Whether the next answer message starts folded, according to [feedbackMode]. */
    fun feedbackStartsFolded(): Boolean = _feedbackMode.value.startsFolded(feedbackLastFolded)

    /** Whether the learner left the last answer message folded; used by [FeedbackPanelMode.REMEMBER]. */
    val feedbackLastFolded: Boolean get() = prefs.getBoolean(KEY_FEEDBACK_FOLDED, false)

    fun setFeedbackFolded(folded: Boolean) {
        prefs.edit { putBoolean(KEY_FEEDBACK_FOLDED, folded) }
    }

    private companion object {
        const val KEY_FEEDBACK_MODE = "feedback_panel_mode"
        const val KEY_FEEDBACK_FOLDED = "feedback_panel_folded"
        const val KEY_REMINDER_ON = "reminder_enabled"
        const val KEY_REMINDER_MINUTE = "reminder_minute_of_day"
        const val KEY_CHOICE = "choice_enabled"
        const val KEY_TYPING = "typing_enabled"
        const val KEY_DRAWING = "drawing_enabled"
        const val KEY_LISTENING = "listening_enabled"
        const val KEY_THEME = "theme_mode"
        const val KEY_AUTO_ADVANCE = "auto_advance"
    }
}
