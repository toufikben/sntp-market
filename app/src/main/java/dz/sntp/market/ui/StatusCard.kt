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
import androidx.compose.ui.platform.LocalConfiguration
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
    // Nothing is confirmed, so the honest line is the usual window, labelled as usual.
    // The pair is an LTR run inside an Arabic sentence: without the isolate the dash
    // and the two times land in the wrong order, on the one line the product is read for.
    MarketStatus.UNKNOWN -> {
        val open = s.opensAtLocal
        val close = s.closesAtLocal
        if (open != null && close != null) {
            stringResource(R.string.usual_hours, "\u2066$open\u2069", "\u2066$close\u2069")
        } else if (open != null) {
            stringResource(R.string.opens_at, "\u2066$open\u2069")
        } else {
            null
        }
    }
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

@Composable
private fun reasonText(s: MarketState): String? = when (displayLocale().language) {
    "fr" -> s.reasonFr?.takeIf { it.isNotBlank() } ?: s.reasonAr?.takeIf { it.isNotBlank() }
    else -> s.reasonAr?.takeIf { it.isNotBlank() } ?: s.reasonFr?.takeIf { it.isNotBlank() }
}

@Composable
private fun confidenceText(s: MarketState): String = when {
    s.source == Source.ADMIN -> "100%"
    s.confidence <= 0.0 -> "—"
    // A bare "85%" flips to "%85" next to Arabic text; the isolate keeps the
    // number and its sign in reading order.
    else -> "\u2066${(s.confidence * 100).toInt()}%\u2069"
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
    return MarketTime.dateLabel(d, displayLocale())
}

/**
 * The locale the visible strings are actually in, not the phone's locale.
 *
 * This app ships two string sets: `values/` (Arabic, the default) and
 * `values-fr/`. A phone set to English therefore shows Arabic while its locale
 * is "en" — and formatting the date with "en" printed an English date inside an
 * Arabic screen. Anything that is not French resolves to Arabic, which is
 * exactly how the resources resolve.
 */
@Composable
internal fun displayLocale(): java.util.Locale {
    val device = androidx.core.os.ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
    return if (device?.language == "fr") java.util.Locale.FRENCH else ARABIC
}

internal val ARABIC: java.util.Locale = java.util.Locale.forLanguageTag("ar-DZ")


