package hu.sigee.hiralearn.core

/**
 * When the feedback after an answer goes away by itself. Only a right answer does, and only if the learner turned it on
 * (Settings); every question type behaves alike, so the session never stops for some tasks and not for others.
 * A wrong answer always waits for Continue, so the learner can see what went wrong.
 */
object AutoAdvance {
    /** Long enough to read the feedback (and see a drawing next to the model) before it goes away. */
    const val DELAY_MS = 3_000L

    /** How long to wait before moving on by itself, or null when the learner moves on with Continue. */
    fun delayAfter(correct: Boolean, enabled: Boolean): Long? = if (correct && enabled) DELAY_MS else null
}
