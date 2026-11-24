package hu.sigee.hiralearn.audio

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import hu.sigee.hiralearn.core.Kana
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Says kana aloud with the phone's Japanese text-to-speech. It adds nothing to the APK and needs no licence, but a
 * phone may have no Japanese voice: then [status] becomes [Status.UNAVAILABLE] and the listening skill is turned off.
 */
class KanaVoice(context: Context) {
    enum class Status { CHECKING, READY, UNAVAILABLE }

    private val appContext = context.applicationContext
    private val _status = MutableStateFlow(Status.CHECKING)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var engine: TextToSpeech? = null

    init {
        connect()
    }

    private fun connect() {
        _status.value = Status.CHECKING
        engine?.shutdown()
        engine = null
        // The listener can run before the constructor returns (no engine at all), so it never touches [engine]
        // in the error case.
        lateinit var tts: TextToSpeech
        tts = TextToSpeech(appContext) { result ->
            if (result != TextToSpeech.SUCCESS) {
                _status.value = Status.UNAVAILABLE
                return@TextToSpeech
            }
            engine = tts
            useJapanese(tts)
        }
    }

    private fun useJapanese(tts: TextToSpeech) {
        val ok = runCatching { tts.setLanguage(Locale.JAPANESE) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false)
        if (ok) {
            // A little slower than normal speech: learners hear a single sound, not a sentence.
            tts.setSpeechRate(SPEECH_RATE)
        }
        _status.value = if (ok) Status.READY else Status.UNAVAILABLE
    }

    /** Looks again, e.g. after the learner installed a Japanese voice. */
    fun refresh() {
        val tts = engine
        if (tts == null) connect() else useJapanese(tts)
    }

    fun speak(kana: Kana) = speak(kana.char, kana.id)

    /** Says [text] (kana or a word in kana); [utteranceId] names it for the engine. */
    fun speak(text: String, utteranceId: String) {
        if (_status.value != Status.READY) return
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        engine?.stop()
    }

    /** Opens the screen that installs voice data, or the text-to-speech settings. Returns false if neither exists. */
    fun openInstall(context: Context): Boolean {
        val intents = listOf(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA), Intent("com.android.settings.TTS_SETTINGS"))
        return intents.any { intent ->
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }

    private companion object {
        const val SPEECH_RATE = 0.85f
    }
}
