package vn.edu.fpt.hmerdemo.network


enum class HmerModel(
    val apiValue: String,
    val timeoutMs: Int,
) {
    Tamer("tamer_a3", 30_000),
    UniMumer("unimumer_lora", 60_000),
}


interface HmerApi {
    suspend fun health(): Boolean

    suspend fun predict(
        imageBytes: ByteArray,
        model: HmerModel,
    ): PredictionResult
}
