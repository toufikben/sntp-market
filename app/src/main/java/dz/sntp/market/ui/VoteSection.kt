package dz.sntp.market.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dz.sntp.market.R
import dz.sntp.market.data.model.DaySchedule
import dz.sntp.market.ui.theme.SntpColors
import dz.sntp.market.util.MarketTime

/**
 * Two large targets, no dialogs, no login. A trader standing in a crowd taps one
 * of these and walks on.
 */
@Composable
fun VoteSection(
    voting: Boolean,
    pending: Int,
    onVote: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.vote_question),
        style = MaterialTheme.typography.titleLarge,
        color = SntpColors.TextPrimary,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.vote_question_hint),
        style = MaterialTheme.typography.labelSmall,
        color = SntpColors.TextSecondary,
    )
    Spacer(Modifier.height(14.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VoteButton(
            text = stringResource(R.string.vote_open),
            background = SntpColors.Open,
            enabled = !voting,
            modifier = Modifier.weight(1f),
            onClick = { onVote("open") },
        )
        VoteButton(
            text = stringResource(R.string.vote_closed),
            background = SntpColors.Closed,
            enabled = !voting,
            modifier = Modifier.weight(1f),
            onClick = { onVote("closed") },
        )
    }

    if (pending > 0) {
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SntpColors.UnknownBg)
                .padding(10.dp),
        ) {
            Text(
                text = stringResource(R.string.vote_queued_count, pending),
                style = MaterialTheme.typography.labelSmall,
                color = SntpColors.Unknown,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VoteButton(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) background else background.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(76.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = SntpColors.OnAccent,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** The usual week, clearly labelled as usual and not as today. */
@Composable
fun WeekSection(week: List<DaySchedule>) {
    Text(
        text = stringResource(R.string.week_title),
        style = MaterialTheme.typography.titleLarge,
        color = SntpColors.TextPrimary,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.week_note),
        style = MaterialTheme.typography.labelSmall,
        color = SntpColors.TextSecondary,
    )
    Spacer(Modifier.height(12.dp))

    val todayIso = MarketTime.isoDayNumber()
    week.forEach { day ->
        val isToday = day.day == todayIso
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isToday) SntpColors.SandDim else androidx.compose.ui.graphics.Color.Transparent)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dayName(day.day),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = SntpColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            val hours = when {
                day.open == null -> stringResource(R.string.closed_day)
                day.open != null && day.close != null -> "${day.open} — ${day.close}"
                else -> stringResource(R.string.unknown_day)
            }
            Text(
                text = hours,
                style = MaterialTheme.typography.bodyMedium,
                color = if (day.open == null) SntpColors.Closed else SntpColors.TextPrimary,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun dayName(iso: Int): String = when (iso) {
    1 -> stringResource(R.string.day_mon)
    2 -> stringResource(R.string.day_tue)
    3 -> stringResource(R.string.day_wed)
    4 -> stringResource(R.string.day_thu)
    5 -> stringResource(R.string.day_fri)
    6 -> stringResource(R.string.day_sat)
    else -> stringResource(R.string.day_sun)
}
