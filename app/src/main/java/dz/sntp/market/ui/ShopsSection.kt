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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dz.sntp.market.R
import dz.sntp.market.data.model.Shop
import dz.sntp.market.ui.theme.SntpColors

/**
 * The shop directory. Free users see names and categories; the telephone number
 * is the rewarded slot. That keeps one ad where the user actively wants
 * something instead of three ads where they do not.
 */
@Composable
fun ShopsSection(shops: List<Shop>, isPro: Boolean, onUnlock: (Shop) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, shops) {
        if (query.isBlank()) shops
        else shops.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.category?.contains(query, ignoreCase = true) == true
        }
    }

    Text(
        text = stringResource(R.string.shops_title),
        style = MaterialTheme.typography.titleLarge,
        color = SntpColors.TextPrimary,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(10.dp))

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text(stringResource(R.string.shops_search)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SntpColors.DeepGreen,
            unfocusedBorderColor = SntpColors.Divider,
            focusedLabelColor = SntpColors.DeepGreen,
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(12.dp))

    if (filtered.isEmpty()) {
        Text(
            text = stringResource(R.string.shops_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = SntpColors.TextSecondary,
        )
        return
    }

    Column {
        filtered.forEach { shop ->
            ShopRow(shop = shop, isPro = isPro, onUnlock = { onUnlock(shop) })
            HorizontalDivider(color = SntpColors.Divider.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun ShopRow(shop: Shop, isPro: Boolean, onUnlock: () -> Unit) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = shop.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SntpColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                val sub = listOfNotNull(
                    shop.category,
                    shop.shopNumber?.let { "#$it" },
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = SntpColors.TextSecondary,
                    )
                }
            }
            if (shop.isVerified) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SntpColors.OpenBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        stringResource(R.string.shop_verified),
                        style = MaterialTheme.typography.labelSmall,
                        color = SntpColors.Open,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!shop.hasPhone) {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SntpColors.TextSecondary,
                )
            } else if (isPro) {
                TextButton(onClick = {
                    runCatching { uri.openUri("tel:${shop.phone}") }
                }) { Text(stringResource(R.string.shop_call), color = SntpColors.DeepGreen) }

                shop.whatsapp?.let { wa ->
                    TextButton(onClick = {
                        val digits = wa.replace("+", "").replace(" ", "")
                        runCatching { uri.openUri("https://wa.me/$digits") }
                    }) { Text(stringResource(R.string.shop_whatsapp), color = SntpColors.DeepGreen) }
                }
            } else {
                TextButton(onClick = onUnlock) {
                    Text(
                        stringResource(R.string.shop_watch_ad),
                        style = MaterialTheme.typography.labelSmall,
                        color = SntpColors.AmberDark,
                    )
                }
            }
        }
    }
}
