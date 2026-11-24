package dz.sntp.market.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import dz.sntp.market.R
import dz.sntp.market.data.model.Freshness
import dz.sntp.market.data.model.MarketState
import dz.sntp.market.data.model.MarketStatus
import dz.sntp.market.data.model.Source
import dz.sntp.market.ui.theme.SntpColors
import dz.sntp.market.util.MarketTime

@Composable
fun MarketHeader() {
    Text(
        text = stringResource(R.string.market_name),
        style = MaterialTheme.typography.titleLarge,
        color = SntpColors.DeepGreen,
        fontWeight = FontWeight.Black,
    )
    Text(
        text = stringResource(R.string.market_area),
        style = MaterialTheme.typography.labelSmall,
        color = SntpColors.TextSecondary,
    )
}

/**
 * The single panel the whole product exists for. Colour, word and time, so it
 * reads in one glance at arm's length.
 */
@Composable
fun StatusCard(ui: UiState) {
    val s = ui.state
    val accent = when (s.status) {
        MarketStatus.OPEN -> SntpColors.Open
        MarketStatus.CLOSED -> SntpColors.Closed
        MarketStatus.UNKNOWN -> SntpColors.Unknown
    }
    val bg = when (s.status) {
        MarketStatus.OPEN -> SntpColors.OpenBg
        MarketStatus.CLOSED -> SntpColors.ClosedBg
        MarketStatus.UNKNOWN -> SntpColors.UnknownBg
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(bg)
            .padding(vertical = 32.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(13.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.height(14.dp))
            Text(
                text = statusText(s),
                style = MaterialTheme.typography.displayLarge,
                color = accent,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            primaryTimeLine(s)?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineMedium,
                    color = SntpColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun statusText(s: MarketState): String = when (s.status) {
    MarketStatus.OPEN -> stringResource(R.string.status_open)
    MarketStatus.CLOSED -> stringResource(R.string.status_closed)
    MarketStatus.UNKNOWN -> if (s.opensAtLocal == null && s.confidence <= 0.0) {
        stringResource(R.string.status_no_data)
    } else {
        stringResource(R.string.status_unknown)
    }
}

@Composable
private fun primaryTimeLine(s: MarketState): String? = when (s.status) {
    MarketStatus.OPEN -> s.closesAtLocal?.let { stringResource(R.string.closes_at, it) }
    MarketStatus.CLOSED -> s.opensAtLocal?.let { stringResource(R.string.opens_at, it) }
    MarketStatus.UNKNOWN -> null
}

@Composable
fun DetailRows(ui: UiState) {
    val s = ui.state

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = todayLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = SntpColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }

    reasonText(s)?.let { InfoRow(stringResource(R.string.reason_label), it) }

    InfoRow(
        label = stringResource(R.string.confidence_label),
        value = confidenceText(s),
    )

    if (s.reportersCount > 0) {
        InfoRow("", stringResource(R.string.confirmers_count, s.reportersCount))
    }

    InfoRow("", sourceText(s), dim = true)

    MarketTime.stamp(s.updatedAtEpochMs)?.let {
        InfoRow("", stringResource(R.string.updated_at, it), dim = true)
    }

    if (s.freshness == Freshness.STALE || s.freshness == Freshness.OFFLINE) {
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SntpColors.UnknownBg)
                .padding(12.dp)
        ) {
            Text(
                text = if (s.freshness == Freshness.OFFLINE) {
                    stringResource(R.string.offline_warning)
                } else {
                    stringResource(R.string.stale_warning)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = SntpColors.Unknown,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, dim: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = SntpColors.TextSecondary,
                modifier = Modifier.width(96.dp),
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (dim) SntpColors.TextSecondary else SntpColors.TextPrimary,
        )
    }
}

private fun reasonText(s: MarketState): String? =
    s.reasonAr?.takeIf { it.isNotBlank() } ?: s.reasonFr?.takeIf { it.isNotBlank() }

private fun confidenceText(s: MarketState): String = when {
    s.source == Source.ADMIN -> "100%"
    s.confidence <= 0.0 -> "—"
    else -> "${(s.confidence * 100).toInt()}%"
}

@Composable
private fun sourceText(s: MarketState): String = when (s.source) {
    Source.ADMIN -> stringResource(R.string.trust_admin)
    Source.TRADERS -> stringResource(R.string.trust_traders)
    Source.SEED -> stringResource(R.string.trust_seed)
}

@Composable
private fun todayLabel(): String {
    val d = MarketTime.today()
    val dayName = when (d.dayOfWeek.value) {
        1 -> stringResource(R.string.day_mon)
        2 -> stringResource(R.string.day_tue)
        3 -> stringResource(R.string.day_wed)
        4 -> stringResource(R.string.day_thu)
        5 -> stringResource(R.string.day_fri)
        6 -> stringResource(R.string.day_sat)
        else -> stringResource(R.string.day_sun)
    }
    return "$dayName ${d.dayOfMonth} ${d.year}"
}
