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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
fun HistorySection(history: Map<String, String>) {
    val today = MarketTime.today()
    val month = YearMonth.from(today)

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

    Text(
        text = MarketTime.monthLabel(month, java.util.Locale.getDefault()),
        style = MaterialTheme.typography.titleLarge,
        color = SntpColors.DeepGreen,
        fontWeight = FontWeight.Black,
    )
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
    if (history.isEmpty()) {
        Text(
            text = stringResource(R.string.history_empty),
            style = MaterialTheme.typography.bodyMedium,
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(bg),
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

