package dz.sntp.market

import dz.sntp.market.data.model.Caveat
import dz.sntp.market.data.model.Freshness
import dz.sntp.market.data.model.MarketState
import dz.sntp.market.data.model.MarketStatus
import dz.sntp.market.data.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The banner under the card. Its whole job is to say "this was not verified just
 * now" — so it must not appear over an answer that was never verified in the
 * first place, and must not be missing over one that was verified earlier.
 *
 * The regression: a seed row (the usual timetable, read when the server could not
 * be reached) printed «آخر معلومة معروفة — غير مؤكدة الآن» directly under «لا
 * بيانات اليوم». The first line claimed a confirmation had existed and lapsed;
 * the second said nobody had ever reported. The widget and the notification
 * already checked the seed before freshness — the card was the only surface that
 * did not, and it is the one people read.
 */
class CaveatTest {

    private fun state(
        source: Source = Source.TRADERS,
        freshness: Freshness = Freshness.LIVE,
        status: MarketStatus = MarketStatus.OPEN,
    ) = MarketState(
        status = status,
        opensAtLocal = null,
        closesAtLocal = null,
        reasonAr = null,
        reasonFr = null,
        confidence = 0.9,
        reportersCount = 4,
        source = source,
        updatedAtEpochMs = null,
        freshness = freshness,
    )

    @Test
    fun `a live answer from the traders carries no caveat`() {
        assertEquals(Caveat.NONE, state().caveat)
    }

    @Test
    fun `an answer read from disk carries the stale caveat`() {
        assertEquals(Caveat.STALE, state(freshness = Freshness.CACHED).caveat)
        assertEquals(Caveat.STALE, state(freshness = Freshness.STALE).caveat)
    }

    @Test
    fun `no connection is named as offline, not as stale`() {
        // "Stale" is a report that aged; "offline" is a report we could not check.
        // The two send the trader to different actions, so the wording differs.
        assertEquals(Caveat.OFFLINE, state(freshness = Freshness.OFFLINE).caveat)
    }

    @Test
    fun `a seed row never claims a confirmation that lapsed`() {
        // The whole point. A seed is the usual timetable; nothing was ever
        // confirmed, so nothing can be "no longer confirmed".
        assertEquals(Caveat.NONE, state(source = Source.SEED, freshness = Freshness.STALE).caveat)
        assertEquals(Caveat.NONE, state(source = Source.SEED, freshness = Freshness.OFFLINE).caveat)
        assertEquals(
            Caveat.NONE,
            state(
                source = Source.SEED,
                freshness = Freshness.OFFLINE,
                status = MarketStatus.UNKNOWN,
            ).caveat,
        )
    }

    @Test
    fun `an admin row is still caveated when we could not reach the server`() {
        // Admin overrides are official, so they never carry the "seed" exemption —
        // but "we could not re-read this" is a fact about our connection, not about
        // who wrote the row, so the caveat stays.
        assertEquals(Caveat.NONE, state(source = Source.ADMIN).caveat)
        assertEquals(Caveat.STALE, state(source = Source.ADMIN, freshness = Freshness.CACHED).caveat)
        assertEquals(Caveat.OFFLINE, state(source = Source.ADMIN, freshness = Freshness.OFFLINE).caveat)
    }
}
