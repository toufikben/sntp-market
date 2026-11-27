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

    /**
     * How long a state may be shown without a stale badge.
     *
     * Matches the first step of the server's freshness decay (full weight for 45
     * minutes, then 0.7, 0.4, and nothing after three hours). It used to be 20
     * minutes, which flagged a confirmation as stale while the consensus engine
     * still counted it at full strength — the app disagreed with its own source of
     * truth. The badge now appears exactly when the evidence starts to weaken.
     */
    private val freshWindowMs = 45 * 60 * 1000L

    val isConfigured: Boolean get() = client.isConfigured

    /**
     * The market this build reads and votes on.
     *
     * Exposed because the vote payload had `SntpSeed.MARKET_ID` hardcoded while
     * every read went through [config]. With the shipped value they coincide, so
     * the bug was invisible — but the moment the slug is pointed anywhere else
     * (a second market, a staging project), the app reads one market's state and
     * writes votes into another: the trader's observation is accepted, counted
     * into a consensus nobody is watching, and the screen he is looking at never
     * changes. One source for both directions.
     */
    val marketSlug: String get() = config.marketSlug

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
            //
            // A row with a missing or unparseable updated_at is not evidence of
            // freshness either: `ageMs` used to be null there and fell into the
            // LIVE branch, so an old schema, a null column or a garbled timestamp
            // produced "السوق مفتوح" with no caveat at all. No timestamp means no
            // proof of recency, which is STALE, not LIVE.
            val ageMs = IsoTime.toEpochMs(remote.updatedAt).takeIf { it > 0 }?.let { now - it }
            val freshness = if (ageMs != null && ageMs <= freshWindowMs) {
                Freshness.LIVE
            } else {
                Freshness.STALE
            }
            val domain = withUsualHours(remote.toDomain(freshness, now), cachedWeekOrSeed())
            prefs.saveCachedState(
                json.encodeToString(MarketStateDto.serializer(), remote),
                now,
            )
            return@withContext domain
        }

        val (cachedJson, cachedAt) = prefs.cachedState()

        if (cachedJson != null) {
            val dto = runCatching {
                json.decodeFromString(MarketStateDto.serializer(), cachedJson)
            }.getOrNull()
            if (dto != null) {
                // The age that matters is the age of the server's information, not
                // the moment we happened to write the cache. Reading the row at
                // 07:00 that the server last touched at 05:00 and going offline left
                // the badge measuring from 07:00, so the evidence was always
                // reported fresher offline than online.
                val now = System.currentTimeMillis()
                val evidenceAge = IsoTime.toEpochMs(dto.updatedAt).takeIf { it > 0 }
                    ?.let { now - it }
                    ?: (now - cachedAt)
                // We are here because the server could not be asked, so the answer on
                // screen is a cached one either way. Whether the row is 5 minutes old
                // or 5 hours old, the promise of this app is that it says so: it used
                // to be CACHED when under 45 minutes and CACHED drew no warning at
                // all, so the common case — opening the app inside the market, where
                // there is no signal — showed "السوق مفتوح" with nothing marking it as
                // the last thing we heard rather than the current truth.
                val online = isOnline()
                val freshness = when {
                    !online -> Freshness.OFFLINE
                    evidenceAge <= freshWindowMs -> Freshness.CACHED
                    else -> Freshness.STALE
                }
                return@withContext withUsualHours(
                    dto.toDomain(freshness, cachedAt),
                    cachedWeekOrSeed(),
                )
            }
        }

        val freshness = if (isOnline()) Freshness.STALE else Freshness.OFFLINE

        // Nothing at all: fall back to the seed schedule so the screen is never empty.
        seedState(freshness)
    }

    /**
     * The last known state, read from disk with no network call. The home-screen
     * widget uses this: it must show the same freshness the app would show for the
     * same row, so the freshness rules live in exactly one place — here, shared
     * with [loadState]'s offline branch.
     *
     * Returns null when nothing has ever been cached, which is different from
     * "cached and unknown".
     */
    suspend fun cachedStateOnly(): MarketState? = withContext(Dispatchers.IO) {
        val (cachedJson, cachedAt) = prefs.cachedState()
        val dto = cachedJson?.let {
            runCatching { json.decodeFromString(MarketStateDto.serializer(), it) }.getOrNull()
        } ?: return@withContext null

        val now = System.currentTimeMillis()
        val evidenceAge = IsoTime.toEpochMs(dto.updatedAt).takeIf { it > 0 }
            ?.let { now - it }
            ?: (now - cachedAt)
        val freshness = if (evidenceAge <= freshWindowMs) Freshness.CACHED else Freshness.STALE
        withUsualHours(dto.toDomain(freshness, cachedAt), cachedWeekOrSeed())
    }

    /**
     * An unknown state carries no hours, so the card printed "لا بيانات اليوم"
     * while the week tab one tap away knew today's usual window — and the offline
     * path, which falls back to the seed, showed that window. The same market had
     * two answers depending on the network.
     *
     * Filling the gap here keeps the label honest: the card words these hours
     * "المعتاد", never a confirmation. It lives in the repository rather than in
     * the screen so the widget, which reads only the cache, cannot disagree.
     */
    private fun withUsualHours(state: MarketState, week: List<DaySchedule>): MarketState {
        if (state.status != MarketStatus.UNKNOWN) return state
        if (state.opensAtLocal != null || state.closesAtLocal != null) return state
        val today = week.firstOrNull { it.day == MarketTime.isoDayNumber() } ?: return state
        if (today.open == null) return state
        return state.copy(opensAtLocal = today.open, closesAtLocal = today.close)
    }

    private suspend fun cachedWeekOrSeed(): List<DaySchedule> {
        val cached = prefs.cachedWeek() ?: return SntpSeed.week
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(DaySchedule.serializer()), cached
            )
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: SntpSeed.week
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

    /**
     * Day -> status map for the last ~120 days, for the calendar grid.
     *
     * The second value says the server could not be asked in this read — the map is
     * either the copy from disk or nothing at all. Without it the screen cannot tell
     * "this market has no record" from "I could not ask", and it printed
     * "لا سجل بعد" over a record that existed.
     */
    suspend fun loadHistory(): Pair<Map<String, String>, Boolean> = withContext(Dispatchers.IO) {
        // From the first day of the oldest month the calendar can show, not from
        // "today minus 120 days". The grid navigates by whole months, so a cut-off
        // in the middle of one (120 days before the 27th lands on the 30th of July)
        // left that entire month openable while its earlier days had never been
        // fetched: they rendered as "no record", which is a claim about the market
        // made out of a missing query parameter. The floor and the query are now the
        // same date.
        val since = MarketTime.today()
            .minusDays(HISTORY_DAYS)
            .withDayOfMonth(1)
            .toString()
        val body = runCatching {
            client.get(
                "state_history?market_slug=eq.${config.marketSlug}&day=gte.$since&select=day,status&order=day.desc"
            ).getOrNull()
        }.getOrNull()

        val parsed = body?.let {
            runCatching {
                val arr = json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(HistoryRow.serializer()), it
                )
                arr.associate { row -> row.day to row.status }
            }.getOrNull()
        }

        if (parsed != null) {
            prefs.saveCachedHistory(json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(HistoryRow.serializer()),
                parsed.map { HistoryRow(it.key, it.value) },
            ))
            return@withContext parsed to false
        }

        val cached = prefs.cachedHistory()?.let {
            runCatching {
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(HistoryRow.serializer()), it
                )
            }.getOrNull()?.associate { row -> row.day to row.status }
        }

        // The flag means "this map did not come from the server", not "a copy was
        // found". Returning `false` when the fetch failed and nothing was cached
        // told the screen the empty map was a real answer, and the calendar printed
        // «لا سجل بعد» — "no record yet", a claim about the market — over what was
        // only a failed request. Nothing distinguishes the two in the data, so the
        // one bit has to describe the read, and the screen decides the wording
        // (the cache line when there is a copy, the offline line when there is not).
        (cached ?: emptyMap()) to true
    }

    // --------------------------------------------------------------- reports

    suspend fun postReport(payload: String): Result<String> = withContext(Dispatchers.IO) {
        // reports is insert-only: no SELECT policy, so asking for the inserted row
        // back is a read the caller may not perform.
        client.post("reports", payload, prefer = "return=minimal")
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
                // `id.asc` is a tie-breaker, not decoration. PostgREST gives no
                // order for equal keys, so two rows in the same slot (an overlap
                // the constraint below should have prevented, or a slot whose old
                // row was left `active`) came back in whatever order the planner
                // chose. The screen picks `firstOrNull { it.slot == 1 }`, so the
                // sponsor shown could change between two refreshes without a
                // single row being edited.
                "sponsors?select=id,slot,name,phone,url&order=slot.asc,id.asc"
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

    companion object {
        /**
         * How far back the calendar reaches. Shared with [loadHistory]'s query so the
         * oldest month the grid opens and the oldest day it asked for are the same
         * date; they used to drift apart and the gap rendered as empty history.
         */
        const val HISTORY_DAYS = 120L
    }
}

