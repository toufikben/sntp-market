package dz.sntp.market.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * All market logic runs on Africa/Algiers. The device may be set to anything,
 * so nothing here trusts the phone's zone.
 */
object MarketTime {

    val ZONE: ZoneId = ZoneId.of("Africa/Algiers")

    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun now(): Instant = Instant.now()

    fun today(): LocalDate = LocalDate.now(ZONE)

    fun isoDay(d: LocalDate = today()): String = d.toString()

    /** ISO day number: Monday = 1 … Sunday = 7. */
    fun isoDayNumber(d: LocalDate = today()): Int = d.dayOfWeek.value

    fun parseTime(raw: String?): LocalTime? = runCatching {
        val cleaned = raw?.trim()?.replace("H", ":").orEmpty()
        if (cleaned.length < 4) return@runCatching null
        LocalTime.parse(cleaned.padStart(5, '0'), HHMM)
    }.getOrNull()

    fun format(time: LocalTime?): String? = time?.format(HHMM)

    fun minutesOfDay(t: LocalTime): Int = t.hour * 60 + t.minute

    fun nowMinutes(): Int {
        val n = LocalTime.now(ZONE)
        return n.hour * 60 + n.minute
    }

    /** Minutes from now until [target], wrapping to the next day when needed. */
    fun minutesUntil(target: String?): Int? {
        val t = parseTime(target) ?: return null
        val delta = minutesOfDay(t) - nowMinutes()
        return if (delta < 0) delta + 24 * 60 else delta
    }

    /** True when "now" sits inside [open, close). Handles windows that cross midnight. */
    fun isWithin(open: String?, close: String?, nowMin: Int = nowMinutes()): Boolean {
        val o = parseTime(open) ?: return false
        val c = parseTime(close) ?: return false
        val start = minutesOfDay(o)
        val end = minutesOfDay(c)
        return if (end > start) {
            nowMin in start until end
        } else {
            nowMin >= start || nowMin < end
        }
    }

    /** "05:12" style stamp for the trust strip. */
    fun stamp(epochMs: Long?): String? {
        if (epochMs == null || epochMs <= 0) return null
        val t = Instant.ofEpochMilli(epochMs).atZone(ZONE)
        return "%02d:%02d".format(t.hour, t.minute)
    }

    /**
     * Day + month + year in the user's locale. Hand-built strings such as
     * "$day $dayOfMonth $year" come out mangled in RTL, because the bidi
     * algorithm reorders the run: the month simply disappears.
     */
    fun dateLabel(d: LocalDate, locale: Locale): String =
        d.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale))

    /** "نوفمبر 2026" / "novembre 2026" — spelled out, because "11 / 2026" reads badly in RTL. */
    fun monthLabel(ym: java.time.YearMonth, locale: Locale): String =
        ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
}
