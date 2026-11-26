package dz.sntp.market

import dz.sntp.market.data.model.Entitlement
import dz.sntp.market.data.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementTest {

    @Test
    fun `free is not pro`() {
        assertFalse(Entitlement().isPro)
        assertFalse(Entitlement().effective().isPro)
    }

    @Test
    fun `play sku maps to the right tier`() {
        assertEquals(Tier.PRO, Entitlement.fromSku("pro_monthly").tier)
        assertEquals(Tier.PRO, Entitlement.fromSku("pro_yearly").tier)
        assertEquals(Tier.BOUTIQUE, Entitlement.fromSku("boutique_monthly").tier)
        assertEquals("play", Entitlement.fromSku("pro_monthly").source)
    }

    @Test
    fun `a lapsed subscription falls back to free`() {
        val yesterday = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val lapsed = Entitlement(Tier.PRO, "play", yesterday)
        assertTrue(lapsed.isExpired())
        assertEquals(Tier.FREE, lapsed.effective().tier)
    }

    @Test
    fun `a live subscription stays pro`() {
        val nextMonth = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000L
        val live = Entitlement(Tier.PRO, "play", nextMonth)
        assertFalse(live.isExpired())
        assertEquals(Tier.PRO, live.effective().tier)
    }

    @Test
    fun `lifetime promo never expires`() {
        val promo = Entitlement(Tier.PRO, "promo", 0L)
        assertFalse(promo.isExpired())
        assertEquals(Tier.PRO, promo.effective().tier)
    }

    @Test
    fun `server row with nulls is free`() {
        val e = Entitlement.fromServer(tier = null, source = null, expiresAtMs = 0L)
        assertEquals(Tier.FREE, e.tier)
    }

    @Test
    fun `server row with iso expiry keeps the tier`() {
        val e = Entitlement.fromServer("boutique", "admin", 0L)
        assertEquals(Tier.BOUTIQUE, e.tier)
        assertEquals("admin", e.source)
    }

    @Test
    fun `a play grant carries an end date so it can lapse`() {
        val monthly = Entitlement.fromSku("pro_monthly")
        assertTrue(monthly.expiresAtMs > 0)
        assertFalse(monthly.isExpired())

        val inFortyDays = System.currentTimeMillis() + 40L * 24 * 60 * 60 * 1000L
        assertTrue(monthly.isExpired(inFortyDays))

        val yearly = Entitlement.fromSku("pro_yearly")
        val inTwoMonths = System.currentTimeMillis() + 60L * 24 * 60 * 60 * 1000L
        assertFalse("a yearly plan must outlive two months", yearly.isExpired(inTwoMonths))
        assertTrue(yearly.isExpired(System.currentTimeMillis() + 400L * 24 * 60 * 60 * 1000L))
    }

    /**
     * restore() runs on every launch and replays the purchase already stored. The
     * expiry must not move: otherwise a cancelled subscriber who keeps opening the
     * app is granted a fresh month each time and never lapses.
     */
    @Test
    fun `replaying the same purchase token does not extend the expiry`() {
        val now = 1_000_000_000_000L
        val first = Entitlement.fromSku("pro_monthly", now = now, token = "tok-A")

        val later = now + 20L * 24 * 60 * 60 * 1000L
        val replay = Entitlement.fromSku(
            "pro_monthly",
            now = later,
            previous = first,
            previousToken = "tok-A",
            token = "tok-A",
        )

        assertEquals(first.expiresAtMs, replay.expiresAtMs)
        // Twenty days later it must still expire when the first month ends.
        assertTrue(replay.isExpired(now + 33L * 24 * 60 * 60 * 1000L))
    }

    /**
     * Play keeps one token for the whole life of a subscription and exposes no
     * expiry on the client, so a renewal is invisible to the device: the token
     * repeats and the date must be extended from the period it extends.
     */
    @Test
    fun `a renewal with the same token extends from the previous expiry`() {
        val now = 1_000_000_000_000L
        val first = Entitlement.fromSku("pro_monthly", now = now, token = "tok-A")

        val renewed = Entitlement.fromSku(
            "pro_monthly",
            now = now + 31L * 24 * 60 * 60 * 1000L,
            previous = first,
            previousToken = "tok-A",
            token = "tok-A",
        )

        // 32 days after the period end, not 32 days after today: the grace day
        // must not be re-spent on every cycle.
        assertEquals(first.expiresAtMs + 32L * 24 * 60 * 60 * 1000L, renewed.expiresAtMs)
    }

    /**
     * The bug this guards: anchoring each renewal on `now` re-spent the grace day
     * every month, so the date drifted a couple of days further ahead each cycle.
     * Two years of renewals handed out roughly two free months.
     */
    @Test
    fun `the grace day does not compound across renewals`() {
        var now = 1_000_000_000_000L
        var e = Entitlement.fromSku("pro_monthly", now = now, token = "tok-A")
        val firstExpiry = e.expiresAtMs

        // Two years of monthly renewals, each checked the day after the stored
        // date lapses — exactly when a real subscriber opens the app.
        repeat(24) {
            now = e.expiresAtMs + 24L * 60 * 60 * 1000L
            e = Entitlement.fromSku(
                "pro_monthly",
                now = now,
                previous = e,
                previousToken = "tok-A",
                token = "tok-A",
            )
        }

        // 24 renewals of 32 days from the original expiry: the grace stays one
        // day per period instead of growing with the number of renewals.
        val expected = firstExpiry + 24 * 32L * 24 * 60 * 60 * 1000L
        assertEquals(expected, e.expiresAtMs)
        // And the drift never exceeded a single period.
        assertTrue(e.expiresAtMs - now < 33L * 24 * 60 * 60 * 1000L)
    }

    /** An upgrade or downgrade mints a new token and starts a new period. */
    @Test
    fun `a new token starts a new period`() {
        val now = 1_000_000_000_000L
        val monthly = Entitlement.fromSku("pro_monthly", now = now, token = "tok-A")

        val yearly = Entitlement.fromSku(
            "pro_yearly",
            now = now + 5L * 24 * 60 * 60 * 1000L,
            previous = monthly,
            previousToken = "tok-A",
            token = "tok-B",
        )

        assertEquals(Tier.PRO, yearly.tier)
        assertTrue(yearly.expiresAtMs > monthly.expiresAtMs)
        assertFalse(yearly.isExpired(now + 300L * 24 * 60 * 60 * 1000L))
    }

    /**
     * Play still reports PURCHASED during its own grace period, after our local
     * date has passed. That answer is Play's, so it is honoured — but only once per
     * token, and it still carries an end date, so the revival is a month, not
     * forever.
     */
    @Test
    fun `a lapsed row Play still reports as active is revived once, with an end date`() {
        val now = 1_000_000_000_000L
        val lapsed = Entitlement(Tier.PRO, "play", now - 1L)

        val revived = Entitlement.fromSku(
            "pro_monthly",
            now = now,
            previous = lapsed,
            previousToken = "tok-A",
            token = "tok-A",
        )

        assertTrue(revived.expiresAtMs > now)
        // And it lapses again: this is not a lifetime grant.
        assertTrue(revived.isExpired(now + 33L * 24 * 60 * 60 * 1000L))
    }

    /**
     * A device that stored no token (an older install) must still be treated as
     * continuing. Falling through to the from-today branch is the compounding
     * path, so a null token cannot be allowed to mean "new subscription".
     */
    @Test
    fun `a missing token still extends rather than restarting the period`() {
        val now = 1_000_000_000_000L
        val first = Entitlement(Tier.PRO, "play", now + 5L * 24 * 60 * 60 * 1000L)

        val next = Entitlement.fromSku(
            "pro_monthly",
            now = now + 6L * 24 * 60 * 60 * 1000L,
            previous = first,
            previousToken = null,
            token = "tok-A",
        )

        assertEquals(
            first.expiresAtMs + 32L * 24 * 60 * 60 * 1000L,
            next.expiresAtMs,
        )
    }
}
