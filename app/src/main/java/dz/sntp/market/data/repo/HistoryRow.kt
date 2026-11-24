package dz.sntp.market.data.repo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HistoryRow(
    @SerialName("day") val day: String,
    @SerialName("status") val status: String,
)
