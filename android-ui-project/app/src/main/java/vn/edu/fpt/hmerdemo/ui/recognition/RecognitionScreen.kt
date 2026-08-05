package vn.edu.fpt.hmerdemo.ui.recognition

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import vn.edu.fpt.hmerdemo.BuildConfig
import vn.edu.fpt.hmerdemo.R
import vn.edu.fpt.hmerdemo.image.ImageFiles
import vn.edu.fpt.hmerdemo.image.ImageValidator
import vn.edu.fpt.hmerdemo.network.HmerApi
import vn.edu.fpt.hmerdemo.network.HmerApiClient
import vn.edu.fpt.hmerdemo.network.HmerApiException
import vn.edu.fpt.hmerdemo.network.HmerModel
import vn.edu.fpt.hmerdemo.ui.AppBackground
import vn.edu.fpt.hmerdemo.ui.HmerErrorCode
import vn.edu.fpt.hmerdemo.ui.Ink
import vn.edu.fpt.hmerdemo.ui.Muted
import vn.edu.fpt.hmerdemo.ui.RecognitionStartMode
import vn.edu.fpt.hmerdemo.ui.TamerBlue
import vn.edu.fpt.hmerdemo.ui.TamerSoft
import vn.edu.fpt.hmerdemo.ui.UiError
import vn.edu.fpt.hmerdemo.ui.UniPurple
import vn.edu.fpt.hmerdemo.ui.UniSoft
import vn.edu.fpt.hmerdemo.ui.toUiError


