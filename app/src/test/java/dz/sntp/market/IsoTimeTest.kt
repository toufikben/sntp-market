package dz.sntp.market

import dz.sntp.market.util.IsoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Postgres hands back timestamptz as `+00:00`, not `Z`. Parsing that with
 * Instant.parse threw and callers read 0, which meant "never expires" — a lapsed
 * subscription stayed Pro. These cases pin the shapes Supabase actually sends.
 */
class IsoTimeTest {

    private val expected = 1_764_048_785_328L // 2025-11-25T05:33:05.328Z

    @Test
    fun `zulu instant parses`() {
        assertEquals(expected, IsoTime.toEpochMs("2025-11-25T05:33:05.328Z"))
    }

    @Test
    fun `numeric offset parses`() {
        assertEquals(expected, IsoTime.toEpochMs("2025-11-25T05:33:05.328+00:00"))
    }

    @Test
    fun `microsecond precision parses`() {
        assertEquals(expected, IsoTime.toEpochMs("2025-11-25T05:33:05.328411+00:00"))
    }

    @Test
    fun `space separated timestamp without offset is utc`() {
        assertEquals(expected, IsoTime.toEpochMs("2025-11-25 05:33:05.328"))
    }

    @Test
    fun `null and empty are zero`() {
        assertEquals(0L, IsoTime.toEpochMs(null))
        assertEquals(0L, IsoTime.toEpochMs(""))
        assertEquals(0L, IsoTime.toEpochMs("null"))
    }

    @Test
    fun `garbage is zero and not an exception`() {
        assertEquals(0L, IsoTime.toEpochMs("not a date"))
    }

    @Test
    fun `a lapsed expiry from the server really is in the past`() {
        val past = IsoTime.toEpochMs("2020-01-01T00:00:00.000+00:00")
        assertTrue(past > 0)
        assertTrue(past < System.currentTimeMillis())
    }
}
