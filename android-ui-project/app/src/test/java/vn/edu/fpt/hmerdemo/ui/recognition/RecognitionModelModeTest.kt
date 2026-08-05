package vn.edu.fpt.hmerdemo.ui.recognition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecognitionModelModeTest {
    @Test
    fun parsesUniOnlyMode() {
        assertEquals(
            RecognitionModelMode.UNI_ONLY,
            RecognitionModelMode.fromConfig("uni_only"),
        )
    }

    @Test
    fun parsesAllModelsMode() {
        assertEquals(
            RecognitionModelMode.ALL_MODELS,
            RecognitionModelMode.fromConfig("all_models"),
        )
    }

    @Test
    fun rejectsUnknownMode() {
        assertThrows(IllegalArgumentException::class.java) {
            RecognitionModelMode.fromConfig("unknown")
        }
    }
}
