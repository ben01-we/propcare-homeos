package app.propcare.homeos.net

import android.util.Base64
import app.propcare.homeos.BuildConfig
import app.propcare.homeos.domain.Triage
import app.propcare.homeos.domain.TriageResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Calls the PropCare AI triage endpoint (a Vercel function that holds the Gemini key).
 * HTTPS only (enforced by network_security_config). No key or secret lives in the app.
 */
object TriageApi {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    val configured: Boolean get() = BuildConfig.API_BASE.startsWith("https://")

    class TriageException(message: String) : Exception(message)

    suspend fun triage(jpeg: ByteArray, description: String): Triage = withContext(Dispatchers.IO) {
        if (!configured) throw TriageException("Online photo check is not set up.")
        val body = buildJsonObject {
            put("imageBase64", Base64.encodeToString(jpeg, Base64.NO_WRAP))
            put("mimeType", "image/jpeg")
            put("description", description.take(500))
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(BuildConfig.API_BASE.trimEnd('/') + "/api/triage")
            .header("X-Client-Id", BuildConfig.CLIENT_ID)
            .post(body)
            .build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            val parsed = runCatching { json.decodeFromString(TriageResponse.serializer(), text) }.getOrNull()
            if (!res.isSuccessful || parsed?.triage == null) {
                throw TriageException(parsed?.error ?: "The photo check did not respond (${res.code}).")
            }
            parsed.triage
        }
    }
}
