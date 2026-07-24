package vn.edu.fpt.hmerdemo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import vn.edu.fpt.hmerdemo.ui.onboarding.OnboardingScreen
import vn.edu.fpt.hmerdemo.ui.recognition.RecognitionScreen


@Composable
fun HmerDemoApp() {
    var showOnboarding by remember { mutableStateOf(true) }
    var recognitionStartMode by remember {
        mutableStateOf(RecognitionStartMode.EMPTY)
    }

    if (showOnboarding) {
        OnboardingScreen(
            onStart = { startMode ->
                recognitionStartMode = startMode
                showOnboarding = false
            },
        )
    } else {
        RecognitionScreen(
            startMode = recognitionStartMode,
            onBackToOverview = { showOnboarding = true },
        )
    }
}
