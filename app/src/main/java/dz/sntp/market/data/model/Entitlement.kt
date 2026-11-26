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
         * a month of Pro on every launch.
         *
         * What "the same purchase" means here is narrower than it looks. Play
         * issues ONE token per subscription and keeps it across every renewal —
         * only the order id gains a suffix per cycle — and the client-side
         * [com.android.billingclient.api.Purchase] exposes no expiry date at all.
         * So the device cannot see a renewal happen; it can only see that Play
         * still reports the subscription as purchased. The stored date is
         * therefore a backstop, not the truth:
         *
         *  · while it is still in the future, it stands — re-basing it on each
         *    launch is what made the tier permanent;
         *  · once it has passed, Play has either renewed (it keeps returning the
         *    purchase, so the date is re-based) or lapsed (it returns nothing, so
         *    [grantFromPlay] is never called and the tier falls away).
         *
         * A plan upgrade or downgrade does mint a new token, which re-bases the
         * date — correct, because it starts a new billing period.
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

            // Same subscription continuing: Play reported it as purchased before
            // and is reporting it again. Deliberately not gated on the token being
            // present — a null token (an older install that stored none) must still
            // extend rather than fall into the from-today branch, which is the
            // compounding path. A *different* token means upgrade/downgrade and
            // starts a fresh period.
            val continuing = previous != null &&
                previous.source == "play" &&
                (previousToken == null || token == null || previousToken == token)

            // Still inside the period we already granted: nothing to re-date.
            if (continuing && previous!!.expiresAtMs > now) {
                return previous.copy(tier = tierFor(sku))
            }

            // One day of grace: Play posts a renewal a little after the boundary.
            val days = if (yearly) 367 else 32

            // A renewal anchors on the expiry it is extending, not on today.
            // Anchoring on `now` looked harmless and was not: Play keeps the same
            // token for the whole life of a subscription, so every month the date
            // lapsed, got re-based from today, and carried its extra day along.
            // The error compounded — 32, 34, 36 days ahead of the real period end —
            // so a two-year subscriber was handed roughly two free months.
            // Extending the previous expiry keeps the grace constant forever.
            //
            // If the anchor is already in the past the subscription really did
            // stop (a long absence, a resubscribe), and Play reporting it as
            // purchased means a new period began now.
            val anchored = if (continuing) previous!!.expiresAtMs + days * DAY_MS else 0L
            val expiresAt = if (anchored > now) anchored else now + days * DAY_MS

            return Entitlement(
                tier = tierFor(sku),
                source = "play",
                expiresAtMs = expiresAt,
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
