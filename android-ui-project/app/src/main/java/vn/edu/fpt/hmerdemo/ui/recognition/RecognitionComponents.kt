package vn.edu.fpt.hmerdemo.ui.recognition

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.edu.fpt.hmerdemo.R
import vn.edu.fpt.hmerdemo.ui.AppBackground
import vn.edu.fpt.hmerdemo.ui.Ink
import vn.edu.fpt.hmerdemo.ui.Muted
import vn.edu.fpt.hmerdemo.ui.TamerBlue
import vn.edu.fpt.hmerdemo.ui.UiError

@Composable
internal fun RecognitionHeader(onBackToOverview: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onBackToOverview) {
            Text(stringResource(R.string.navigation_overview))
        }
        Text(
            text = stringResource(R.string.recognition_title),
            color = Ink,
            fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.recognition_subtitle),
            color = Muted,
            fontSize = 14.sp,
        )
    }
}

@Composable
internal fun ImageInputCard(
    hasImage: Boolean,
    isCropped: Boolean,
    sourceImageUri: Uri?,
    croppedImageUri: Uri?,
    onTakePhoto: () -> Unit,
    onChooseImage: () -> Unit,
    onUseSample: () -> Unit,
    onCrop: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTakePhoto, modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.action_take_photo),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(onClick = onChooseImage, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_open_gallery), maxLines = 1)
                }
            }
            OutlinedButton(onClick = onUseSample, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_use_sample), maxLines = 1)
            }
            if (hasImage) {
                FormulaImageBox(
                    label = stringResource(R.string.image_original_label),
                    imageUri = sourceImageUri,
                )
                if (isCropped) {
                    FormulaImageBox(
                        label = stringResource(R.string.image_cropped_label),
                        imageUri = croppedImageUri,
                    )
                } else {
                    CropPlaceholder()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCrop,
                        enabled = sourceImageUri != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(
                                if (isCropped) {
                                    R.string.action_recrop
                                } else {
                                    R.string.action_crop_formula
                                },
                            ),
                        )
                    }
                    OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_delete_image))
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(116.dp)
                        .background(AppBackground, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.image_input_empty), color = Muted)
                }
            }
        }
    }
}

@Composable
private fun CropPlaceholder() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.image_cropped_label),
            color = Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(AppBackground, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.image_not_cropped), color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
internal fun FormulaImageBox(label: String, imageUri: Uri? = null) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = imageUri,
    ) {
        value = if (imageUri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(imageUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(Color(0xFFFCFCFE), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {

            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                val stroke = 4.dp.toPx()
                val path = Path().apply {
                    moveTo(size.width * .08f, size.height * .66f)
                    cubicTo(
                        size.width * .18f, size.height * .08f,
                        size.width * .20f, size.height * .92f,
                        size.width * .30f, size.height * .25f,
                    )
                    moveTo(size.width * .37f, size.height * .35f)
                    lineTo(size.width * .50f, size.height * .70f)
                    moveTo(size.width * .50f, size.height * .35f)
                    lineTo(size.width * .37f, size.height * .70f)
                    moveTo(size.width * .60f, size.height * .36f)
                    lineTo(size.width * .78f, size.height * .36f)
                    moveTo(size.width * .60f, size.height * .62f)
                    lineTo(size.width * .78f, size.height * .62f)
                }
                drawPath(path, color = Ink, style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(
                    color = TamerBlue,
                    start = Offset(size.width * .31f, size.height * .78f),
                    end = Offset(size.width * .55f, size.height * .78f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                }
            }
        }
    }
}

@Composable
internal fun ModelControls(
    mode: RecognitionModelMode,
    enabled: Boolean,
    onRunTamer: () -> Unit,
    onRunUni: () -> Unit,
    onRunBoth: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                stringResource(R.string.recognition_controls_title),
                fontWeight = FontWeight.SemiBold,
                color = Ink,
            )
            if (mode.showsAllModels) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRunTamer,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.model_tamer_title), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onRunUni,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.model_unimumer_action), maxLines = 1)
                    }
                }
                Button(
                    onClick = onRunBoth,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                ) {
                    Text(stringResource(R.string.action_compare_models))
                }
            } else {
                Button(
                    onClick = onRunUni,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.model_unimumer_action), maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun LoadingCard() {
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.recognition_loading), color = Muted)
        }
    }
}

