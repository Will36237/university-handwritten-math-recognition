package vn.edu.fpt.hmerdemo.ui.recognition

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModelControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun uniOnlyModeShowsOnlyUniMumerAction() {
        var tamerCalls = 0
        var uniCalls = 0
        var compareCalls = 0
        composeRule.setContent {
            ModelControls(
                mode = RecognitionModelMode.UNI_ONLY,
                enabled = true,
                onRunTamer = { tamerCalls += 1 },
                onRunUni = { uniCalls += 1 },
                onRunBoth = { compareCalls += 1 },
            )
        }

        composeRule.onNodeWithText("Uni-MuMER").assertIsDisplayed().performClick()
        composeRule.onAllNodesWithText("TAMER-A3").assertCountEquals(0)
        composeRule.onAllNodesWithText("So sánh models").assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(0, tamerCalls)
            assertEquals(1, uniCalls)
            assertEquals(0, compareCalls)
        }
    }

    @Test
    fun allModelsModeShowsEveryModelAction() {
        var tamerCalls = 0
        var uniCalls = 0
        var compareCalls = 0
        composeRule.setContent {
            ModelControls(
                mode = RecognitionModelMode.ALL_MODELS,
                enabled = true,
                onRunTamer = { tamerCalls += 1 },
                onRunUni = { uniCalls += 1 },
                onRunBoth = { compareCalls += 1 },
            )
        }

        composeRule.onNodeWithText("TAMER-A3").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Uni-MuMER").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("So sánh models").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, tamerCalls)
            assertEquals(1, uniCalls)
            assertEquals(1, compareCalls)
        }
    }
}
