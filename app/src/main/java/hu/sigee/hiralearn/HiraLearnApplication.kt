package hu.sigee.hiralearn

import android.app.Application
import android.content.Context
import hu.sigee.hiralearn.audio.KanaVoice
import hu.sigee.hiralearn.core.QuestionTypes
import hu.sigee.hiralearn.core.StrokeLibrary
import hu.sigee.hiralearn.data.HiraLearnDatabase
import hu.sigee.hiralearn.data.ProgressRepository
import hu.sigee.hiralearn.data.SettingsStore
import hu.sigee.hiralearn.reminder.ReviewReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HiraLearnApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Parse the stroke data off the main thread before the first screen needs it.
        container.appScope.launch(Dispatchers.Default) { container.strokes }
        // Re-arms the daily reminder if the system dropped it (e.g. after a force stop); keeps an existing one.
        val reminder = container.settings.reminder.value
        if (reminder.enabled) ReviewReminder.apply(this, reminder, container.clock(), replace = false)
        // Kana learned without listening get their listening card as soon as listening is possible.
        container.appScope.launch {
            container.questionTypes.map { it.listening }.distinctUntilChanged().filter { it }.collect {
                container.progress.addMissingListeningCards(container.clock())
            }
        }
    }
}

/** Manual dependency container; small enough that a DI framework isn't worth it. */
class AppContainer(context: Context) {
    /** Outlives screens, so saving progress is never cancelled when the user leaves a session. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val clock: () -> Long = System::currentTimeMillis

    private val database by lazy { HiraLearnDatabase.create(context) }
    val progress by lazy { ProgressRepository(database.progressDao()) }
    val settings = SettingsStore(context)
    val strokes: StrokeLibrary by lazy { StrokeLibrary.loadBundled() }
    val voice = KanaVoice(context)

    /** The question types actually asked: the learner's choice, with listening only while a Japanese voice works. */
    val questionTypes: StateFlow<QuestionTypes> =
        combine(settings.questionTypes, voice.status, ::effectiveTypes)
            .stateIn(appScope, SharingStarted.Eagerly, effectiveTypes(settings.questionTypes.value, voice.status.value))

    /** [questionTypes] as of right now, without waiting for the flow to catch up. */
    fun questionTypesNow(): QuestionTypes = effectiveTypes(settings.questionTypes.value, voice.status.value)

    private fun effectiveTypes(types: QuestionTypes, voice: KanaVoice.Status): QuestionTypes =
        types.copy(listening = types.listening && voice == KanaVoice.Status.READY)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as HiraLearnApplication).container
