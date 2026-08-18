package vn.edu.fpt.hmerdemo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.edu.fpt.hmerdemo.ui.recognition.RecognitionModelMode

@RunWith(AndroidJUnit4::class)
class HmerDemoSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingSkipOpensRecognitionWorkspace() {
        composeRule.onNodeWithText("University HMER").assertIsDisplayed()
        composeRule.onNodeWithText("Bỏ qua").performClick()
        composeRule.onNodeWithText("T\u1ed5ng quan", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ch\u1ee5p \u1ea3nh").assertIsDisplayed()
        composeRule.onNodeWithText("Th\u01b0 vi\u1ec7n").assertIsDisplayed()
        composeRule.onNodeWithText("D\u00f9ng \u1ea3nh m\u1eabu").assertIsDisplayed()
        composeRule.onAllNodesWithText("Uni-MuMER LoRA").assertCountEquals(2)
        when (RecognitionModelMode.fromConfig(BuildConfig.HMER_MODEL_UI_MODE)) {
            RecognitionModelMode.UNI_ONLY -> {
                composeRule.onAllNodesWithText("TAMER-A3").assertCountEquals(0)
                composeRule.onAllNodesWithText("So s\u00e1nh models").assertCountEquals(0)
            }
            RecognitionModelMode.ALL_MODELS -> {
                composeRule.onAllNodesWithText("TAMER-A3").assertCountEquals(2)
                composeRule.onNodeWithText("So s\u00e1nh models").assertIsDisplayed()
            }
        }
        composeRule.onNodeWithText("D\u00f9ng \u1ea3nh m\u1eabu").performClick()
        composeRule.onNodeWithText("C\u1eaft v\u00f9ng c\u00f4ng th\u1ee9c")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("X\u00f3a \u1ea3nh")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun onboardingSampleStartOpensWorkspaceWithImage() {
        repeat(2) {
            composeRule.onNodeWithText("Ti\u1ebfp t\u1ee5c").performClick()
        }

        composeRule.onNodeWithText("B\u1eaft \u0111\u1ea7u v\u1edbi \u1ea3nh m\u1eabu")
            .performClick()
        composeRule.onNodeWithText("1. \u1ea2nh g\u1ed1c")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
