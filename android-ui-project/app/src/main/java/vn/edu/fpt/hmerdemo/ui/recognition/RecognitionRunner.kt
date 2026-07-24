package vn.edu.fpt.hmerdemo.ui.recognition

import vn.edu.fpt.hmerdemo.network.HmerApi
import vn.edu.fpt.hmerdemo.network.HmerApiException
import vn.edu.fpt.hmerdemo.network.HmerModel


sealed interface RecognitionOutcome {
    val model: HmerModel

    data class Success(
        override val model: HmerModel,
        val result: ModelResult,
    ) : RecognitionOutcome

    data class Failure(
        override val model: HmerModel,
        val error: HmerApiException,
    ) : RecognitionOutcome
}


class RecognitionRunner(
    private val api: HmerApi,
) {
    suspend fun run(
        imageBytes: ByteArray,
        models: List<HmerModel>,
        onOutcome: (RecognitionOutcome) -> Unit,
    ) {
        try {
            api.health()
        } catch (error: HmerApiException) {
            models.forEach { model ->
                onOutcome(RecognitionOutcome.Failure(model, error))
            }
            return
        }

        models.forEach { model ->
            val outcome = try {
                val response = api.predict(imageBytes, model)
                RecognitionOutcome.Success(
                    model=model,
                    result=ModelResult(
                        latex=response.latex,
                        rendered=response.latex,
                        formattedLatency=String.format("%.3f s", response.latencyMs / 1000.0),
                    ),
                )
            } catch (error: HmerApiException) {
                RecognitionOutcome.Failure(model, error)
            }
            onOutcome(outcome)
        }
    }
}
