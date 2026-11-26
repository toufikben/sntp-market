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
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /**
         * A Play purchase must carry an end date. Without one a cancelled
         * monthly plan stayed Pro forever on the device: `restore()` only
         * replays *active* purchases, and the server never downgrades a Play
         * grant, so nothing could ever take the tier away again.
         *
         * Play itself honours the paid period after a cancellation, so locally
         * expiring at the end of that period is correct rather than harsh: the
         * next launch calls restore(), and a live subscription renews the date.
         *
         * [previous] and [previousToken] are what make that true. restore() runs
         * on every launch and replays the *same* purchase, so recomputing
         * now + 32 days each time pushed the expiry forward forever: a cancelled
         * subscriber who kept opening the app never expired, and the app granted
         * a month of Pro on every launch. The same token means the same paid
         * period, so the existing expiry stands; only a new token (a real
         * renewal) moves it.
         */
        fun fromSku(
            sku: String,
            now: Long = System.currentTimeMillis(),
            previous: Entitlement? = null,
            previousToken: String? = null,
            token: String? = null,
        ): Entitlement {
            val yearly = sku.contains("year", ignoreCase = true) ||
                sku.contains("annual", ignoreCase = true)

            val samePlayPeriod = previous != null &&
                previous.source == "play" &&
                previousToken != null &&
                token != null &&
                previousToken == token &&
                previous.expiresAtMs > now

            if (samePlayPeriod) {
                return previous.copy(tier = tierFor(sku))
            }

            // One day of grace: Play posts a renewal a little after the boundary.
            val days = if (yearly) 367 else 32
            return Entitlement(
                tier = tierFor(sku),
                source = "play",
                expiresAtMs = now + days * DAY_MS,
            )
        }

        private fun tierFor(sku: String): Tier =
            if (sku.startsWith("boutique")) Tier.BOUTIQUE else Tier.PRO

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
