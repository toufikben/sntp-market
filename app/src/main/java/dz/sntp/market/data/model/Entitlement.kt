package dz.sntp.market.data.model

/**
 * Paid level. `source` matters: the same Pro can come from Play, from the
 * first-500 promo, or from an admin grant, and only Play renews by itself.
 */
enum class Tier { FREE, PRO, BOUTIQUE }

data class Entitlement(
    val tier: Tier = Tier.FREE,
    val source: String = "none",
    /** 0 = never expires (promo and admin grants). */
    val expiresAtMs: Long = 0L,
) {
    val isPro: Boolean get() = tier != Tier.FREE

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        expiresAtMs in 1..now

    fun effective(now: Long = System.currentTimeMillis()): Entitlement =
        if (isExpired(now)) Entitlement() else this

    companion object {
        fun fromSku(sku: String): Entitlement = Entitlement(
            tier = if (sku.startsWith("boutique")) Tier.BOUTIQUE else Tier.PRO,
            source = "play",
        )

        fun fromServer(tier: String?, source: String?, expiresAtMs: Long): Entitlement =
            Entitlement(
                tier = when (tier?.lowercase()) {
                    "pro" -> Tier.PRO
                    "boutique" -> Tier.BOUTIQUE
                    else -> Tier.FREE
                },
                source = source ?: "server",
                expiresAtMs = expiresAtMs,
            )
    }
}
