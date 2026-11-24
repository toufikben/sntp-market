package dz.sntp.market.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Thin PostgREST client. No SDK: one small surface we fully control, which keeps
 * the release APK tiny and the failure modes obvious.
 *
 * All calls return [Result]; nothing throws into the UI.
 */
class SupabaseClient(
    private val baseUrl: String,
    private val anonKey: String,
) {
    private val json = "application/json".toMediaType()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val isConfigured: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    private fun restUrl(path: String) = "${baseUrl.trimEnd('/')}/rest/v1/$path"

    fun get(path: String, bearer: String? = null): Result<String> = runCatching {
        val req = Request.Builder()
            .url(restUrl(path))
            .get()
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${bearer ?: anonKey}")
            .header("Accept", "application/json")
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${body.take(200)}")
            body
        }
    }

    fun post(path: String, bodyJson: String, bearer: String? = null): Result<String> = runCatching {
        val req = Request.Builder()
            .url(restUrl(path))
            .post(bodyJson.toRequestBody(json))
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${bearer ?: anonKey}")
            .header("Content-Type", "application/json")
            .header("Prefer", "return=representation")
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: ${body.take(200)}")
            body
        }
    }

    /** Exchanges the anon key for a short-lived anonymous JWT so RLS can see auth.uid(). */
    fun signInAnonymously(): Result<String> = runCatching {
        val url = "${baseUrl.trimEnd('/')}/auth/v1/signup"
        val req = Request.Builder()
            .url(url)
            .post("""{"data":{},"gotrue_meta_security":{}}""".toRequestBody(json))
            .header("apikey", anonKey)
            .header("Content-Type", "application/json")
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("signup HTTP ${resp.code}")
            body
        }
    }
}
