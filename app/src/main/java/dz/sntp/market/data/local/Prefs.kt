package dz.sntp.market.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dz.sntp.market.data.model.Entitlement
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
 * Device identity is a plain file rather than a DataStore entry because it is read
 * synchronously from Application.onCreate, where a suspend call is not available.
 */
class Prefs(private val context: Context) {

    private object K {
        val REPUTATION = intPreferencesKey("reputation_x100")
        val LAST_VOTE_DAY = stringPreferencesKey("last_vote_day")
        val LAST_VOTE_AT = longPreferencesKey("last_vote_at")
        val CACHED_STATE = stringPreferencesKey("cached_state_json")
        val CACHED_STATE_AT = longPreferencesKey("cached_state_at")
        val CACHED_WEEK = stringPreferencesKey("cached_week_json")
        val CACHED_SHOPS = stringPreferencesKey("cached_shops_json")
        /** Last month grid read from the server; the calendar must not vanish offline. */
        val CACHED_HISTORY = stringPreferencesKey("cached_history_json")
        val NOTIFY_ENABLED = booleanPreferencesKey("notify_enabled")
        val NOTIFY_HOUR = intPreferencesKey("notify_hour")
        val NOTIFY_MINUTE = intPreferencesKey("notify_minute")
        val LAST_NOTIFIED_DAY = stringPreferencesKey("last_notified_day")
        val LAST_NOTIFIED_STATUS = stringPreferencesKey("last_notified_status")
        val LAST_SEEN_STATUS = stringPreferencesKey("last_seen_status")
        /** The status the user was last actually told, by either notification. */
        val LAST_TOLD_STATUS = stringPreferencesKey("last_told_status")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val TIER = stringPreferencesKey("entitlement_tier")
        val TIER_SOURCE = stringPreferencesKey("entitlement_source")
        val TIER_EXPIRES = longPreferencesKey("entitlement_expires")
        val PROMO_ATTEMPTED = booleanPreferencesKey("promo_attempted")
        val NOTIFY_ASKED = booleanPreferencesKey("notify_asked")
        /**
         * True while the app turned the morning switch off *itself* because the
         * permission was missing. Without it the state is indistinguishable from a
         * user who switched notifications off on purpose, so returning from system
         * settings with the permission granted would leave the switch off for good.
         */
        val NOTIFY_GATED = booleanPreferencesKey("notify_gated")
        /** The Play purchase token the stored entitlement came from. */
        val PLAY_TOKEN = stringPreferencesKey("play_token")
        /**
         * The SKU the live Play subscription was bought with. The upgrade sheet
         * offers two plans that both map to PRO, so the tier alone cannot say which
         * row is the one already paid for — and it marked both as active.
         */
        val PLAY_SKU = stringPreferencesKey("play_sku")
        /**
         * Shop ids whose phone number this device already unlocked by watching the
         * rewarded slot. Kept here rather than in memory: the unlock is what the
         * user paid attention for, and it vanished on every relaunch.
         */
        val UNLOCKED_SHOPS = androidx.datastore.preferences.core.stringSetPreferencesKey("unlocked_shops")
    }

    // ---------------------------------------------------------------- theme