private fun buildKatexHtml(latex: String): String {
    val quotedLatex = JSONObject.quote(latex)

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
            <link rel="stylesheet" href="katex/katex.min.css">
                        <style>
                html {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    background: transparent;
                }

                body {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    min-height: 100vh;
                    display: flex;
                    align-items: center;
                    justify-content: flex-start;
                    overflow-x: auto;
                    overflow-y: visible;
                    background: transparent;
                    box-sizing: border-box;
                }

                #formula {
                    display: inline-block;
                    min-width: max-content;
                    padding: 28px 18px;
                    color: #20283A;
                    font-size: 24px;
                    line-height: 1.6;
                    box-sizing: border-box;
                }

                .katex {
                    line-height: 1.5 !important;
                }

                .katex-display {
                    margin: 0 !important;
                    padding: 0 !important;
                    overflow: visible !important;
                    text-align: left;
                }

                .katex-html {
                    overflow: visible !important;
                }
            </style>
        </head>
        <body>
            <div id="formula"></div>
            <script src="katex/katex.min.js"></script>
            <script>
                const tex = $quotedLatex;
                katex.render(
                    tex,
                    document.getElementById("formula"),
                    {
                        throwOnError: false,
                        displayMode: true,
                        strict: false,
                        trust: false,
                        output: "htmlAndMathml"
                    }
                );
            </script>
        </body>
        </html>
    """.trimIndent()
}

@Composable
private fun LatexView(
    latex: String,
    modifier: Modifier = Modifier,
) {
    val html = remember(latex) { buildKatexHtml(latex) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                settings.textZoom = 100
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = false

                setInitialScale(100)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = true
                overScrollMode = android.view.View.OVER_SCROLL_NEVER
                tag = latex
                loadDataWithBaseURL(
                    "file:///android_asset/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
        update = { webView ->
            if (webView.tag != latex) {
                webView.tag = latex
                webView.loadDataWithBaseURL(
                    "file:///android_asset/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
        },
    )
}
@Composable
internal fun ResultCard(
    title: String,
    subtitle: String,
    accent: Color,
    softColor: Color,
    result: ModelResult?,
    error: UiError? = null,
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(softColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(result?.formattedLatency ?: "—", color = Muted, fontSize = 12.sp)
            }
            Text(subtitle, color = Muted, fontSize = 12.sp)
            HorizontalDivider(color = Color(0xFFE8ECF2))
            if (error != null) {
                InlineErrorCard(error = error)
            } else if (result == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(76.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.result_empty), color = Color(0xFF98A1B2))
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = softColor,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    LatexView(
                        latex = result.latex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 142.dp, max = 190.dp),
                    )
                }
                Text(
                    stringResource(R.string.result_latex_label),
                    color = Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = result.latex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppBackground, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    color = Ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                )
                OutlinedButton(
                    onClick = { copyLatex(context, result.latex) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_copy_latex))
                }
            }
        }
    }
}

@Composable
internal fun InlineErrorCard(
    error: UiError,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        color = Color(0xFFFFF3F1),
        shape = RoundedCornerShape(15.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(error.title, color = Color(0xFF9E3B32), fontWeight = FontWeight.SemiBold)
            Text(error.message, color = Ink, fontSize = 13.sp, lineHeight = 19.sp)
            Text(error.suggestion, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            if ((error.canRetry && onRetry != null) || onDismiss != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (error.canRetry && onRetry != null) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9E3B32)),
                        ) { Text(stringResource(R.string.action_retry)) }
                    }
                    if (onDismiss != null) {
                        OutlinedButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_close))
                        }
                    }
                }
            }
        }
    }
}

private fun copyLatex(context: Context, latex: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("LaTeX", latex))
}
