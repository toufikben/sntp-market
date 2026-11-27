package dz.sntp.market.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dz.sntp.market.R
import dz.sntp.market.ui.theme.SntpColors
import dz.sntp.market.util.MarketTime
import java.time.LocalDate
import java.time.YearMonth

/**
 * Month grid so a trader can look back and see "this is how often I wasted a
 * trip". Data comes from state_history; empty days stay blank rather than being
 * invented.
 */
@Composable
fun HistorySection(history: Map<String, String>, fromCache: Boolean) {
    val today = MarketTime.today()
    val currentMonth = YearMonth.from(today)

    // The repository fetches 120 days, and the grid drew only the current month —
    // three quarters of the archive was downloaded, cached, and unreachable. On
    // the 2nd of a month the whole "history" tab was two squares over a real
    // record. The oldest month the server still answers for is the floor.
    val oldestMonth = remember { YearMonth.from(today.minusDays(120)) }
    var shownMonth by remember { mutableIntStateOf(0) }
    val month = currentMonth.minusMonths(shownMonth.toLong())
    val canGoOlder = month > oldestMonth
    val canGoNewer = shownMonth > 0
    val olderLabel = stringResource(R.string.history_prev_month)
    val newerLabel = stringResource(R.string.history_next_month)

    Text(
        text = stringResource(R.string.history_title),
        style = MaterialTheme.typography.titleLarge,
        color = SntpColors.TextPrimary,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.history_legend),
        style = MaterialTheme.typography.labelSmall,
        color = SntpColors.TextSecondary,
    )
    Spacer(Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = MarketTime.monthLabel(month, displayLocale()),
            style = MaterialTheme.typography.titleLarge,
            color = SntpColors.DeepGreen,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
        )
        // Only drawn when there is somewhere to go, so a market with one month of
        // history does not show two dead arrows.
        //
        // The glyph is chosen by layout direction, not hardcoded: in an RTL layout
        // "older" is to the right, and a fixed "‹" pointed the Arabic user at the
        // wrong month. The description carries the meaning for a screen reader,
        // because a bare chevron has none.
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        if (canGoOlder) {
            TextButton(onClick = { shownMonth++ }) {
                Text(
                    text = if (rtl) "\u203a" else "\u2039",
                    style = MaterialTheme.typography.titleLarge,
                    color = SntpColors.DeepGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics {
                        contentDescription = olderLabel
                    },
                )
            }
        }
        if (canGoNewer) {
            TextButton(onClick = { shownMonth-- }) {
                Text(
                    text = if (rtl) "\u2039" else "\u203a",
                    style = MaterialTheme.typography.titleLarge,
                    color = SntpColors.DeepGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics {
                        contentDescription = newerLabel
                    },
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))

    val firstDay = month.atDay(1)
    val leading = firstDay.dayOfWeek.value - 1        // Monday-first grid
    val days = month.lengthOfMonth()
    val cells = leading + days
    val rows = (cells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(7) { col ->
                    val index = row * 7 + col
                    val dayNum = index - leading + 1
                    Box(Modifier.weight(1f)) {
                        if (dayNum in 1..days) {
                            DayCell(
                                date = month.atDay(dayNum),
                                status = history[month.atDay(dayNum).toString()],
                                isToday = month.atDay(dayNum) == today,
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    // The grid draws one month, so the message must answer for that month. It used
    // to test the whole 120-day map: with rows from last month and none this month,
    // the grid was blank and *neither* message appeared — a month of empty squares
    // with nothing saying why.
    val monthKey = month.toString()          // "2026-11"
    val monthHasRows = history.keys.any { it.startsWith(monthKey) }
    if (!monthHasRows) {
        Text(
            // "No record yet" is a claim about the *market*, and it is only true for
            // the month we are still in: the archive starts now. Any earlier month
            // came back empty because nothing was recorded then, which is a
            // different sentence. Offline is a third thing again — the record
            // exists and could not be fetched.
            text = stringResource(
                when {
                    fromCache -> R.string.history_offline
                    month == currentMonth -> R.string.history_empty
                    else -> R.string.history_month_empty
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = SntpColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    } else if (fromCache) {
        // The grid is real but old: it is the last copy this phone managed to read.
        Text(
            text = stringResource(R.string.history_cached),
            style = MaterialTheme.typography.labelSmall,
            color = SntpColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DayCell(date: LocalDate, status: String?, isToday: Boolean) {
    val bg = when (status) {
        "open" -> SntpColors.OpenBg
        "closed" -> SntpColors.ClosedBg
        "unknown" -> SntpColors.UnknownBg
        else -> SntpColors.SandDim.copy(alpha = 0.45f)
    }
    val fg = when (status) {
        "open" -> SntpColors.Open
        "closed" -> SntpColors.Closed
        "unknown" -> SntpColors.Unknown
        else -> SntpColors.TextSecondary
    }
    // The grid is the only screen in the app made of nothing but bare numbers, so a
    // screen reader walked through it announcing "14", "15", "16" with no month and
    // no state — the calendar conveyed nothing at all. Each cell now carries the
    // date and what happened on it; an empty cell says it has no record, which is a
    // fact about the market and not the same as "closed".
    val stateLabel = when (status) {
        "open" -> stringResource(R.string.status_open)
        "closed" -> stringResource(R.string.status_closed)
        "unknown" -> stringResource(R.string.status_unknown)
        else -> stringResource(R.string.history_cell_no_record)
    }
    val label = stringResource(
        R.string.history_cell_label,
        MarketTime.dateLabel(date, displayLocale()),
        stateLabel,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .semantics(mergeDescendants = true) { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            fontSize = 13.sp,
            color = fg,
            fontWeight = if (isToday) FontWeight.Black else FontWeight.Medium,
        )
    }
}


