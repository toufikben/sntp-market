package dz.sntp.market

import dz.sntp.market.data.model.Freshness
import dz.sntp.market.data.model.MarketState
import dz.sntp.market.data.model.MarketStatus
import dz.sntp.market.data.model.Source
import dz.sntp.market.util.MarketTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * "لا بيانات اليوم" is a claim the card and the widget both make, and it is only
 * true when nobody reported anything today. The server zeroes reporters_count and
 * confidence three hours after the last vote ages out, so those two fields alone
 * answer "is anyone confirming right now", not "did anyone confirm today".
 */
class NoDataTodayTest {

    private fun state(
        reporters: Int = 0,
        confidence: Double = 0.0,
        updatedAt: Long? = null,
        status: MarketStatus = MarketStatus.UNKNOWN,
        source: Source = Source.TRADERS,
    ) = MarketState(
        status = status,
        opensAtLocal = null,
        closesAtLocal = null,
        reasonAr = null,
        reasonFr = null,
        confidence = confidence,
        reportersCount = reporters,
        source = source,
        updatedAtEpochMs = updatedAt,
        freshness = Freshness.LIVE,
    )

    private fun hoursAgo(h: Long) =
        Instant.now().minus(h, ChronoUnit.HOURS).toEpochMilli()

    /**
     * 08:00 *today in the market's timezone*, not "eight hours ago".
     *
     * The test asserted a day reported at 08:00 and aged out by 16:00, and used
     * `hoursAgo(8)` for it. At 05:46 UTC that is 22:45 the previous evening in
     * Algiers, so the timestamp was yesterday's and `hasNoDataToday` correctly
     * came out true — the test failed on the hour CI happened to run, not on the
     * behaviour it was written to pin.
     */
    private fun todayAt(hour: Int): Long =
        MarketTime.today().atTime(hour, 0).atZone(MarketTime.ZONE).toInstant().toEpochMilli()

    @Test
    fun `a market nobody has ever reported has no data`() {
        assertTrue(state().hasNoDataToday)
    }

    @Test
    fun `votes still counting are not "no data"`() {
        assertFalse(state(reporters = 3, confidence = 0.8).hasNoDataToday)
    }

    @Test
    fun `a day reported at 08 and aged out by 16 is still not "no data"`() {
        // This is the regression: votes expire from the consensus after three
        // hours, so the afternoon recompute writes 0 reporters and 0 confidence
        // over a day the calendar still records as confirmed.
        assertFalse(state(updatedAt = todayAt(8)).hasNoDataToday)
    }

    @Test
    fun `yesterday's evidence does not count as today's`() {
        assertTrue(state(updatedAt = hoursAgo(30)).hasNoDataToday)
    }

    @Test
    fun `a row with no evidence timestamp is 'no data'`() {
        // The server writes no timestamp until a vote has actually been counted.
        // A fresh row used to be stamped `now()` by the column default, so the card
        // claimed traders had disagreed on a market nobody had ever reported.
        assertTrue(state(updatedAt = null).hasNoDataToday)
    }

    @Test
    fun `a confirmed status is never "no data"`() {
        assertFalse(state(status = MarketStatus.OPEN).hasNoDataToday)
        assertFalse(state(status = MarketStatus.CLOSED).hasNoDataToday)
    }
}
