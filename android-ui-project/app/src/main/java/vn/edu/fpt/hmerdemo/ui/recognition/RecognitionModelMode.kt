package vn.edu.fpt.hmerdemo.ui.recognition

enum class RecognitionModelMode {
    UNI_ONLY,
    ALL_MODELS,
    ;

    val showsAllModels: Boolean
        get() = this == ALL_MODELS

    companion object {
        fun fromConfig(value: String): RecognitionModelMode = when (value) {
            "uni_only" -> UNI_ONLY
            "all_models" -> ALL_MODELS
            else -> throw IllegalArgumentException(
                "Unsupported HMER model UI mode: $value",
            )
        }
    }
}