@Composable
fun RecognitionScreen(
    startMode: RecognitionStartMode,
    onBackToOverview: () -> Unit,
    api: HmerApi = HmerApiClient,
    modelMode: RecognitionModelMode = RecognitionModelMode.fromConfig(
        BuildConfig.HMER_MODEL_UI_MODE,
    ),
) {
    val context = LocalContext.current
    val initialSampleUri = remember(startMode) {
        if (startMode == RecognitionStartMode.SAMPLE_IMAGE) {
            ImageFiles.randomSampleUri(context)
        } else {
            null
        }
    }
    var state by remember(initialSampleUri) {
        mutableStateOf(
            RecognitionState(sourceImageUri = initialSampleUri?.toString()),
        )
    }
    val runner = remember(api) { RecognitionRunner(api) }
    val scope = rememberCoroutineScope()
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    val cropFailedMessage = stringResource(R.string.crop_failed_message)
    val cropFailedSuggestion = stringResource(R.string.crop_failed_suggestion)
    val cropOpenFailedMessage = stringResource(R.string.crop_open_failed_message)
    val cropOpenFailedSuggestion = stringResource(R.string.crop_open_failed_suggestion)
    val cropToolbarTitle = stringResource(R.string.crop_toolbar_title)

    LaunchedEffect(Unit) {
        ImageFiles.clearTransientImages(context)
    }

    fun deleteOwnedImages(vararg uriValues: String?) {
        uriValues
            .filterNotNull()
            .distinct()
            .map(Uri::parse)
            .forEach { uri -> ImageFiles.deleteOwnedImage(context, uri) }
    }

    suspend fun validateImage(uri: Uri): UiError? =
        ImageValidator.validate(context, uri)

    val importImage: (Uri) -> Unit = { uri ->
        scope.launch {
            val validationError = validateImage(uri)

            if (validationError != null) {
                ImageFiles.deleteOwnedImage(context, uri)
                state = state.copy(
                    pendingCameraUri = state.pendingCameraUri
                        ?.takeUnless { it == uri.toString() },
                    inputError = validationError,
                )
                return@launch
            }
            val localUri = runCatching {
                ImageFiles.copyImageToAppCache(context, uri)
            }.getOrElse { error ->
                Log.e(
                    "HMER_IMAGE",
                    "Không thể xử lý ảnh (${error::class.java.simpleName}).",
                )

                state = state.copy(
                    pendingCameraUri = state.pendingCameraUri
                        ?.takeUnless { it == uri.toString() },
                    inputError = HmerErrorCode.IMAGE_DECODE_FAILED.toUiError(),
                )
                ImageFiles.deleteOwnedImage(context, uri)

                return@launch
            }
            val previousState = state
            state = previousState.selectSource(localUri.toString())
            deleteOwnedImages(
                previousState.sourceImageUri,
                previousState.croppedImageUri,
                previousState.pendingCameraUri,
            )
            if (uri != localUri) {
                ImageFiles.deleteOwnedImage(context, uri)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }

            importImage(uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val pendingUri = state.pendingCameraUri?.let(Uri::parse)
        if (success && pendingUri != null) {
            importImage(pendingUri)
        } else {
            pendingUri?.let { ImageFiles.deleteOwnedImage(context, it) }
            state = state.copy(pendingCameraUri = null)
        }
    }

    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val requestedOutput = pendingCropUri
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val outputUri = result.data?.let(UCrop::getOutput) ?: requestedOutput
                if (outputUri != null) {
                    state.croppedImageUri
                        ?.let(Uri::parse)
                        ?.takeIf { it != outputUri }
                        ?.let { ImageFiles.deleteOwnedImage(context, it) }
                    state = state.cropSucceeded(outputUri.toString())
                    requestedOutput
                        ?.takeIf { it != outputUri }
                        ?.let { ImageFiles.deleteOwnedImage(context, it) }
                } else {
                    state = state.copy(
                        inputError = HmerErrorCode.CROP_INVALID.toUiError(),
                    )
                }
            }
            UCrop.RESULT_ERROR -> {
                requestedOutput?.let { ImageFiles.deleteOwnedImage(context, it) }
                val cause = result.data?.let(UCrop::getError)
                val baseError = HmerErrorCode.CROP_INVALID.toUiError()
                state = state.copy(
                    inputError = baseError.copy(
                        message = cause?.localizedMessage
                            ?.takeIf { it.isNotBlank() }
                            ?: cropFailedMessage,
                        suggestion = cropFailedSuggestion,
                    ),
                )
            }
            else -> {
                requestedOutput?.let { ImageFiles.deleteOwnedImage(context, it) }
            }
        }
        pendingCropUri = null
    }

    fun clearRecognitionInput() {
        deleteOwnedImages(
            state.sourceImageUri,
            state.croppedImageUri,
            state.pendingCameraUri,
            pendingCropUri?.toString(),
        )
        pendingCropUri = null
        state = state.clear()
    }

    fun runApi(models: List<HmerModel>) {
        if (!state.isCropped || state.isRunning) return
        val imageUri = state.croppedImageUri?.let(Uri::parse) ?: return
        scope.launch {
            state = state.start(models.toSet())
            try {
                val imageBytes = ImageFiles.readImageBytes(context, imageUri)
                runner.run(imageBytes, models) { outcome ->
                    state = when (outcome) {
                        is RecognitionOutcome.Success -> state.complete(
                            outcome.model,
                            outcome.result,
                        )
                        is RecognitionOutcome.Failure -> state.fail(
                            outcome.model,
                            outcome.error.toUiError(),
                        )
                    }
                }
            } catch (error: HmerApiException) {
                val uiError = error.toUiError()
                models.forEach { model -> state = state.fail(model, uiError) }
            } finally {
                state = state.finish()
            }
        }
    }

    Scaffold(containerColor = AppBackground) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RecognitionHeader {
                clearRecognitionInput()
                onBackToOverview()
            }
            ImageInputCard(
                hasImage = state.hasImage,
                isCropped = state.isCropped,
                sourceImageUri = state.sourceImageUri?.let(Uri::parse),
                croppedImageUri = state.croppedImageUri?.let(Uri::parse),
                onTakePhoto = {
                    try {
                        deleteOwnedImages(state.pendingCameraUri)
                        val uri = ImageFiles.createCameraUri(context)
                        state = state.copy(
                            pendingCameraUri = uri.toString(),
                            inputError = null,
                        )
                        cameraLauncher.launch(uri)
                    } catch (_: Exception) {
                        state = state.copy(
                            inputError = HmerErrorCode.CAMERA_UNAVAILABLE.toUiError(),
                        )
                    }
                },
                onChooseImage = {
                    state = state.copy(inputError = null)
                    galleryLauncher.launch(
                        arrayOf(
                            "image/jpeg",
                            "image/png",
                            "image/webp",
                        ),
                    )
                },
                onUseSample = {
                    val sampleUri = ImageFiles.randomSampleUri(context)
                    val previousState = state
                    state = previousState.selectSource(sampleUri.toString())
                    deleteOwnedImages(
                        previousState.sourceImageUri,
                        previousState.croppedImageUri,
                        previousState.pendingCameraUri,
                    )
                },
                onCrop = {
                    val source = state.sourceImageUri?.let(Uri::parse)
                    if (source == null) {
                        state = state.copy(
                            inputError = HmerErrorCode.NO_IMAGE.toUiError(),
                        )
                    } else {
                        pendingCropUri?.let {
                            ImageFiles.deleteOwnedImage(context, it)
                        }
                        val output = ImageFiles.createCropUri(context)
                        pendingCropUri = output
                        val options = UCrop.Options().apply {
                            setFreeStyleCropEnabled(true)
                            setHideBottomControls(false)
                            setCompressionQuality(95)
                            setToolbarTitle(cropToolbarTitle)
                            setToolbarColor(android.graphics.Color.rgb(32, 40, 58))
                            setActiveControlsWidgetColor(android.graphics.Color.rgb(53, 110, 216))
                        }
                        val intent = UCrop.of(source, output)
                            .withOptions(options)
                            .getIntent(context)
                            .apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                clipData = ClipData.newUri(
                                    context.contentResolver,
                                    "HMER crop source",
                                    source,
                                ).apply {
                                    addItem(ClipData.Item(output))
                                }
                            }
                        try {
                            cropLauncher.launch(intent)
                        } catch (error: Exception) {
                            ImageFiles.deleteOwnedImage(context, output)
                            pendingCropUri = null
                            state = state.copy(
                                inputError = HmerErrorCode.CROP_INVALID.toUiError().copy(
                                    message = error.localizedMessage
                                        ?: cropOpenFailedMessage,
                                    suggestion = cropOpenFailedSuggestion,
                                ),
                            )
                        }
                    }
                },
                onClear = ::clearRecognitionInput,
            )
            if (state.inputError != null) {
                InlineErrorCard(
                    error = state.inputError!!,
                    onDismiss = { state = state.copy(inputError = null) },
                )
            }
            ModelControls(
                mode = modelMode,
                enabled = state.isCropped && !state.isRunning,
                onRunTamer = { runApi(listOf(HmerModel.Tamer)) },
                onRunUni = { runApi(listOf(HmerModel.UniMumer)) },
                onRunBoth = {
                    runApi(listOf(HmerModel.Tamer, HmerModel.UniMumer))
                },
            )
            if (state.isRunning) LoadingCard()
            Text(
                text = stringResource(R.string.recognition_results_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Ink,
            )
            if (modelMode.showsAllModels) {
                ResultCard(
                    title = stringResource(R.string.model_tamer_title),
                    subtitle = stringResource(R.string.model_tamer_subtitle),
                    accent = TamerBlue,
                    softColor = TamerSoft,
                    result = state.tamerResult,
                    error = state.tamerError,
                )
            }
            ResultCard(
                title = stringResource(R.string.model_unimumer_title),
                subtitle = stringResource(R.string.model_unimumer_subtitle),
                accent = UniPurple,
                softColor = UniSoft,
                result = state.uniResult,
                error = state.uniError,
            )
            Text(
                text = stringResource(R.string.recognition_privacy_notice),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                color = Muted,
                fontSize = 12.sp,
            )
        }
    }
}

private fun HmerApiException.toUiError(): UiError {
    val mappedCode = runCatching { HmerErrorCode.valueOf(code) }.getOrDefault(HmerErrorCode.UNKNOWN)
    return mappedCode.toUiError().copy(message = message)
}
