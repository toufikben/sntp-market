package dz.sntp.market.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "sntp_prefs")

/**
 * Everything that must survive a relaunch lives here. Deliberately small and
 * synchronous-friendly: the launch path reads it once before any network call.
 */
class Prefs(private val context: Context) {

    private object K {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val RECOVERY_CODE = stringPreferencesKey("recovery_code")
        val REPUTATION = intPreferencesKey("reputation_x100")
        val LAST_VOTE_DAY = stringPreferencesKey("last_vote_day")
        val CACHED_STATE = stringPreferencesKey("cached_state_json")
        val CACHED_STATE_AT = longPreferencesKey("cached_state_at")
        val CACHED_WEEK = stringPreferencesKey("cached_week_json")
        val CACHED_SHOPS = stringPreferencesKey("cached_shops_json")
        val NOTIFY_ENABLED = booleanPreferencesKey("notify_enabled")
        val NOTIFY_HOUR = intPreferencesKey("notify_hour")
        val NOTIFY_MINUTE = intPreferencesKey("notify_minute")
        val LAST_NOTIFIED_DAY = stringPreferencesKey("last_notified_day")
        val LAST_NOTIFIED_STATUS = stringPreferencesKey("last_notified_status")
        val SEEDED = booleanPreferencesKey("seeded_v1")
        val ANON_TOKEN = stringPreferencesKey("anon_token")
        val ANON_REFRESH = stringPreferencesKey("anon_refresh")
    }

    val deviceId: String
        get() = runCatching {
            var id = ""
            // Cheap synchronous read guarded by the DataStore file itself.
            val f = java.io.File(context.filesDir, "datastore/sntp_prefs.preferences_pb")
            if (f.exists()) id = ""
            id
        }.getOrDefault("").ifBlank { ensureDeviceId() }

    /** Blocking-safe, called from Application.onCreate only. */
    private fun ensureDeviceId(): String {
        val f = java.io.File(context.filesDir, "device_id.txt")
        if (f.exists()) {
            val t = f.readText().trim()
            if (t.length >= 8) return t
        }
        val fresh = UUID.randomUUID().toString().replace("-", "")
        runCatching { f.writeText(fresh) }
        return fresh
    }

    fun recoveryCode(): String =
        runCatching { java.io.File(context.filesDir, "recovery_code.txt").readText().trim() }
            .getOrDefault("")
            .ifBlank { "—" }

    fun saveRecoveryCode(code: String) {
        runCatching { java.io.File(context.filesDir, "recovery_code.txt").writeText(code) }
    }

    fun anonToken(): String? = runCatching {
        java.io.File(context.filesDir, "anon.txt").readText().trim()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    fun saveAnonToken(access: String?, refresh: String?) {
        runCatching {
            java.io.File(context.filesDir, "anon.txt").writeText(
                "access=$access\nrefresh=$refresh"
            )
        }
    }

    suspend fun lastVoteDay(): String? = context.dataStore.data.map { it[K.LAST_VOTE_DAY] }.first()

    suspend fun setLastVoteDay(day: String) {
        context.dataStore.edit { it[K.LAST_VOTE_DAY] = day }
    }

    suspend fun reputation(): Int = context.dataStore.data.map { it[K.REPUTATION] ?: 100 }.first()

    suspend fun setReputation(v: Int) {
        context.dataStore.edit { it[K.REPUTATION] = v }
    }

    suspend fun cachedState(): Pair<String?, Long> {
        val d = context.dataStore.data.first()
        return d[K.CACHED_STATE] to (d[K.CACHED_STATE_AT] ?: 0L)
    }

    suspend fun saveCachedState(json: String, at: Long) {
        context.dataStore.edit {
            it[K.CACHED_STATE] = json
            it[K.CACHED_STATE_AT] = at
        }
    }

    suspend fun cachedWeek(): String? = context.dataStore.data.map { it[K.CACHED_WEEK] }.first()

    suspend fun saveCachedWeek(json: String) {
        context.dataStore.edit { it[K.CACHED_WEEK] = json }
    }

    suspend fun cachedShops(): String? = context.dataStore.data.map { it[K.CACHED_SHOPS] }.first()

    suspend fun saveCachedShops(json: String) {
        context.dataStore.edit { it[K.CACHED_SHOPS] = json }
    }

    val notifyEnabled: Flow<Boolean> = context.dataStore.data.map { it[K.NOTIFY_ENABLED] ?: true }

    val notifyHour: Flow<Int> = context.dataStore.data.map { it[K.NOTIFY_HOUR] ?: 5 }

    val notifyMinute: Flow<Int> = context.dataStore.data.map { it[K.NOTIFY_MINUTE] ?: 0 }

    suspend fun setNotifyEnabled(on: Boolean) {
        context.dataStore.edit { it[K.NOTIFY_ENABLED] = on }
    }

    suspend fun setNotifyTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[K.NOTIFY_HOUR] = hour
            it[K.NOTIFY_MINUTE] = minute
        }
    }

    suspend fun lastNotified(): Pair<String?, String?> {
        val d = context.dataStore.data.first()
        return d[K.LAST_NOTIFIED_DAY] to d[K.LAST_NOTIFIED_STATUS]
    }

    suspend fun setLastNotified(day: String, status: String) {
        context.dataStore.edit {
            it[K.LAST_NOTIFIED_DAY] = day
            it[K.LAST_NOTIFIED_STATUS] = status
        }
    }
}
