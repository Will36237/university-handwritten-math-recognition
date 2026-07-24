package vn.edu.fpt.hmerdemo.ui.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.fpt.hmerdemo.network.HmerModel
import vn.edu.fpt.hmerdemo.ui.HmerErrorCode
import vn.edu.fpt.hmerdemo.ui.toUiError


class RecognitionStateTest {
    private val result = ModelResult(
        latex = "x",
        rendered = "x",
        formattedLatency = "0.100 s",
    )
    private val error = HmerErrorCode.NO_IMAGE.toUiError()

    @Test
    fun selectingSourceClearsCropResultsAndErrors() {
        val state = RecognitionState(
            croppedImageUri = "content://old-crop",
            tamerResult = result,
            uniError = error,
        ).selectSource("content://source")

        assertEquals("content://source", state.sourceImageUri)
        assertNull(state.croppedImageUri)
        assertNull(state.tamerResult)
        assertNull(state.uniError)
        assertTrue(state.hasImage)
        assertFalse(state.isCropped)
    }

    @Test
    fun cropSuccessEnablesRecognitionAndClearRestoresInitialState() {
        val cropped = RecognitionState(sourceImageUri = "content://source")
            .cropSucceeded("content://crop")

        assertTrue(cropped.isCropped)
        assertEquals("content://crop", cropped.croppedImageUri)
        assertEquals(RecognitionState(), cropped.clear())
    }

    @Test
    fun cameraAndGallerySourcesShareTheSameCropGate() {
        val cameraState = RecognitionState(
            pendingCameraUri = "content://camera-pending",
        ).selectSource("content://camera-source")
        val galleryState = RecognitionState()
            .selectSource("content://gallery-source")

        assertNull(cameraState.pendingCameraUri)
        assertTrue(cameraState.hasImage)
        assertFalse(cameraState.isCropped)
        assertTrue(galleryState.hasImage)
        assertFalse(galleryState.isCropped)

        val croppedCamera = cameraState.cropSucceeded("content://camera-crop")
        val croppedGallery = galleryState.cropSucceeded("content://gallery-crop")

        assertTrue(croppedCamera.isCropped)
        assertTrue(croppedGallery.isCropped)
    }

    @Test
    fun startingSelectedModelClearsOnlyItsPreviousOutcome() {
        val state = RecognitionState(
            croppedImageUri = "content://crop",
            tamerResult = result,
            uniResult = result,
            tamerError = error,
            uniError = error,
        ).start(setOf(HmerModel.Tamer))

        assertTrue(state.isRunning)
        assertNull(state.tamerResult)
        assertNull(state.tamerError)
        assertEquals(result, state.uniResult)
        assertEquals(error, state.uniError)
    }

    @Test
    fun outcomesUpdateOnlyTheirModelAndFinishStopsLoading() {
        val tamer = RecognitionState(isRunning = true)
            .complete(HmerModel.Tamer, result)
        val failedUni = tamer.fail(HmerModel.UniMumer, error).finish()

        assertEquals(result, failedUni.tamerResult)
        assertEquals(error, failedUni.uniError)
        assertFalse(failedUni.isRunning)
    }
}
