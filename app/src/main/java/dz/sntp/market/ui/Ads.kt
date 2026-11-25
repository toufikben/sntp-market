package dz.sntp.market.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.sntp.market.R
import dz.sntp.market.data.model.Shop
import dz.sntp.market.data.model.Sponsor
import dz.sntp.market.ui.theme.SntpColors

/**
 * A paid placement, or an empty one that says it is for sale. Direct local
 * sponsors are what this market can sell today; no ad network account, no
 * tracking SDK, and the slot still renders something honest when unsold.
 */
@Composable
fun SponsorSlot(sponsor: Sponsor?, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SntpColors.SandDim)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = stringResource(R.string.ad_label),
            style = MaterialTheme.typography.labelSmall,
            color = SntpColors.AmberDark,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = sponsor?.name?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.ad_placeholder_title),
            style = MaterialTheme.typography.bodyLarge,
            color = SntpColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        if (sponsor == null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.ad_placeholder_body),
                style = MaterialTheme.typography.labelSmall,
                color = SntpColors.TextSecondary,
            )
            return@Column
        }
        sponsor.phone?.takeIf { it.isNotBlank() }?.let { phone ->
            TextButton(onClick = { runCatching { uri.openUri("tel:$phone") } }) {
                Text(stringResource(R.string.ad_sponsor_contact), color = SntpColors.DeepGreen)
            }
        }
    }
}

/**
 * Stands where a rewarded ad would be. Watching it unlocks one shop's number
 * for this session; Pro skips it. Kept as a card, not a fake countdown.
 */
@Composable
fun RewardedDialog(
    shop: Shop,
    sponsor: Sponsor?,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SntpColors.Sand,
        title = {
            Text(
                text = stringResource(R.string.ad_rewarded_title),
                color = SntpColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.ad_rewarded_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SntpColors.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                SponsorSlot(sponsor = sponsor)
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(
                    text = stringResource(R.string.ad_continue),
                    color = SntpColors.DeepGreen,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = SntpColors.TextSecondary)
            }
        },
    )
}
