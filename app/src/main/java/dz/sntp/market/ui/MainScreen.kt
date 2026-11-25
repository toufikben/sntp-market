package dz.sntp.market.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dz.sntp.market.R
import dz.sntp.market.data.model.Shop
import dz.sntp.market.data.model.VoteMessage
import dz.sntp.market.notify.Notifier
import dz.sntp.market.notify.Scheduler
import dz.sntp.market.ui.theme.SntpColors
import dz.sntp.market.ui.theme.SntpTheme
import kotlinx.coroutines.delay

private const val PRIVACY_URL = "https://toufikben.github.io/sntp-market/privacy.html"

@Composable
fun MainScreen(vm: MainViewModel = rememberMainViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // A vote needs a GPS fix. Nothing asked for the permission, so the first tap
    // on a vote button always ended in "we need your location" with no way to
    // give it. The tap is remembered and replayed once the user answers.
    var pendingVote by remember { mutableStateOf<String?>(null) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val status = pendingVote
        pendingVote = null
        if (granted && status != null) {
            vm.vote(status, ui.state.opensAtLocal, ui.state.closesAtLocal, null)
        }
    }

    fun voteWithPermission(status: String) {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fine || coarse) {
            vm.vote(status, ui.state.opensAtLocal, ui.state.closesAtLocal, null)
        } else {
            pendingVote = status
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    // From Android 13 the notification permission is not granted at install, and
    // nothing ever asked for it: the switch said "on", the worker ran every
    // morning, and the notification was silently dropped. Asking here is the
    // difference between the product existing and not.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // A refusal must turn the switch off instead of leaving it on over a
        // worker whose output the system discards.
        vm.setNotifications(granted)
    }

    fun setNotificationsWithPermission(enabled: Boolean) {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

        if (enabled && needsAsk) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.setNotifications(enabled)
        }
    }

    val dark = when (ui.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    // Channel + daily job must exist before the first morning.
    LaunchedEffect(Unit) {
        Notifier.ensureChannel(context)
        if (!ui.notifyEnabled) return@LaunchedEffect

        // Android 13+ grants no notification permission at install. The switch
        // defaults to on and the morning notification is the whole product, so ask
        // — but only once ever: Android answers a repeat request without showing
        // anything, which would look like a broken switch rather than a refusal.
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

        if (needsAsk && vm.permissionNeverAsked()) {
            vm.markPermissionAsked()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (needsAsk) {
            // Already asked before and still not granted: the user said no, or
            // revoked it in system settings. Leaving the switch on would promise a
            // notification that the system drops, so bring it in line with reality.
            vm.setNotifications(false)
        } else {
            Scheduler.scheduleMorning(context, ui.notifyHour, 0)
        }
    }

    SntpTheme(darkTheme = dark) {
        if (ui.loading) {
            LoadingScreen()
        } else {
            MainContent(ui = ui, vm = vm, onVote = ::voteWithPermission, onNotifyToggle = ::setNotificationsWithPermission)
        }
    }
}

@Composable
private fun MainContent(
    ui: UiState,
    vm: MainViewModel,
    onVote: (String) -> Unit,
    onNotifyToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    // Which shop number the user is unlocking right now (null = no dialog).
    var adShop by remember { mutableStateOf<Shop?>(null) }

    Box(Modifier.fillMaxSize().background(SntpColors.Sand)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Box(Modifier.weight(1f)) {
                when (ui.tab) {
                    0 -> TodayTab(ui, vm, onVote)
                    1 -> ScrollTab { WeekSection(ui.week) }
                    2 -> ScrollTab {
                        ShopsSection(
                            shops = ui.shops,
                            isPro = ui.isPro,
                            unlocked = ui.unlockedShops,
                            onUnlock = { adShop = it },
                        )
                        if (!ui.isPro) {
                            Spacer(Modifier.height(18.dp))
                            SponsorSlot(sponsor = ui.sponsors.firstOrNull { it.slot == 1 })
                        }
                    }
                    3 -> ScrollTab {
                        HistorySection(ui.history)
                        if (!ui.isPro) {
                            Spacer(Modifier.height(18.dp))
                            SponsorSlot(sponsor = ui.sponsors.firstOrNull { it.slot == 2 })
                        }
                    }
                    else -> ScrollTab {
                        SettingsSection(
                            notifyEnabled = ui.notifyEnabled,
                            notifyHour = ui.notifyHour,
                            themeMode = ui.themeMode,
                            reputation = ui.reputation,
                            versionName = ui.versionName,
                            tier = ui.tier,
                            onNotifyToggle = onNotifyToggle,
                            onThemeMode = vm::setThemeMode,
                            onOpenBattery = { openBatterySettings(context) },
                            onPrivacy = { openUrl(context, PRIVACY_URL) },
                            onUpgrade = { vm.setShowUpgrade(true) },
                        )
                    }
                }
            }
            BottomBar(selected = ui.tab, onSelect = vm::selectTab)
        }

        ui.toast?.let { msg ->
            ToastBanner(
                message = msg,
                onDismiss = vm::clearToast,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp, start = 20.dp, end = 20.dp),
            )
        }

        UpgradeSheet(
            visible = ui.showUpgrade,
            billingReady = ui.billingReady,
            buyError = ui.buyError,
            prices = ui.prices,
            tier = ui.tier,
            onDismiss = { vm.setShowUpgrade(false) },
            onBuy = { sku -> vm.buy(context as? android.app.Activity, sku) },
        )

        adShop?.let { shop ->
            RewardedDialog(
                shop = shop,
                sponsor = ui.sponsors.firstOrNull { it.slot == 3 },
                onContinue = {
                    vm.unlockShop(shop.id)
                    adShop = null
                },
                onDismiss = { adShop = null },
            )
        }
    }
}

