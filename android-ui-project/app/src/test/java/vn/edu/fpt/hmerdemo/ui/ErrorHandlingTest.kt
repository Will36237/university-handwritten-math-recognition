package vn.edu.fpt.hmerdemo.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test


class ErrorHandlingTest {
    @Test
    fun nonMathImageExplainsHowToCaptureAFormula() {
        val error = HmerErrorCode.NON_MATH_IMAGE.toUiError()

        assertEquals("Không tìm thấy công thức toán", error.title)
        assertEquals(
            "Ảnh không chứa công thức toán đủ rõ để nhận dạng.",
            error.message,
        )
        assertEquals(
            "Hãy chụp hoặc cắt sát một công thức toán rồi thử lại.",
            error.suggestion,
        )
        assertFalse(error.canRetry)
    }
}
