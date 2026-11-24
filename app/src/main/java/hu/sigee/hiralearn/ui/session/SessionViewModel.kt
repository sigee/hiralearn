package hu.sigee.hiralearn.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import hu.sigee.hiralearn.AppContainer
import hu.sigee.hiralearn.audio.KanaVoice
import hu.sigee.hiralearn.core.Answer
import hu.sigee.hiralearn.core.Evaluation
import hu.sigee.hiralearn.core.QuestionType
import hu.sigee.hiralearn.core.SessionMode
import hu.sigee.hiralearn.core.SessionSummary
import hu.sigee.hiralearn.core.SessionUpdate
import hu.sigee.hiralearn.core.StudySession
import hu.sigee.hiralearn.core.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SessionUiState {
    data object Loading : SessionUiState

    /** Nothing to do (no due reviews / everything learned). */
    data class Empty(val mode: SessionMode) : SessionUiState

    /**
     * @param stepKey changes with every step, so per-question UI state (typed text, strokes) resets.
     * @param evaluation set once the learner answered and feedback is shown.
     */
    data class Active(
        val mode: SessionMode,
        val task: Task,
        val stepKey: Int,
        val progress: Float,
        val evaluation: Evaluation?,
        val canSkipDrawing: Boolean,
    ) : SessionUiState

    data class Finished(val summary: SessionSummary) : SessionUiState
}

class SessionViewModel(private val mode: SessionMode, private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<SessionUiState>(SessionUiState.Loading)
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private var session: StudySession? = null
    private var stepKey = 0
    private var evaluation: Evaluation? = null
    private var autoAdvance: Job? = null
    private val saveLock = Mutex()

    init {
        viewModelScope.launch {
            // The text-to-speech engine takes a moment to start; wait for it briefly so listening isn't left out.
            withTimeoutOrNull(VOICE_WAIT_MS) { container.voice.status.first { it != KanaVoice.Status.CHECKING } }
            val strokes = withContext(Dispatchers.Default) { container.strokes }
            val types = container.questionTypesNow()
            val states = if (types.listening) container.progress.addMissingListeningCards(container.clock()) else container.progress.snapshot()
            session = when (mode) {
                SessionMode.LEARN -> StudySession.learn(states, strokes, drawingEnabled = types.drawing, listeningEnabled = types.listening)
                SessionMode.REVIEW -> StudySession.review(states, strokes, now = container.clock(), types = types)
                SessionMode.PRACTICE -> StudySession.practice(states, strokes, now = container.clock(), types = types)
                // Words have their own screen (WordSessionViewModel).
                SessionMode.WORDS -> null
                SessionMode.DRILL -> StudySession.drill(states, strokes, container.progress.confusionsSnapshot(container.clock()))
            }
            publish()
        }
    }

    fun onIntroductionSeen() {
        val s = session ?: return
        if (s.current !is Task.Introduce) return
        save(s.acknowledgeIntroduction(container.clock()))
        stepKey++
        publish()
    }

    fun onAnswer(answer: Answer) {
        val s = session ?: return
        if (evaluation != null || s.current !is Task.Question) return
        val result = s.evaluate(answer)
        evaluation = result
        publish()
        // Like Memrise: right answers to quick questions move on by themselves.
        val drawn = result.question.type == QuestionType.WRITING_DRAWING || result.question.type == QuestionType.LISTENING_DRAWING
        if (result.correct && !drawn) {
            autoAdvance = viewModelScope.launch {
                delay(AUTO_ADVANCE_MS)
                onContinue()
            }
        }
    }

    /** Moves on after feedback. [acceptAsCorrect] = "I was right" (typo or handwriting misread). */
    fun onContinue(acceptAsCorrect: Boolean = false) {
        autoAdvance?.cancel()
        autoAdvance = null
        val s = session ?: return
        val result = evaluation ?: return
        evaluation = null
        save(s.commit(result, container.clock(), acceptAsCorrect))
        stepKey++
        publish()
    }

    fun onSkipDrawing() {
        val s = session ?: return
        if (evaluation != null) return
        s.disableDrawing()
        stepKey++
        publish()
    }

    private fun publish() {
        val s = session
        _state.value = when {
            s == null -> SessionUiState.Empty(mode)
            s.isFinished -> SessionUiState.Finished(s.summary)
            else -> SessionUiState.Active(mode, s.current!!, stepKey, s.progress, evaluation, s.canSkipDrawing)
        }
    }

    /** Saved in the app scope after every step, so quitting mid-session keeps all progress made so far. */
    private fun save(update: SessionUpdate) {
        container.appScope.launch { saveLock.withLock { container.progress.save(update) } }
    }

    companion object {
        private const val AUTO_ADVANCE_MS = 900L
        private const val VOICE_WAIT_MS = 2_000L

        fun factory(mode: SessionMode, container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SessionViewModel(mode, container) }
        }
    }
}
