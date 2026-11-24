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
import java.io.File
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "sntp_prefs")

/**
 * Everything that must survive a relaunch lives here. Deliberately small: the
 * launch path reads it once before any network call.
 *
 * Device identity and the recovery code are plain files rather than DataStore
 * entries because they are read synchronously from Application.onCreate, where a
 * suspend call is not available.
 */
class Prefs(private val context: Context) {

    private object K {
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
    }

    // ------------------------------------------------------------- identity

    /** Stable per-install identity. Sent with every report, never leaves the device otherwise. */
    val deviceId: String by lazy { readOrCreate("device_id.txt", 16) }

    /**
     * Printed once in settings so a trader who changes phone can carry their
     * reputation across. Blocks of four, unambiguous alphabet.
     */
    fun recoveryCode(): String = readOrCreate("recovery_code.txt", 12, alphabet = CODE_ALPHABET)

    private fun readOrCreate(
        name: String,
        length: Int,
        alphabet: String = HEX,
    ): String {
        val file = File(context.filesDir, name)
        val existing = runCatching { file.readText().trim() }.getOrNull()
        if (!existing.isNullOrBlank() && existing.length >= 8) return existing

        val fresh = buildString {
            val rnd = java.security.SecureRandom()
            repeat(length) { append(alphabet[rnd.nextInt(alphabet.length)]) }
        }
        runCatching { file.writeText(fresh) }
        return fresh
    }

    // -------------------------------------------------------------- voting

    suspend fun lastVoteDay(): String? =
        context.dataStore.data.map { it[K.LAST_VOTE_DAY] }.first()

    suspend fun setLastVoteDay(day: String) {
        context.dataStore.edit { it[K.LAST_VOTE_DAY] = day }
    }

    suspend fun reputation(): Int =
        context.dataStore.data.map { it[K.REPUTATION] ?: 100 }.first()

    suspend fun setReputation(v: Int) {
        context.dataStore.edit { it[K.REPUTATION] = v }
    }

    // --------------------------------------------------------------- cache

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

    suspend fun cachedWeek(): String? =
        context.dataStore.data.map { it[K.CACHED_WEEK] }.first()

    suspend fun saveCachedWeek(json: String) {
        context.dataStore.edit { it[K.CACHED_WEEK] = json }
    }

    suspend fun cachedShops(): String? =
        context.dataStore.data.map { it[K.CACHED_SHOPS] }.first()

    suspend fun saveCachedShops(json: String) {
        context.dataStore.edit { it[K.CACHED_SHOPS] = json }
    }

    // -------------------------------------------------------- notifications

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

    /** Last (day, status) we already told the user about. Drives "only when it changed". */
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

    private companion object {
        const val HEX = "0123456789abcdef"
        /** No 0/O or 1/I: this string gets read aloud over the phone. */
        const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    }
}
