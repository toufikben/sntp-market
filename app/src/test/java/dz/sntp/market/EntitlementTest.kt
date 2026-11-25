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
}
