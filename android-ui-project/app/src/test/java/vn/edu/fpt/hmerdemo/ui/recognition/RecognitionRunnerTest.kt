package vn.edu.fpt.hmerdemo.ui.recognition

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.edu.fpt.hmerdemo.network.HmerApi
import vn.edu.fpt.hmerdemo.network.HmerApiException
import vn.edu.fpt.hmerdemo.network.HmerModel
import vn.edu.fpt.hmerdemo.network.PredictionResult
import java.util.Locale


class RecognitionRunnerTest {
    @Test
    fun runsHealthThenModelsSequentially() = runBlocking {
        val api = FakeApi()
        val outcomes = mutableListOf<RecognitionOutcome>()

        RecognitionRunner(api).run(
            imageBytes = byteArrayOf(1),
            models = listOf(HmerModel.Tamer, HmerModel.UniMumer),
            onOutcome = outcomes::add,
        )

        assertEquals(listOf("health", "tamer_a3", "unimumer_lora"), api.calls)
        assertEquals(2, outcomes.size)
        assertTrue(outcomes.all { it is RecognitionOutcome.Success })
    }

    @Test
    fun modelFailureDoesNotPreventSecondModel() = runBlocking {
        val api = FakeApi(failingModel = HmerModel.Tamer)
        val outcomes = mutableListOf<RecognitionOutcome>()

        RecognitionRunner(api).run(
            byteArrayOf(1),
            listOf(HmerModel.Tamer, HmerModel.UniMumer),
            outcomes::add,
        )

        assertTrue(outcomes[0] is RecognitionOutcome.Failure)
        assertTrue(outcomes[1] is RecognitionOutcome.Success)
        assertEquals(listOf("health", "tamer_a3", "unimumer_lora"), api.calls)
    }

    @Test
    fun healthFailureFailsEveryRequestedModelWithoutPredicting() = runBlocking {
        val api = FakeApi(healthError = HmerApiException("API_UNREACHABLE", "offline"))
        val outcomes = mutableListOf<RecognitionOutcome>()

        RecognitionRunner(api).run(
            byteArrayOf(1),
            listOf(HmerModel.Tamer, HmerModel.UniMumer),
            outcomes::add,
        )

        assertEquals(listOf("health"), api.calls)
        assertEquals(
            listOf(HmerModel.Tamer, HmerModel.UniMumer),
            outcomes.map { it.model },
        )
        assertTrue(outcomes.all { it is RecognitionOutcome.Failure })
    }

    @Test
    fun successFormatsExistingResultFieldsIndependentlyOfDefaultLocale() = runBlocking {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val outcomes = mutableListOf<RecognitionOutcome>()

            RecognitionRunner(FakeApi()).run(
                byteArrayOf(1),
                listOf(HmerModel.Tamer),
                outcomes::add,
            )

            val result = (outcomes.single() as RecognitionOutcome.Success).result
            assertEquals("tamer_a3-latex", result.latex)
            assertEquals("tamer_a3-latex", result.rendered)
            assertEquals("1.235 s", result.formattedLatency)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}


private class FakeApi(
    private val failingModel: HmerModel? = null,
    private val healthError: HmerApiException? = null,
) : HmerApi {
    val calls = mutableListOf<String>()

    override suspend fun health(): Boolean {
        calls += "health"
        healthError?.let { throw it }
        return true
    }

    override suspend fun predict(
        imageBytes: ByteArray,
        model: HmerModel,
    ): PredictionResult {
        calls += model.apiValue
        if (model == failingModel) {
            throw HmerApiException("MODEL_UNAVAILABLE", "failed")
        }
        return PredictionResult(
            model = model.apiValue,
            latex = "${model.apiValue}-latex",
            latencyMs = 1234.56,
            validLatex = true,
            requestId = "request-id",
        )
    }
}
