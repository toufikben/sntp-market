package dz.sntp.market.data.repo

import android.content.Context
import dz.sntp.market.data.local.Prefs
import dz.sntp.market.data.model.DaySchedule
import dz.sntp.market.data.model.Freshness
import dz.sntp.market.data.model.MarketState
import dz.sntp.market.data.model.MarketStateDto
import dz.sntp.market.data.model.MarketStatus
import dz.sntp.market.data.model.Shop
import dz.sntp.market.data.model.ShopDto
import dz.sntp.market.data.model.Source
import dz.sntp.market.data.model.Sponsor
import dz.sntp.market.data.remote.SupabaseClient
import dz.sntp.market.data.seed.SntpSeed
import dz.sntp.market.util.IsoTime
import dz.sntp.market.util.MarketTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class RepoConfig(
    val supabaseUrl: String,
    val anonKey: String,
    val marketSlug: String,
)

/**
 * Single source of truth for "what is the market doing".
 *
 * Rule that never bends: a value read from disk is never returned as if it were
 * live. Freshness is carried on every result, and the UI must show it.
 */
class MarketRepository(
    private val context: Context,
    private val config: RepoConfig,
    private val prefs: Prefs,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val client = SupabaseClient(config.supabaseUrl, config.anonKey)

    /** How long a cached state may be shown without a stale badge. */
    private val freshWindowMs = 20 * 60 * 1000L

    val isConfigured: Boolean get() = client.isConfigured

    // ---------------------------------------------------------------- state

    suspend fun loadState(): MarketState = withContext(Dispatchers.IO) {
        val remote = fetchState()
        if (remote != null) {
            val now = System.currentTimeMillis()
            // Reaching the server is not the same as the server knowing something
            // recent. If cron stalled or nobody has voted since yesterday, the row
            // is old and must carry the stale warning; marking it LIVE because the
            // HTTP call succeeded is exactly the confident lie this app exists to
            // avoid. The age comes from the server's own updated_at.
            val ageMs = IsoTime.toEpochMs(remote.updatedAt).takeIf { it > 0 }?.let { now - it }
            val freshness = if (ageMs != null && ageMs > freshWindowMs) {
                Freshness.STALE
            } else {
                Freshness.LIVE
            }
            val domain = remote.toDomain(freshness, now)
            prefs.saveCachedState(
                json.encodeToString(MarketStateDto.serializer(), remote),
                now,
            )
            return@withContext domain
        }

        val (cachedJson, cachedAt) = prefs.cachedState()
        val age = System.currentTimeMillis() - cachedAt
        val freshness = when {
            cachedJson == null -> if (isOnline()) Freshness.STALE else Freshness.OFFLINE
            age <= freshWindowMs -> Freshness.CACHED
            else -> Freshness.STALE
        }

        if (cachedJson != null) {
            val dto = runCatching {
                json.decodeFromString(MarketStateDto.serializer(), cachedJson)
            }.getOrNull()
            if (dto != null) return@withContext dto.toDomain(freshness, cachedAt)
        }

        // Nothing at all: fall back to the seed schedule so the screen is never empty.
        seedState(freshness)
    }

    private suspend fun fetchState(): MarketStateDto? {
        if (!isConfigured) return null
        val body = client.get(
            "market_state?market_slug=eq.${config.marketSlug}&select=*&limit=1"
        ).getOrNull() ?: return null

        return runCatching {
            val arr = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(MarketStateDto.serializer()), body
            )
            arr.firstOrNull()
        }.getOrNull()
    }

    private fun seedState(freshness: Freshness): MarketState {
        val iso = MarketTime.isoDayNumber()
        val day = SntpSeed.week.firstOrNull { it.day == iso }
        // A guessed schedule is not a state. Reporting "closed" from the usual
        // timetable is precisely the confident lie the product exists to avoid,
        // so the seed lands on UNKNOWN and the card prints the usual window.
        return MarketState(
            status = MarketStatus.UNKNOWN,
            opensAtLocal = day?.open,
            closesAtLocal = day?.close,
            reasonAr = day?.noteAr,
            reasonFr = day?.noteFr,
            confidence = 0.0,
            reportersCount = 0,
            source = Source.SEED,
            updatedAtEpochMs = null,
            freshness = freshness,
        )
    }

    // ----------------------------------------------------------------- week

    suspend fun loadWeek(): List<DaySchedule> = withContext(Dispatchers.IO) {
        val body = runCatching {
            client.get("market_schedule?market_slug=eq.${config.marketSlug}&select=*&order=day.asc").getOrNull()
        }.getOrNull()

        if (!body.isNullOrBlank()) {
            val parsed = runCatching {
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(DaySchedule.serializer()), body
                )
            }.getOrNull()
            if (!parsed.isNullOrEmpty()) {
                prefs.saveCachedWeek(json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(DaySchedule.serializer()), parsed
                ))
                return@withContext parsed
            }
        }

        val cached = prefs.cachedWeek()
        if (cached != null) {
            runCatching {
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(DaySchedule.serializer()), cached
                )
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
        }
        SntpSeed.week
    }

    // ---------------------------------------------------------------- shops

    suspend fun loadShops(): List<Shop> = withContext(Dispatchers.IO) {
        val body = runCatching {
            client.get("shops?market_slug=eq.${config.marketSlug}&select=*&order=name.asc").getOrNull()
        }.getOrNull()

        if (!body.isNullOrBlank()) {
            val parsed = runCatching {
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(ShopDto.serializer()), body
                )
            }.getOrNull()
            if (!parsed.isNullOrEmpty()) {
                prefs.saveCachedShops(json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(ShopDto.serializer()), parsed
                ))
                return@withContext parsed.map { it.toDomain() }
            }
        }

        val cached = prefs.cachedShops()
        if (cached != null) {
            runCatching {
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(ShopDto.serializer()), cached
                )
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it.map(ShopDto::toDomain) }
        }
        SntpSeed.shops
    }

    // -------------------------------------------------------------- history

    /** Day -> status map for the last ~120 days, for the calendar grid. */
    suspend fun loadHistory(): Map<String, String> = withContext(Dispatchers.IO) {
        val since = MarketTime.today().minusDays(120).toString()
        val body = runCatching {
            client.get(
                "state_history?market_slug=eq.${config.marketSlug}&day=gte.$since&select=day,status&order=day.desc"
            ).getOrNull()
        }.getOrNull() ?: return@withContext emptyMap()

        runCatching {
            val arr = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(HistoryRow.serializer()), body
            )
            arr.associate { it.day to it.status }
        }.getOrDefault(emptyMap())
    }

    // --------------------------------------------------------------- reports

    suspend fun postReport(payload: String): Result<String> = withContext(Dispatchers.IO) {
        client.post("reports", payload)
    }

    /** PostgREST RPC, used by the entitlement functions. */
    suspend fun rpc(name: String, bodyJson: String): Result<String> = withContext(Dispatchers.IO) {
        client.post("rpc/$name", bodyJson)
    }

    // -------------------------------------------------------------- sponsors

    /** Paid placements, already filtered by RLS to active and unexpired. */
    suspend fun loadSponsors(): List<Sponsor> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext emptyList()
        val body = runCatching {
            client.get(
                "sponsors?select=id,slot,name,phone,url&order=slot.asc"
            ).getOrNull()
        }.getOrNull() ?: return@withContext emptyList()

        runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(Sponsor.serializer()), body
            )
        }.getOrDefault(emptyList())
    }

    private fun isOnline(): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return@runCatching false
        val net = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(net) ?: return@runCatching false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    /** A trusted shop number the user can call when the app cannot confirm anything. */
    suspend fun trustedPhone(): String? = loadShops().firstOrNull { it.hasPhone }?.phone
}

private fun MarketStateDto.toDomain(freshness: Freshness, fetchedAt: Long) = MarketState(
    status = when (status.lowercase()) {
        "open" -> MarketStatus.OPEN
        "closed" -> MarketStatus.CLOSED
        else -> MarketStatus.UNKNOWN
    },
    opensAtLocal = opensAt,
    closesAtLocal = closesAt,
    reasonAr = reasonAr,
    reasonFr = reasonFr,
    confidence = confidence,
    reportersCount = reportersCount,
    source = when (source.lowercase()) {
        "admin" -> Source.ADMIN
        "traders" -> Source.TRADERS
        else -> Source.SEED
    },
    // The server's own `updated_at` is what "last update" means; the local fetch
    // time only stands in when an older cache predates that column. Showing the
    // fetch time told the trader a day-old state had just been updated.
    updatedAtEpochMs = IsoTime.toEpochMs(updatedAt).takeIf { it > 0 }
        ?: fetchedAt.takeIf { it > 0 },
    freshness = freshness,
)

private fun ShopDto.toDomain() = Shop(
    id = id,
    name = name,
    category = category,
    phone = phone,
    whatsapp = whatsapp,
    isVerified = isVerified,
    shopNumber = shopNumber,
)