@Composable
private fun TodayTab(ui: UiState, vm: MainViewModel, onVote: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        MarketHeader()
        Spacer(Modifier.height(18.dp))
        StatusCard(ui)
        Spacer(Modifier.height(16.dp))
        DetailRows(ui)
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.HorizontalDivider(color = SntpColors.Divider)
        Spacer(Modifier.height(18.dp))
        VoteSection(
            voting = ui.voting,
            pending = ui.pendingVotes,
            onVote = onVote,
        )
        Spacer(Modifier.height(20.dp))
        TextButton(
            onClick = { vm.refresh() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.refresh), color = SntpColors.DeepGreen, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = SntpColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ScrollTab(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        content()
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(SntpColors.Sand),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.CircularProgressIndicator(color = SntpColors.DeepGreen)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodyMedium,
                color = SntpColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(
        R.string.tab_today,
        R.string.tab_week,
        R.string.tab_shops,
        R.string.tab_history,
        R.string.tab_settings,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SntpColors.BrandBar)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        labels.forEachIndexed { index, labelRes ->
            val active = index == selected
            TextButton(onClick = { onSelect(index) }, modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) SntpColors.Amber else SntpColors.TextOnDark.copy(alpha = 0.65f),
                    fontWeight = if (active) FontWeight.Black else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ToastBanner(message: VoteMessage, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val textRes = when (message) {
        VoteMessage.SENT -> R.string.vote_sent
        VoteMessage.QUEUED -> R.string.vote_queued
        VoteMessage.NEED_LOCATION -> R.string.vote_need_location
        VoteMessage.TOO_FAR -> R.string.vote_too_far
        VoteMessage.ALREADY_VOTED -> R.string.vote_already
        VoteMessage.RATE_LIMITED -> R.string.vote_rate_limited
        VoteMessage.FAILED -> R.string.retry
    }
    LaunchedEffect(message) {
        delay(4000)
        onDismiss()
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SntpColors.DeepGreenDark)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodyMedium,
            color = SntpColors.TextOnDark,
        )
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openBatterySettings(context: android.content.Context) {
    val intents = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            add(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            })
        }
        add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        })
    }
    for (i in intents) {
        if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