    /** 0 = follow the phone, 1 = always light, 2 = always dark. */
    suspend fun themeMode(): Int = context.dataStore.data.map { it[K.THEME_MODE] ?: 0 }.first()

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[K.THEME_MODE] = mode }
    }

    // --------------------------------------------------------- entitlement

    suspend fun entitlement(): Entitlement {
        val d = context.dataStore.data.first()
        return Entitlement.fromServer(
            tier = d[K.TIER],
            source = d[K.TIER_SOURCE],
            expiresAtMs = d[K.TIER_EXPIRES] ?: 0L,
        )
    }

    suspend fun setEntitlement(e: Entitlement) {
        context.dataStore.edit {
            it[K.TIER] = e.tier.name.lowercase()
            it[K.TIER_SOURCE] = e.source
            it[K.TIER_EXPIRES] = e.expiresAtMs
        }
    }

    /**
     * Writes the tier and the Play token in one edit. Two separate edits left a
     * window where the process could die between them and leave a new billing
     * period recorded against the previous token, which is exactly the pair
     * [Entitlement.fromSku] uses to decide whether a period is continuing.
     */
    suspend fun setPlayEntitlement(e: Entitlement, token: String?, sku: String? = null) {
        context.dataStore.edit { d ->
            d[K.TIER] = e.tier.name.lowercase()
            d[K.TIER_SOURCE] = e.source
            d[K.TIER_EXPIRES] = e.expiresAtMs
            if (token == null) d.remove(K.PLAY_TOKEN) else d[K.PLAY_TOKEN] = token
            if (sku == null) d.remove(K.PLAY_SKU) else d[K.PLAY_SKU] = sku
        }
    }

    /** The SKU behind the stored Play subscription, when it came from Play. */
    suspend fun playSku(): String? = context.dataStore.data.first()[K.PLAY_SKU]

    suspend fun promoAttempted(): Boolean =
        context.dataStore.data.map { it[K.PROMO_ATTEMPTED] ?: false }.first()

    /**
     * The Play token behind the stored entitlement. Kept separately from the tier
     * because it is the only thing that tells an upgrade (a new token, a new
     * period) from Play reporting the same subscription again.
     */
    suspend fun playToken(): String? = context.dataStore.data.first()[K.PLAY_TOKEN]

    suspend fun setPromoAttempted() {
        context.dataStore.edit { it[K.PROMO_ATTEMPTED] = true }
    }

    // ------------------------------------------------------------- identity

    /** Stable per-install identity. Sent with every report, never leaves the device otherwise. */
    val deviceId: String by lazy { readOrCreate("device_id.txt", 16) }

    private fun readOrCreate(
        name: String,
        length: Int,
    ): String {
        val file = File(context.filesDir, name)
        val existing = runCatching { file.readText().trim() }.getOrNull()
        if (!existing.isNullOrBlank() && existing.length >= 8) return existing

        val fresh = buildString {
            val rnd = java.security.SecureRandom()
            repeat(length) { append(HEX[rnd.nextInt(HEX.length)]) }
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

    /**
     * When this device last reported a state. The market can open at 06:00 and
     * shut by 12:00 on the same day, so "one vote per calendar day" froze the
     * screen on the morning value and made the app useless after lunch. What the
     * server actually enforces is a 20-minute gap, so that is what we mirror.
     */
    suspend fun lastVoteAt(): Long =
        context.dataStore.data.map { it[K.LAST_VOTE_AT] ?: 0L }.first()

    suspend fun setLastVoteAt(ms: Long) {
        context.dataStore.edit { it[K.LAST_VOTE_AT] = ms }
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

    /** The last month grid the app managed to read, so the calendar survives a dead network. */
    suspend fun cachedHistory(): String? =
        context.dataStore.data.map { it[K.CACHED_HISTORY] }.first()

    suspend fun saveCachedHistory(json: String) {
        context.dataStore.edit { it[K.CACHED_HISTORY] = json }
    }

    // --------------------------------------------------------------- shops

    /** Shop ids unlocked on this device; survives a relaunch. */
    suspend fun unlockedShops(): Set<String> =
        context.dataStore.data.map { it[K.UNLOCKED_SHOPS] ?: emptySet() }.first()

    suspend fun unlockShop(id: String) {
        context.dataStore.edit { it[K.UNLOCKED_SHOPS] = (it[K.UNLOCKED_SHOPS] ?: emptySet()) + id }
    }

    // -------------------------------------------------------- notifications

    val notifyEnabled: Flow<Boolean> = context.dataStore.data.map { it[K.NOTIFY_ENABLED] ?: true }

    val notifyHour: Flow<Int> = context.dataStore.data.map { it[K.NOTIFY_HOUR] ?: 5 }

    val notifyMinute: Flow<Int> = context.dataStore.data.map { it[K.NOTIFY_MINUTE] ?: 0 }

    suspend fun setNotifyEnabled(on: Boolean) {
        context.dataStore.edit { it[K.NOTIFY_ENABLED] = on }
    }

    /**
     * Whether the system permission dialog has already been shown. Android answers
     * a second request silently, so repeating it would look like a broken switch
     * rather than a user who said no.
     */
    suspend fun notifyAsked(): Boolean =
        context.dataStore.data.map { it[K.NOTIFY_ASKED] ?: false }.first()

    suspend fun setNotifyAsked() {
        context.dataStore.edit { it[K.NOTIFY_ASKED] = true }
    }

    suspend fun notifyGated(): Boolean =
        context.dataStore.data.map { it[K.NOTIFY_GATED] ?: false }.first()

    suspend fun setNotifyGated(gated: Boolean) {
        context.dataStore.edit { it[K.NOTIFY_GATED] = gated }
    }

    suspend fun setNotifyTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[K.NOTIFY_HOUR] = hour
            it[K.NOTIFY_MINUTE] = minute
        }
    }

    /** Last (day, status) the morning notification already spent. */
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

    /** Just the day, for the scheduler's "is today's brief already out" check. */
    suspend fun lastNotifiedDay(): String? = context.dataStore.data.first()[K.LAST_NOTIFIED_DAY]

    /**
     * The last status the user was told by *either* notification. Kept apart from
     * [lastNotified] on purpose: that one answers "did the morning brief go out
     * today", this one answers "what does the user already know". They were the
     * same field, so a change alert at 02:00 marked the day as done and the 05:00
     * morning brief never fired.
     */
    suspend fun lastToldStatus(): String? = context.dataStore.data.first()[K.LAST_TOLD_STATUS]

    suspend fun setLastToldStatus(status: String) {
        context.dataStore.edit { it[K.LAST_TOLD_STATUS] = status }
    }

    /**
     * The status the screen last showed, independent of what we notified about.
     * A change notification compares against this so a change the user already
     * saw on screen (or was already pushed at dawn) does not push again.
     */
    suspend fun lastSeenStatus(): String? = context.dataStore.data.first()[K.LAST_SEEN_STATUS]

    suspend fun setLastSeenStatus(status: String) {
        context.dataStore.edit { it[K.LAST_SEEN_STATUS] = status }
    }

    private companion object {
        const val HEX = "0123456789abcdef"
    }
}
