package dz.sntp.market.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import dz.sntp.market.data.model.Tier
import dz.sntp.market.ui.theme.SntpColors

@Composable
fun SettingsSection(
    notifyEnabled: Boolean,
    notifyHour: Int,
    themeMode: Int,
    recoveryCode: String,
    reputation: Int,
    versionName: String,
    tier: Tier,
    onNotifyToggle: (Boolean) -> Unit,
    onThemeMode: (Int) -> Unit,
    onOpenBattery: () -> Unit,
    onUpgrade: () -> Unit,
) {
    Text(
        text = stringResource(R.string.settings_title),
        style = MaterialTheme.typography.titleLarge,
        color = SntpColors.TextPrimary,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(16.dp))

    SettingRow(
        title = stringResource(R.string.settings_notify),
        subtitle = stringResource(
            R.string.settings_notify_desc
        ) + " · " + "%02d:%02d".format(notifyHour, 0),
    ) {
        Switch(
            checked = notifyEnabled,
            onCheckedChange = onNotifyToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SntpColors.TextOnDark,
                checkedTrackColor = SntpColors.DeepGreen,
                uncheckedTrackColor = SntpColors.Divider,
            ),
        )
    }

    HorizontalDivider(color = SntpColors.Divider, modifier = Modifier.padding(vertical = 4.dp))

    Column(Modifier.padding(vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.bodyLarge,
            color = SntpColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.settings_theme_desc),
            style = MaterialTheme.typography.labelSmall,
            color = SntpColors.TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        ThemeSelector(mode = themeMode, onSelect = onThemeMode)
    }

    HorizontalDivider(color = SntpColors.Divider, modifier = Modifier.padding(vertical = 4.dp))

    SettingRow(
        title = stringResource(R.string.settings_battery),
        subtitle = stringResource(R.string.settings_battery_desc),
    ) {
        TextButton(onClick = onOpenBattery) {
            Text(stringResource(R.string.settings_open), color = SntpColors.DeepGreen, fontWeight = FontWeight.Bold)
        }
    }

    HorizontalDivider(color = SntpColors.Divider, modifier = Modifier.padding(vertical = 4.dp))

    Column(Modifier.padding(vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.settings_recovery),
            style = MaterialTheme.typography.bodyLarge,
            color = SntpColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.settings_recovery_desc),
            style = MaterialTheme.typography.labelSmall,
            color = SntpColors.TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(SntpColors.SandDim)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = recoveryCode,
                style = MaterialTheme.typography.titleLarge,
                color = SntpColors.DeepGreen,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_reputation_value, isolate("$reputation / 180")),
            style = MaterialTheme.typography.labelSmall,
            color = SntpColors.TextSecondary,
        )
    }

    HorizontalDivider(color = SntpColors.Divider, modifier = Modifier.padding(vertical = 4.dp))

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when (tier) {
                Tier.FREE -> stringResource(R.string.settings_plan_free)
                Tier.PRO -> stringResource(R.string.settings_plan_pro)
                Tier.BOUTIQUE -> stringResource(R.string.settings_plan_boutique)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = SntpColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (tier == Tier.FREE) {
            TextButton(onClick = onUpgrade) {
                Text(
                    stringResource(R.string.settings_upgrade),
                    color = SntpColors.DeepGreen,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    HorizontalDivider(color = SntpColors.Divider, modifier = Modifier.padding(vertical = 4.dp))

    Spacer(Modifier.height(20.dp))
    Text(
        text = stringResource(R.string.settings_disclaimer),
        style = MaterialTheme.typography.labelSmall,
        color = SntpColors.TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.settings_version, isolate(versionName)),
        style = MaterialTheme.typography.labelSmall,
        color = SntpColors.TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Wraps a Latin run (a version number, a score) in Unicode isolates so the bidi
 * algorithm keeps it in reading order inside an Arabic sentence. Without this
 * "0.1.0-debug" renders as "debug-0.1.0" and "100 / 180" as "180 / 100".
 */
private fun isolate(value: String): String = "\u2066$value\u2069"

@Composable
private fun ThemeSelector(mode: Int, onSelect: (Int) -> Unit) {
    val options = listOf(
        0 to R.string.theme_system,
        1 to R.string.theme_light,
        2 to R.string.theme_dark,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, labelRes) ->
            val active = mode == value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) SntpColors.DeepGreen else SntpColors.SandDim)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) SntpColors.TextOnDark else SntpColors.TextPrimary,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    action: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = SntpColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SntpColors.TextSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        action()
    }
}
