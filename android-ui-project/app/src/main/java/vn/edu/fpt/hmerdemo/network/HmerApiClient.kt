package vn.edu.fpt.hmerdemo.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.edu.fpt.hmerdemo.BuildConfig
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID

data class PredictionResult(
    val model: String,
    val latex: String,
    val latencyMs: Double,
    val validLatex: Boolean,
    val requestId: String,
)

class HmerApiException(
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object HmerApiClient : HmerApi {
    private val baseUrl = BuildConfig.HMER_API_BASE_URL.trimEnd('/')

    override suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        val connection = open("$baseUrl/health", "GET", 5_000)
        try {
            connection.connect()
            connection.responseCode in 200..299
        } catch (error: SocketTimeoutException) {
            throw HmerApiException("NETWORK_TIMEOUT", "Máy chủ phản hồi quá thời gian.", error)
        } catch (error: Exception) {
            throw HmerApiException("API_UNREACHABLE", "Không thể kết nối tới máy chủ nhận dạng.", error)
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun predict(
        imageBytes: ByteArray,
        model: HmerModel,
    ): PredictionResult =
        withContext(Dispatchers.IO) {
            val boundary = "HMER-${UUID.randomUUID()}"
            val connection = open("$baseUrl/predict", "POST", model.timeoutMs)
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("X-Request-ID", UUID.randomUUID().toString())

            try {
                BufferedOutputStream(connection.outputStream).use { output ->
                    fun text(value: String) = output.write(value.toByteArray(Charsets.UTF_8))
                    text("--$boundary\r\n")
                    text("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
                    text("${model.apiValue}\r\n")
                    text("--$boundary\r\n")
                    text("Content-Disposition: form-data; name=\"image\"; filename=\"formula.jpg\"\r\n")
                    text("Content-Type: image/jpeg\r\n\r\n")
                    output.write(imageBytes)
                    text("\r\n--$boundary--\r\n")
                }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                if (status !in 200..299) {
                    val error = json?.optJSONObject("error")
                    throw HmerApiException(
                        error?.optString("code")?.ifBlank { "REQUEST_REJECTED" } ?: "REQUEST_REJECTED",
                        error?.optString("message")?.ifBlank { "Máy chủ từ chối yêu cầu." }
                            ?: "Máy chủ từ chối yêu cầu.",
                    )
                }
                if (json == null) throw HmerApiException("EMPTY_MODEL_OUTPUT", "Phản hồi máy chủ không hợp lệ.")
                PredictionResult(
                    model = json.getString("model"),
                    latex = json.getString("latex"),
                    latencyMs = json.getDouble("latency_ms"),
                    validLatex = json.optBoolean("valid_latex", false),
                    requestId = json.getString("request_id"),
                )
            } catch (error: HmerApiException) {
                throw error
            } catch (error: SocketTimeoutException) {
                throw HmerApiException("NETWORK_TIMEOUT", "Yêu cầu nhận dạng quá thời gian.", error)
            } catch (error: Exception) {
                throw HmerApiException("API_UNREACHABLE", "Không thể kết nối tới máy chủ nhận dạng.", error)
            } finally {
                connection.disconnect()
            }
        }

    private fun open(url: String, method: String, timeoutMs: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = timeoutMs
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }
}
