package dz.sntp.market.data.remote

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
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
            body
        }
    }

    fun post(
        path: String,
        bodyJson: String,
        bearer: String? = null,
        /**
         * `return=representation` asks Postgres for the inserted row back, which
         * under RLS means the row must also pass the table's SELECT policy.
         * `reports` deliberately has no SELECT policy — an insert-only table is
         * what stops anyone listing other traders' votes with the anon key — so
         * asking for the representation requests something the caller is not
         * allowed to read. `return=minimal` is what an insert-only table wants.
         */
        prefer: String = "return=representation",
    ): Result<String> = runCatching {
        val req = Request.Builder()
            .url(restUrl(path))
            .post(bodyJson.toRequestBody(json))
            .header("apikey", anonKey)
            .header("Authorization", "Bearer ${bearer ?: anonKey}")
            .header("Content-Type", "application/json")
            .header("Prefer", prefer)
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, body)
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
            if (!resp.isSuccessful) throw ApiException(resp.code, "signup failed")
            body
        }
    }
}
