package hu.sigee.hiralearn.ui.session

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import hu.sigee.hiralearn.R
import hu.sigee.hiralearn.appContainer
import hu.sigee.hiralearn.data.FeedbackPanelMode
import hu.sigee.hiralearn.data.SettingsStore
import hu.sigee.hiralearn.ui.theme.HiraLearnTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Folding the answer message. Uses the app's real settings, so the learner's own choice is put back afterwards. */
@RunWith(AndroidJUnit4::class)
class FeedbackPanelTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings: SettingsStore = context.appContainer.settings

    // From the activity, which has the app's own language (Settings → Language); the application context may not.
    private val hide get() = compose.activity.getString(R.string.hide_feedback)
    private val show get() = compose.activity.getString(R.string.show_feedback)
    private lateinit var savedMode: FeedbackPanelMode
    private var savedFolded = false

    /** A new question number recreates the panel, as the session screens do for every question. */
    private var question by mutableIntStateOf(0)

    @Before
    fun saveSettings() {
        savedMode = settings.feedbackMode.value
        savedFolded = settings.feedbackLastFolded
    }

    @After
    fun restoreSettings() {
        settings.setFeedbackMode(savedMode)
        settings.setFeedbackFolded(savedFolded)
    }

    private fun showPanel() {
        compose.setContent {
            HiraLearnTheme {
                key(question) {
                    FeedbackPanel(
                        correct = false,
                        seed = 1,
                        summary = { Text("SUMMARY") },
                        details = { Text("DETAILS") },
                        actions = { Text("CONTINUE") },
                    )
                }
            }
        }
    }

    @Test
    fun foldingHidesTheMessageButKeepsTheButtons() {
        settings.setFeedbackMode(FeedbackPanelMode.OPEN)
        showPanel()
        compose.onNodeWithText("DETAILS").assertIsDisplayed()

        compose.onNodeWithContentDescription(hide).performClick()
        compose.onNodeWithText("DETAILS").assertDoesNotExist()
        compose.onNodeWithText("SUMMARY").assertDoesNotExist()
        compose.onNodeWithText("CONTINUE").assertIsDisplayed()

        compose.onNodeWithContentDescription(show).performClick()
        compose.onNodeWithText("DETAILS").assertIsDisplayed()
        compose.onNodeWithText("SUMMARY").assertIsDisplayed()
    }

    @Test
    fun openModeStartsOpenEvenAfterFolding() {
        settings.setFeedbackMode(FeedbackPanelMode.OPEN)
        showPanel()
        compose.onNodeWithContentDescription(hide).performClick()

        question++
        compose.onNodeWithText("DETAILS").assertIsDisplayed()
    }

    @Test
    fun foldedModeStartsFolded() {
        settings.setFeedbackMode(FeedbackPanelMode.FOLDED)
        settings.setFeedbackFolded(false)
        showPanel()
        compose.onNodeWithText("DETAILS").assertDoesNotExist()
        compose.onNodeWithText("CONTINUE").assertIsDisplayed()
    }

    @Test
    fun rememberModeStartsTheNextQuestionAsTheLastWasLeft() {
        settings.setFeedbackMode(FeedbackPanelMode.REMEMBER)
        settings.setFeedbackFolded(false)
        showPanel()
        compose.onNodeWithText("DETAILS").assertIsDisplayed()

        compose.onNodeWithContentDescription(hide).performClick()
        question++
        compose.onNodeWithText("DETAILS").assertDoesNotExist()

        compose.onNodeWithContentDescription(show).performClick()
        question++
        compose.onNodeWithText("DETAILS").assertIsDisplayed()
    }
}
