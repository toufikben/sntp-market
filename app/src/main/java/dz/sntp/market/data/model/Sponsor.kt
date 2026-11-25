package dz.sntp.market.data.model

import kotlinx.serialization.Serializable

/** A paid placement from the `sponsors` table. Empty list on a fresh project. */
@Serializable
data class Sponsor(
    val id: Long = 0,
    val slot: Int = 0,
    val name: String = "",
    val phone: String? = null,
    val url: String? = null,
) {
    val hasPhone: Boolean get() = !phone.isNullOrBlank()
    val hasUrl: Boolean get() = !url.isNullOrBlank()
}
