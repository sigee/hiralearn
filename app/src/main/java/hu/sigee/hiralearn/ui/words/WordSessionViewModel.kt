package hu.sigee.hiralearn.ui.words

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import hu.sigee.hiralearn.AppContainer
import hu.sigee.hiralearn.audio.KanaVoice
import hu.sigee.hiralearn.core.AutoAdvance
import hu.sigee.hiralearn.core.SessionUpdate
import hu.sigee.hiralearn.core.WordAnswer
import hu.sigee.hiralearn.core.WordEvaluation
import hu.sigee.hiralearn.core.WordQuestion
import hu.sigee.hiralearn.core.WordSession
import hu.sigee.hiralearn.core.WordSummary
import hu.sigee.hiralearn.ui.session.AutoAdvanceTimer
import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface WordUiState {
    data object Loading : WordUiState

    /** No word can be read yet. */
    data object Empty : WordUiState

    /** @param stepKey changes with every step, so per-question UI state (typed text, drawings) resets. */
    data class Active(
        val question: WordQuestion,
        val stepKey: Int,
        val progress: Float,
        val evaluation: WordEvaluation?,
        val canSkipDrawing: Boolean,
    ) : WordUiState

    data class Finished(val summary: WordSummary) : WordUiState
}

class WordSessionViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<WordUiState>(WordUiState.Loading)
    val state: StateFlow<WordUiState> = _state.asStateFlow()

    private var session: WordSession? = null
    private var stepKey = 0
    private var evaluation: WordEvaluation? = null
    private val autoAdvance = AutoAdvanceTimer(viewModelScope)
    private val saveLock = Mutex()

    init {
        viewModelScope.launch {
            // As in SessionViewModel: give the text-to-speech engine a moment, so listening isn't left out.
            withTimeoutOrNull(VOICE_WAIT_MS) { container.voice.status.first { it != KanaVoice.Status.CHECKING } }
            val strokes = withContext(Dispatchers.Default) { container.strokes }
            val types = container.questionTypesNow()
            val states = if (types.listening) container.progress.addMissingListeningCards(container.clock()) else container.progress.snapshot()
            session = WordSession.start(states, strokes, types)
            publish()
        }
    }

    fun onAnswer(answer: WordAnswer) {
        val s = session ?: return
        if (evaluation != null || s.isFinished) return
        val result = s.evaluate(answer)
        evaluation = result
        publish()
        autoAdvance.schedule(AutoAdvance.delayAfter(result.correct, container.settings.autoAdvance.value)) { onContinue() }
    }

    fun onSkipDrawing() {
        val s = session ?: return
        if (evaluation != null) return
        s.disableDrawing()
        stepKey++
        publish()
    }

    /** Moves on after feedback. [acceptAsCorrect] = "I was right" (typo or handwriting misread). */
    fun onContinue(acceptAsCorrect: Boolean = false) {
        autoAdvance.cancel()
        val s = session ?: return
        val result = evaluation ?: return
        evaluation = null
        save(s.commit(result, container.clock(), acceptAsCorrect))
        stepKey++
        publish()
    }

    private fun publish() {
        val s = session
        _state.value = when {
            s == null -> WordUiState.Empty
            s.isFinished -> WordUiState.Finished(s.summary)
            else -> WordUiState.Active(s.current!!, stepKey, s.progress, evaluation, s.canSkipDrawing)
        }
    }

    /** Saved in the app scope, so leaving mid-session keeps the cards reset by misreads. */
    private fun save(update: SessionUpdate) {
        container.appScope.launch { saveLock.withLock { container.progress.save(update) } }
    }

    companion object {
        private const val VOICE_WAIT_MS = 2_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { WordSessionViewModel(container) }
        }
    }
}
