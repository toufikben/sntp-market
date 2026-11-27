package dz.sntp.market

import dz.sntp.market.util.Geo
import dz.sntp.market.util.MarketTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The maths that decides whether the app tells a trader to drive 200 km.
 * These are the tests that matter; everything else is presentation.
 */
class VoteWeightTest {

    private val voteRadius = 1200.0
    private val farRadius = 5000.0

    @Test
    fun `inside the market counts fully`() {
        val w = Geo.proximityWeight(0.0, voteRadius, farRadius)
        assertEquals(1.0, w, 0.0001)
    }

    @Test
    fun `at the radius edge still counts fully`() {
        val w = Geo.proximityWeight(voteRadius, voteRadius, farRadius)
        assertEquals(1.0, w, 0.0001)
    }

    @Test
    fun `just outside the radius is heavily discounted`() {
        val w = Geo.proximityWeight(voteRadius + 1, voteRadius, farRadius)
        assertEquals(0.35, w, 0.0001)
    }

    @Test
    fun `beyond five kilometres counts for nothing`() {
        val w = Geo.proximityWeight(farRadius + 1, voteRadius, farRadius)
        assertEquals(0.0, w, 0.0001)
    }

    @Test
    fun `reputation scales the weight`() {
        assertEquals(1.8, Geo.reputationWeight(180), 0.0001)
        assertEquals(1.4, Geo.reputationWeight(140), 0.0001)
        assertEquals(1.0, Geo.reputationWeight(100), 0.0001)
        assertEquals(0.6, Geo.reputationWeight(60), 0.0001)
        assertEquals(0.4, Geo.reputationWeight(20), 0.0001)
    }

    @Test
    fun `a report older than three hours is worth nothing`() {
        assertEquals(1.0, Geo.recencyWeight(10), 0.0001)
        assertEquals(0.7, Geo.recencyWeight(60), 0.0001)
        assertEquals(0.4, Geo.recencyWeight(150), 0.0001)
        assertEquals(0.0, Geo.recencyWeight(200), 0.0001)
    }

    @Test
    fun `a far away discredited late vote cannot move the state`() {
        val w = Geo.totalWeight(
            distanceM = 20_000.0,
            reputationX100 = 20,
            ageMinutes = 300,
            voteRadius = voteRadius,
            farRadius = farRadius,
        )
        assertEquals(0.0, w, 0.0001)
    }

    @Test
    fun `a nearby trusted fresh vote dominates`() {
        val trusted = Geo.totalWeight(50.0, 180, 5, voteRadius, farRadius)
        val weak = Geo.totalWeight(3000.0, 60, 120, voteRadius, farRadius)
        assertTrue("trusted=$trusted weak=$weak", trusted > weak * 5)
    }

    @Test
    fun `distance between two known points is close to reality`() {
        // SNTP centre to the El Hamiz market centre, roughly 1 km apart.
        val d = Geo.distanceMeters(36.724663, 3.222052, 36.73018, 3.230711)
        assertTrue("distance=$d", d in 500.0..2000.0)
    }

    @Test
    fun `a precise fix at the edge counts, an uncertain one does not`() {
        // The weight is computed from distance + the fix's own uncertainty, which
        // is what the server does. A cell-tower fix 200 m away and accurate to 3 km
        // is not evidence that the trader is inside the market: it falls out of the
        // full-weight radius and is discounted like any other distant report.
        val precise = Geo.proximityWeight(200.0 + 10.0, voteRadius, farRadius)
        val vague = Geo.proximityWeight(200.0 + 3000.0, voteRadius, farRadius)
        assertEquals(1.0, precise, 0.0001)
        assertEquals(0.35, vague, 0.0001)
        // And past the outer radius it counts for nothing at all, which is the case
        // that used to be accepted with full weight before accuracy was read.
        assertEquals(0.0, Geo.proximityWeight(400.0 + 6000.0, voteRadius, farRadius), 0.0001)
    }

    @Test
    fun `zero distance to itself`() {
        val d = Geo.distanceMeters(36.724663, 3.222052, 36.724663, 3.222052)
        assertEquals(0.0, d, 0.5)
    }
}

class MarketTimeTest {

    @Test
    fun `parses the formats the data actually contains`() {
        assertEquals(7, MarketTime.parseTime("07:00")?.hour)
        assertEquals(0, MarketTime.parseTime("07:00")?.minute)
        assertEquals(7, MarketTime.parseTime("7:00")?.hour)
        assertEquals(13, MarketTime.parseTime("13:00")?.hour)
        assertEquals(6, MarketTime.parseTime("06:00")?.hour)
    }

    @Test
    fun `rejects nonsense instead of crashing`() {
        assertNull(MarketTime.parseTime(null))
        assertNull(MarketTime.parseTime(""))
        assertNull(MarketTime.parseTime("مغلق"))
    }

    @Test
    fun `normal window contains midday but not midnight`() {
        assertTrue(MarketTime.isWithin("07:00", "13:00", 8 * 60))
        assertTrue(MarketTime.isWithin("07:00", "13:00", 7 * 60))
        assertFalse(MarketTime.isWithin("07:00", "13:00", 13 * 60))
        assertFalse(MarketTime.isWithin("07:00", "13:00", 2 * 60))
    }

    @Test
    fun `window crossing midnight is handled`() {
        // 22:00 → 02:00
        assertTrue(MarketTime.isWithin("22:00", "02:00", 23 * 60))
        assertTrue(MarketTime.isWithin("22:00", "02:00", 1 * 60))
        assertFalse(MarketTime.isWithin("22:00", "02:00", 12 * 60))
    }

    @Test
    fun `stamps use Latin digits whatever the phone's locale`() {
        // `"%02d:%02d".format(...)` follows the default locale and printed
        // «٠٥:١٢» on an Arabic phone, on the same line as the server's "07:00".
        val old = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-DZ"))
            val ms = java.time.ZonedDateTime.now(MarketTime.ZONE)
                .withHour(5).withMinute(12).withSecond(0).withNano(0)
                .toInstant().toEpochMilli()
            assertEquals("05:12", MarketTime.stamp(ms))
            // Today's stamp is the bare hour, so the same assertion holds.
            assertEquals("05:12", MarketTime.stampWithDay(ms, java.util.Locale("ar")))
        } finally {
            java.util.Locale.setDefault(old)
        }
    }

    @Test
    fun `closed day never reports open`() {
        assertFalse(MarketTime.isWithin(null, null, 600))
    }
}
