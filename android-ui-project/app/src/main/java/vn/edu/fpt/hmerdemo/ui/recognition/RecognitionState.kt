package vn.edu.fpt.hmerdemo.ui.recognition

import vn.edu.fpt.hmerdemo.network.HmerModel
import vn.edu.fpt.hmerdemo.ui.UiError


data class ModelResult(
    val latex: String,
    val rendered: String,
    val formattedLatency: String,
)


data class RecognitionState(
    val sourceImageUri: String? = null,
    val croppedImageUri: String? = null,
    val pendingCameraUri: String? = null,
    val inputError: UiError? = null,
    val isRunning: Boolean = false,
    val tamerResult: ModelResult? = null,
    val uniResult: ModelResult? = null,
    val tamerError: UiError? = null,
    val uniError: UiError? = null,
) {
    val hasImage: Boolean
        get() = sourceImageUri != null

    val isCropped: Boolean
        get() = croppedImageUri != null

    fun selectSource(uri: String): RecognitionState = copy(
        sourceImageUri = uri,
        croppedImageUri = null,
        pendingCameraUri = null,
        inputError = null,
        tamerResult = null,
        uniResult = null,
        tamerError = null,
        uniError = null,
    )

    fun cropSucceeded(uri: String): RecognitionState = copy(
        croppedImageUri = uri,
        inputError = null,
        tamerResult = null,
        uniResult = null,
        tamerError = null,
        uniError = null,
    )

    fun start(models: Set<HmerModel>): RecognitionState = copy(
        isRunning = true,
        tamerResult = if (HmerModel.Tamer in models) null else tamerResult,
        uniResult = if (HmerModel.UniMumer in models) null else uniResult,
        tamerError = if (HmerModel.Tamer in models) null else tamerError,
        uniError = if (HmerModel.UniMumer in models) null else uniError,
    )

    fun complete(model: HmerModel, result: ModelResult): RecognitionState =
        when (model) {
            HmerModel.Tamer -> copy(tamerResult = result, tamerError = null)
            HmerModel.UniMumer -> copy(uniResult = result, uniError = null)
        }

    fun fail(model: HmerModel, error: UiError): RecognitionState =
        when (model) {
            HmerModel.Tamer -> copy(tamerResult = null, tamerError = error)
            HmerModel.UniMumer -> copy(uniResult = null, uniError = error)
        }

    fun finish(): RecognitionState = copy(isRunning = false)

    fun clear(): RecognitionState = RecognitionState()
}
