package dz.sntp.market.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What the market is doing right now. */
enum class MarketState { OPEN, CLOSED, UNKNOWN }

/** Where the information came from — drives the trust strip. */
enum class Source { ADMIN, TRADERS, SEED }

/** How fresh the displayed state is. */
enum class Freshness {
    /** Fetched from the server during this launch. */
    LIVE,

    /** From disk, within the TTL window. */
    CACHED,

    /** From disk, past the TTL window: shown with an explicit warning. */
    STALE,

    /** No connection at all and nothing usable on disk. */
    OFFLINE,
}

@Serializable
data class MarketStateDto(
    @SerialName("market_id") val marketId: String? = null,
    @SerialName("status") val status: String = "unknown",
    @SerialName("opens_at") val opensAt: String? = null,
    @SerialName("closes_at") val closesAt: String? = null,
    @SerialName("reason_ar") val reasonAr: String? = null,
    @SerialName("reason_fr") val reasonFr: String? = null,
    @SerialName("confidence") val confidence: Double = 0.0,
    @SerialName("reporters_count") val reportersCount: Int = 0,
    @SerialName("source") val source: String = "seed",
    @SerialName("updated_at") val updatedAt: String? = null,
)

/**
 * The domain object the UI renders. Always carries its own freshness so the
 * screen can never silently present old data as current.
 */
data class MarketState(
    val state: MarketState,
    val opensAtLocal: String?,
    val closesAtLocal: String?,
    val reasonAr: String?,
    val reasonFr: String?,
    val confidence: Double,
    val reportersCount: Int,
    val source: Source,
    val updatedAtEpochMs: Long?,
    val freshness: Freshness,
) {
    val isTrustworthy: Boolean get() = source == Source.ADMIN || confidence >= 0.6

    companion object {
        fun unknown(freshness: Freshness) = MarketState(
            state = MarketState.UNKNOWN,
            opensAtLocal = null,
            closesAtLocal = null,
            reasonAr = null,
            reasonFr = null,
            confidence = 0.0,
            reportersCount = 0,
            source = Source.SEED,
            updatedAtEpochMs = null,
            freshness = freshness,
        )
    }
}
