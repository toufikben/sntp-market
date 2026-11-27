package dz.sntp.market

import dz.sntp.market.data.model.Caveat
import dz.sntp.market.data.model.Freshness
import dz.sntp.market.data.model.MarketState
import dz.sntp.market.data.model.MarketStatus
import dz.sntp.market.data.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The banner under the card, and the third line on the widget.
 *
 * Its job is to say "this answer may no longer be true" — so it must fire on
 * evidence that has aged, and must stay quiet over evidence that has not, however
 * that evidence reached the screen.
 *
 * Two regressions are pinned here:
 *
 *  • The widget printed «آخر معلومة معروفة — غير مؤكدة الآن» on *every* redraw, in
 *    the warning colour. The rule was `freshness != LIVE`, and the widget reads the
 *    cache by design, so it is never LIVE — a row the morning worker had written
 *    three minutes earlier was labelled as expired. The one surface guaranteed to
 *    be there at 05:00 was the one where the warning meant nothing.
 *
 *  • A seed row (the usual timetable) printed the same line, claiming a
 *    confirmation had existed and lapsed, directly under «لا بيانات اليوم».
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
    fun `a live answer carries no caveat`() {
        assertEquals(Caveat.NONE, state().caveat)
    }

    @Test
    fun `a fresh row read from disk is not a warning`() {
        // CACHED means "inside the 45-minute window, read from disk". Whether this
        // process fetched it is not a fact about the market, and the widget can
        // never be LIVE — keying the banner on that made it permanent there.
        assertEquals(Caveat.NONE, state(freshness = Freshness.CACHED).caveat)
    }

    @Test
    fun `evidence past the window carries the stale caveat`() {
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
        assertEquals(Caveat.STALE, state(source = Source.ADMIN, freshness = Freshness.STALE).caveat)
        assertEquals(Caveat.OFFLINE, state(source = Source.ADMIN, freshness = Freshness.OFFLINE).caveat)
    }
}
