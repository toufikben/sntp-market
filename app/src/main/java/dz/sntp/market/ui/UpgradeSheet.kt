package dz.sntp.market.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.sntp.market.R
import dz.sntp.market.data.billing.BillingManager
import dz.sntp.market.data.model.Tier
import dz.sntp.market.ui.theme.SntpColors

/**
 * Three plans, prices shown even before Play answers so the offer is readable
 * offline. The buy buttons stay disabled until Play returns real product
 * details — a button that can only fail is worse than a disabled one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeSheet(
    visible: Boolean,
    billingReady: Boolean,
    tier: Tier,
    onDismiss: () -> Unit,
    onBuy: (String) -> Unit,
) {
    if (!visible) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SntpColors.Sand,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.upgrade_title),
                style = MaterialTheme.typography.titleLarge,
                color = SntpColors.TextPrimary,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.upgrade_promo),
                style = MaterialTheme.typography.bodyMedium,
                color = SntpColors.DeepGreen,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))

            PlanRow(
                title = stringResource(R.string.plan_pro_monthly),
                price = stringResource(R.string.plan_pro_monthly_price),
                features = stringResource(R.string.plan_pro_features),
                active = tier == Tier.PRO,
                enabled = billingReady,
                onClick = { onBuy(BillingManager.SKU_PRO_MONTHLY) },
            )
            HorizontalDivider(color = SntpColors.Divider)
            PlanRow(
                title = stringResource(R.string.plan_pro_yearly),
                price = stringResource(R.string.plan_pro_yearly_price),
                features = stringResource(R.string.plan_pro_features),
                active = tier == Tier.PRO,
                enabled = billingReady,
                onClick = { onBuy(BillingManager.SKU_PRO_YEARLY) },
            )
            HorizontalDivider(color = SntpColors.Divider)
            PlanRow(
                title = stringResource(R.string.plan_boutique_monthly),
                price = stringResource(R.string.plan_boutique_price),
                features = stringResource(R.string.plan_boutique_features),
                active = tier == Tier.BOUTIQUE,
                enabled = billingReady,
                onClick = { onBuy(BillingManager.SKU_BOUTIQUE_MONTHLY) },
            )

            Spacer(Modifier.height(14.dp))
            Text(
                text = if (billingReady) stringResource(R.string.upgrade_free_note)
                else stringResource(R.string.upgrade_unavailable),
                style = MaterialTheme.typography.labelSmall,
                color = SntpColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun PlanRow(
    title: String,
    price: String,
    features: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
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
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = price,
                style = MaterialTheme.typography.labelSmall,
                color = SntpColors.DeepGreen,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = features,
                style = MaterialTheme.typography.labelSmall,
                color = SntpColors.TextSecondary,
            )
        }
        if (active) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SntpColors.OpenBg)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = stringResource(R.string.upgrade_current),
                    style = MaterialTheme.typography.labelSmall,
                    color = SntpColors.Open,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            TextButton(onClick = onClick, enabled = enabled) {
                Text(
                    text = stringResource(R.string.upgrade_buy),
                    color = if (enabled) SntpColors.DeepGreen else SntpColors.TextSecondary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
