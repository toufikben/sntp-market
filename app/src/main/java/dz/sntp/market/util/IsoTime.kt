package dz.sntp.market.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Timestamps arrive from PostgREST in several shapes depending on the column
 * type: `timestamptz` comes back as `2026-11-25T05:33:05.328411+00:00`, while
 * `Instant.parse` only accepts `...Z`. Parsing those with `Instant.parse` threw,
 * and every caller caught it as 0 — which meant a subscription expiry was read
 * as "never expires" and a Pro grant survived cancellation.
 *
 * One parser, tolerant of `Z`, numeric offsets, and a space instead of `T`.
 */
object IsoTime {

    fun toEpochMs(raw: String?): Long {
        val iso = raw?.trim()
        if (iso.isNullOrBlank() || iso == "null") return 0L

        runCatching { return OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(iso).toEpochMilli() }
        runCatching {
            // Postgres text output without an offset is UTC on Supabase.
            val base = iso.substringBefore('+').substringBefore('Z').trim()
            return LocalDateTime.parse(base.replace(' ', 'T'))
                .toInstant(ZoneOffset.UTC).toEpochMilli()
        }
        return 0L
    }
}