private fun MarketStateDto.toDomain(freshness: Freshness, fetchedAt: Long): MarketState {
    val src = when (source.lowercase()) {
        "admin" -> Source.ADMIN
        "traders" -> Source.TRADERS
        else -> Source.SEED
    }
    return MarketState(
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
        source = src,
        // The server's own `updated_at` is what "last update" means; the local fetch
        // time only stands in when an older cache predates that column. Showing the
        // fetch time told the trader a day-old state had just been updated.
        //
        // A seed row has no update to report. Its timestamp is only the moment the
        // row was created by the schema (and, after a quiet spell, the last vote
        // that has long since aged out of the consensus), so printing it as
        // "آخر تحديث" told the trader somebody had confirmed the market at that
        // hour. Nothing ever did — the source line two rows above says so — and the
        // card contradicted itself in the one place a glance is supposed to settle.
        updatedAtEpochMs = if (src == Source.SEED) {
            null
        } else {
            IsoTime.toEpochMs(updatedAt).takeIf { it > 0 } ?: fetchedAt.takeIf { it > 0 }
        },
        freshness = freshness,
    )
}

private fun ShopDto.toDomain() = Shop(
    id = id,
    name = name,
    category = category,
    phone = phone,
    whatsapp = whatsapp,
    isVerified = isVerified,
    shopNumber = shopNumber,
)
