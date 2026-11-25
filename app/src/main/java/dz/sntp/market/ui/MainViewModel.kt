package dz.sntp.market.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dz.sntp.market.BuildConfig
import dz.sntp.market.SntpApp
import dz.sntp.market.data.billing.BillingManager
import dz.sntp.market.data.model.DaySchedule
import dz.sntp.market.data.model.Freshness
import dz.sntp.market.data.model.MarketState
import dz.sntp.market.data.model.Shop
import dz.sntp.market.data.model.Sponsor
import dz.sntp.market.data.model.Tier
import dz.sntp.market.data.model.VoteMessage
import dz.sntp.market.data.model.VoteOutcome
import dz.sntp.market.data.repo.PendingVotes
import dz.sntp.market.notify.Scheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val loading: Boolean = true,
    val state: MarketState = MarketState.unknown(Freshness.CACHED),
    val week: List<DaySchedule> = emptyList(),
    val shops: List<Shop> = emptyList(),
    val sponsors: List<Sponsor> = emptyList(),
    val history: Map<String, String> = emptyMap(),
    val pendingVotes: Int = 0,
    val voting: Boolean = false,
    val toast: VoteMessage? = null,
    val configured: Boolean = false,
    val reputation: Int = 100,
    val recoveryCode: String = "",
    val notifyEnabled: Boolean = true,
    val notifyHour: Int = 5,
    val themeMode: Int = 0,
    val tier: Tier = Tier.FREE,
    val isPro: Boolean = false,
    val billingReady: Boolean = false,
    val prices: Map<String, String> = emptyMap(),
    val buyError: Boolean = false,
    val showUpgrade: Boolean = false,
    val unlockedShops: Set<String> = emptySet(),
    val tab: Int = 0,
    val versionName: String = BuildConfig.VERSION_NAME,
)

class MainViewModel : ViewModel() {

    private val app get() = SntpApp.instance
    private val repo get() = app.marketRepository
    private val votes get() = app.voteRepository
    private val prefs get() = app.prefs
    private val billing = BillingManager(app)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        billing.onPurchased = { sku ->
            viewModelScope.launch {
                val e = app.entitlements.grantFromPlay(sku)
                _ui.update {
                    it.copy(tier = e.tier, isPro = e.isPro, showUpgrade = false)
                }
            }
        }
        billing.connect()
        viewModelScope.launch {
            billing.ready.collect { ready ->
                _ui.update { it.copy(billingReady = ready) }
            }
        }
        viewModelScope.launch {
            billing.prices.collect { prices ->
                _ui.update { it.copy(prices = prices) }
            }
        }

        viewModelScope.launch {
            val stored = app.entitlements.current()
            _ui.update {
                it.copy(
                    recoveryCode = prefs.recoveryCode(),
                    notifyEnabled = prefs.notifyEnabled.first(),
                    notifyHour = prefs.notifyHour.first(),
                    themeMode = prefs.themeMode(),
                    tier = stored.tier,
                    isPro = stored.isPro,
                )
            }
            // The server may know better (promo grant made on another install).
            runCatching { app.entitlements.syncFromServer() }.getOrNull()?.let { e ->
                _ui.update { it.copy(tier = e.tier, isPro = e.isPro) }
            }
        }
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            if (initial) _ui.update { it.copy(loading = true) }

            val state = repo.loadState()
            val week = repo.loadWeek()
            val shops = repo.loadShops()
            val sponsors = repo.loadSponsors()
            val history = repo.loadHistory()
            val rep = prefs.reputation()

            _ui.update {
                it.copy(
                    loading = false,
                    state = state,
                    week = week,
                    shops = shops,
                    sponsors = sponsors,
                    history = history,
                    pendingVotes = PendingVotes.count(app),
                    configured = repo.isConfigured,
                    reputation = rep,
                )
            }
            // Anything queued while offline goes out on the next successful read.
            runCatching { votes.flushPending() }
        }
    }

    fun selectTab(index: Int) = _ui.update { it.copy(tab = index) }

    fun vote(status: String, opensAt: String?, closesAt: String?, reason: String?) {
        if (_ui.value.voting) return
        viewModelScope.launch {
            _ui.update { it.copy(voting = true) }
            val outcome: VoteOutcome = votes.vote(status, opensAt, closesAt, reason)
            _ui.update {
                it.copy(
                    voting = false,
                    toast = outcome.messageKind,
                    pendingVotes = PendingVotes.count(app),
                )
            }
            if (outcome.accepted && !outcome.queued) refresh()
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setNotifyEnabled(enabled)
            _ui.update { it.copy(notifyEnabled = enabled) }
            if (enabled) {
                Scheduler.scheduleMorning(app, _ui.value.notifyHour, 0)
            } else {
                Scheduler.cancel(app)
            }
        }
    }

    fun clearToast() = _ui.update { it.copy(toast = null) }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            prefs.setThemeMode(mode)
            _ui.update { it.copy(themeMode = mode) }
        }
    }

    fun setShowUpgrade(show: Boolean) = _ui.update { it.copy(showUpgrade = show, buyError = false) }

    fun buy(activity: android.app.Activity?, sku: String) {
        if (activity == null) return
        // A failed launch used to be swallowed: the user tapped "subscribe" and
        // nothing happened at all. Surface it in the sheet instead.
        val error = billing.buy(activity, sku)
        _ui.update { it.copy(buyError = error != null) }
    }

    /** One rewarded card unlocks one shop's number for this session. */
    fun unlockShop(id: String) = _ui.update { it.copy(unlockedShops = it.unlockedShops + id) }

    override fun onCleared() {
        billing.close()
        super.onCleared()
    }
}

@Composable
fun rememberMainViewModel(): MainViewModel =
    androidx.lifecycle.viewmodel.compose.viewModel()
