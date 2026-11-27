package hu.sigee.hiralearn.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackPanelModeTest {
    @Test
    fun openAlwaysStartsOpen() {
        assertFalse(FeedbackPanelMode.OPEN.startsFolded(lastFolded = false))
        assertFalse(FeedbackPanelMode.OPEN.startsFolded(lastFolded = true))
    }

    @Test
    fun foldedAlwaysStartsFolded() {
        assertTrue(FeedbackPanelMode.FOLDED.startsFolded(lastFolded = false))
        assertTrue(FeedbackPanelMode.FOLDED.startsFolded(lastFolded = true))
    }

    @Test
    fun rememberStartsAsTheLastMessageWasLeft() {
        assertFalse(FeedbackPanelMode.REMEMBER.startsFolded(lastFolded = false))
        assertTrue(FeedbackPanelMode.REMEMBER.startsFolded(lastFolded = true))
    }
}
