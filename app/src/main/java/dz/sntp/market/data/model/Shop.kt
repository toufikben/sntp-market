package dz.sntp.market.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One row of the usual weekly schedule. Times are "HH:mm" local, null = closed. */
@Serializable
data class DaySchedule(
    @SerialName("day") val day: Int,            // 1 = Monday … 7 = Sunday (ISO)
    @SerialName("open") val open: String? = null,
    @SerialName("close") val close: String? = null,
    @SerialName("note_ar") val noteAr: String? = null,
    @SerialName("note_fr") val noteFr: String? = null,
)

@Serializable
data class ShopDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("category") val category: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("whatsapp") val whatsapp: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("verified_owner") val verifiedOwner: Boolean = false,
    @SerialName("shop_number") val shopNumber: String? = null,
    @SerialName("keyboard") val keyboard: String? = null,
    @SerialName("lat") val lat: Double? = null,
    @SerialName("lng") val lng: Double? = null,
)

data class Shop(
    val id: String,
    val name: String,
    val category: String?,
    val phone: String?,
    val whatsapp: String?,
    val isVerified: Boolean,
    val shopNumber: String?,
) {
    val hasPhone: Boolean get() = !phone.isNullOrBlank()
}

/** A single community report, either fresh from the server or still queued on device. */
@Serializable
data class ReportDto(
    @SerialName("id") val id: String? = null,
    @SerialName("market_id") val marketId: String,
    @SerialName("status") val status: String,
    @SerialName("opens_at") val opensAt: String? = null,
    @SerialName("closes_at") val closesAt: String? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("device_id") val deviceId: String,
    @SerialName("lat") val lat: Double? = null,
    @SerialName("lng") val lng: Double? = null,
    @SerialName("client_time") val clientTime: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** Where the user stands relative to the market, used to weight their vote. */
enum class Proximity { INSIDE, NEAR, FAR, UNKNOWN }

data class VoteOutcome(
    val accepted: Boolean,
    val queued: Boolean,
    val messageKind: VoteMessage,
)

enum class VoteMessage {
    SENT,
    QUEUED,
    NEED_LOCATION,
    TOO_FAR,
    ALREADY_VOTED,
    RATE_LIMITED,
    FAILED,
}
