package vn.edu.fpt.hmerdemo.ui.onboarding

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.edu.fpt.hmerdemo.R
import vn.edu.fpt.hmerdemo.ui.AppBackground
import vn.edu.fpt.hmerdemo.ui.Ink
import vn.edu.fpt.hmerdemo.ui.Muted
import vn.edu.fpt.hmerdemo.ui.RecognitionStartMode
import vn.edu.fpt.hmerdemo.ui.TamerBlue
import vn.edu.fpt.hmerdemo.ui.TamerSoft
import vn.edu.fpt.hmerdemo.ui.UniPurple
import vn.edu.fpt.hmerdemo.ui.UniSoft
import vn.edu.fpt.hmerdemo.ui.recognition.FormulaImageBox

@Composable
fun OnboardingScreen(onStart: (RecognitionStartMode) -> Unit) {
    var page by remember { mutableStateOf(0) }

    Scaffold(containerColor = AppBackground) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.brand_university_hmer),
                    color = TamerBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (page < 2) {
                    OutlinedButton(
                        onClick = { onStart(RecognitionStartMode.EMPTY) },
                    ) {
                        Text(stringResource(R.string.action_skip))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (page) {
                    0 -> ProblemStoryPage()
                    1 -> ModelStoryPage()
                    else -> ExperienceStoryPage()
                }
            }

            PageIndicator(currentPage = page, pageCount = 3)
            Spacer(Modifier.height(18.dp))

            if (page < 2) {
                Button(
                    onClick = { page += 1 },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.action_continue))
                }
            } else {
                Button(
                    onClick = { onStart(RecognitionStartMode.EMPTY) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.action_start_experience))
                }
                Spacer(Modifier.height(9.dp))
                OutlinedButton(
                    onClick = { onStart(RecognitionStartMode.SAMPLE_IMAGE) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.action_start_with_sample))
                }
            }
        }
    }
}
@Composable
private fun ProblemStoryPage() {
    val context = LocalContext.current
    val realHandwritingSample = remember(context) {
        Uri.parse("android.resource://${context.packageName}/${R.drawable.sample_hard_01}")
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StoryTitle(
            eyebrow = stringResource(R.string.onboarding_problem_eyebrow),
            title = stringResource(R.string.onboarding_problem_title),
            description = stringResource(R.string.onboarding_problem_description),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FormulaImageBox(
                    label = stringResource(R.string.onboarding_sample_label),
                    imageUri = realHandwritingSample,
                )
                HorizontalDivider(color = Color(0xFFE8ECF2))
                Text(
                    text = stringResource(R.string.onboarding_pipeline),
                    color = Ink,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                stringResource(R.string.onboarding_metric_real_images_value),
                stringResource(R.string.onboarding_metric_real_images_label),
                Modifier.weight(1f),
            )
            MetricCard(
                stringResource(R.string.onboarding_metric_datasets_value),
                stringResource(R.string.onboarding_metric_datasets_label),
                Modifier.weight(1f),
            )
            MetricCard(
                stringResource(R.string.onboarding_metric_blind_test_value),
                stringResource(R.string.onboarding_metric_blind_test_label),
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModelStoryPage() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StoryTitle(
            eyebrow = stringResource(R.string.onboarding_models_eyebrow),
            title = stringResource(R.string.onboarding_models_title),
            description = stringResource(R.string.onboarding_models_description),
        )
        ModelStoryCard(
            title = stringResource(R.string.model_tamer_title),
            badge = stringResource(R.string.model_tamer_badge),
            description = stringResource(R.string.model_tamer_description),
            stats = stringResource(R.string.model_tamer_stats),
            accent = TamerBlue,
            softColor = TamerSoft,
        )
        ModelStoryCard(
            title = stringResource(R.string.model_unimumer_title),
            badge = stringResource(R.string.model_unimumer_badge),
            description = stringResource(R.string.model_unimumer_description),
            stats = stringResource(R.string.model_unimumer_stats),
            accent = UniPurple,
            softColor = UniSoft,
        )
        Text(
            text = stringResource(R.string.onboarding_models_comparison),
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun ExperienceStoryPage() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StoryTitle(
            eyebrow = stringResource(R.string.onboarding_experience_eyebrow),
            title = stringResource(R.string.onboarding_experience_title),
            description = stringResource(R.string.onboarding_experience_description),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StoryStep(
                    "01",
                    stringResource(R.string.onboarding_step_select_image_title),
                    stringResource(R.string.onboarding_step_select_image_description),
                    TamerBlue,
                )
                StoryConnector()
                StoryStep(
                    "02",
                    stringResource(R.string.onboarding_step_crop_title),
                    stringResource(R.string.onboarding_step_crop_description),
                    TamerBlue,
                )
                StoryConnector()
                StoryStep(
                    "03",
                    stringResource(R.string.onboarding_step_compare_title),
                    stringResource(R.string.onboarding_step_compare_description),
                    UniPurple,
                )
            }
        }
        Surface(color = TamerSoft, shape = RoundedCornerShape(18.dp)) {
            Text(
                text = stringResource(R.string.onboarding_demo_tip),
                modifier = Modifier.fillMaxWidth().padding(15.dp),
                color = Color(0xFF315A9B),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun StoryTitle(eyebrow: String, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = eyebrow,
            color = TamerBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
        )
        Text(
            text = title,
            color = Ink,
            fontSize = 31.sp,
            lineHeight = 37.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
        )
        Text(
            text = description,
            color = Muted,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color.White, shape = RoundedCornerShape(17.dp)) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 13.dp)) {
            Text(value, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ModelStoryCard(
    title: String,
    badge: String,
    description: String,
    stats: String,
    accent: Color,
    softColor: Color,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(softColor, RoundedCornerShape(20.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(badge, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(description, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            Text(stats, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StoryStep(number: String, title: String, description: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .background(accent, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(number, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
            Text(description, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StoryConnector() {
    Box(
        modifier = Modifier
            .padding(start = 17.dp)
            .height(18.dp)
            .background(Color(0xFFDCE3EF), RoundedCornerShape(2.dp))
            .padding(horizontal = 1.dp),
    )
}

@Composable
private fun PageIndicator(currentPage: Int, pageCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(7.dp)
                    .background(
                        color = if (index == currentPage) Ink else Color(0xFFD5DAE4),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = if (index == currentPage) 12.dp else 4.dp),
            )
        }
    }
}
