package hu.sigee.hiralearn.ui.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs the "move on" action after a delay (see [hu.sigee.hiralearn.core.AutoAdvance]). Tapping Continue must [cancel] it:
 * otherwise a timer left over from one question would skip the feedback of the next one.
 */
class AutoAdvanceTimer(private val scope: CoroutineScope) {
    private var job: Job? = null

    val isPending: Boolean get() = job != null

    /** Replaces any pending run; a null [delayMs] only cancels. */
    fun schedule(delayMs: Long?, action: () -> Unit) {
        cancel()
        if (delayMs == null) return
        job = scope.launch {
            delay(delayMs)
            job = null
            action()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
