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
import dz.sntp.market.data.model.MarketStatus
import dz.sntp.market.data.model.Shop
import dz.sntp.market.data.model.Source
import dz.sntp.market.data.model.Sponsor
import dz.sntp.market.data.model.Tier
import dz.sntp.market.data.model.VoteMessage
import dz.sntp.market.data.model.VoteOutcome
import dz.sntp.market.data.repo.PendingVotes
import dz.sntp.market.notify.Notifier
import dz.sntp.market.notify.Scheduler
import dz.sntp.market.util.MarketTime
import kotlinx.coroutines.async
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

    private var lastRefreshAt = 0L

    /**
     * Called when the screen comes back to the foreground. The market can open and
     * shut inside one morning, so a state older than a few minutes is not worth
     * showing as current; the screen would otherwise lie from lunchtime onward.
     */
    fun refreshIfStale() {
        val age = System.currentTimeMillis() - lastRefreshAt
        if (age > RESUME_REFRESH_MS) refresh()
    }

    fun refresh(initial: Boolean = false) {
        lastRefreshAt = System.currentTimeMillis()
        viewModelScope.launch {
            if (initial) _ui.update { it.copy(loading = true) }

            // Six independent calls were run one after another. Each has an 18-second
            // call timeout, and inside the market the signal is barely alive, so the
            // spinner could sit for a minute and a half while the trader stood at the
            // gate. They do not depend on each other: run them together.
            val state = async { repo.loadState() }
            val week = async { repo.loadWeek() }
            val shops = async { repo.loadShops() }
            val sponsors = async { repo.loadSponsors() }
            val history = async { repo.loadHistory() }
            // Ask the server for the score it actually computed; the cached value
            // is only a fallback for the offline case.
            val rep = async { votes.syncReputation() ?: prefs.reputation() }

            val stateV = runCatching { state.await() }.getOrElse { _ui.value.state }
            val weekV = runCatching { week.await() }.getOrElse { _ui.value.week }
            val shopsV = runCatching { shops.await() }.getOrElse { _ui.value.shops }
            val sponsorsV = runCatching { sponsors.await() }.getOrElse { _ui.value.sponsors }
            val historyV = runCatching { history.await() }.getOrElse { _ui.value.history }
            val repV = runCatching { rep.await() }.getOrElse { _ui.value.reputation }

            // The spec asks for a notification when the state changes, not only at
            // dawn. Compared against the last status this device saw, so a change
            // the user already saw does not push again, and only for real server
            // truth: a seed guess flipping to UNKNOWN is not news.
            notifyStateChange(stateV)

            _ui.update {
                it.copy(
                    // A thrown call must never leave the app on the spinner forever;
                    // the fallbacks above keep the last good values on screen.
                    loading = false,
                    state = stateV,
                    week = weekV,
                    shops = shopsV,
                    sponsors = sponsorsV,
                    history = historyV,
                    pendingVotes = PendingVotes.count(app),
                    configured = repo.isConfigured,
                    reputation = repV,
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
            // A throw here (a location SecurityException, an unexpected I/O fault)
            // used to leave voting = true for the rest of the session, which
            // disables both buttons with no way back short of restarting the app.
            val outcome = runCatching { votes.vote(status, opensAt, closesAt, reason) }
                .getOrElse { VoteOutcome(false, false, VoteMessage.FAILED) }
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

    /**
     * The switch reflects whether a notification can actually arrive, not whether
     * a scheduler is registered. A refusal on Android 13+ must turn it off: the
     * worker would keep running and the system would keep discarding its output.
     */
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

    /** True while the app has never shown the system permission dialog. */
    suspend fun permissionNeverAsked(): Boolean = !prefs.notifyAsked()

    suspend fun markPermissionAsked() = prefs.setNotifyAsked()

    fun clearToast() = _ui.update { it.copy(toast = null) }

    /** Lets the screen report a refusal the vote path never got to see. */
    fun showToast(message: VoteMessage) = _ui.update { it.copy(toast = message) }

    /**
     * Pushes only on a real change of a confirmed state, and only once per change.
     * A user who went to sleep with "open" and wakes to "closed" should hear it
     * during the day, not wait for tomorrow's morning notification.
     */
    private suspend fun notifyStateChange(next: MarketState) {
        if (next.source == Source.SEED) return      // seed is a guess, never news
        if (next.status == MarketStatus.UNKNOWN) return

        val key = when (next.status) {
            MarketStatus.OPEN -> "open"
            MarketStatus.CLOSED -> "closed"
            MarketStatus.UNKNOWN -> "unknown"
        }

        // First sight on this device: record it and stay quiet. Pushing here would
        // greet a brand-new user with a "changed" alert for a market they have
        // never seen before.
        val lastSeen = prefs.lastSeenStatus()
        if (lastSeen == null) {
            prefs.setLastSeenStatus(key)
            return
        }
        if (lastSeen == key) return

        // What the user was last told, which the morning worker also writes: a
        // dawn push already said "closed", so a later refresh finding "closed"
        // must not push a second time.
        val lastTold = prefs.lastNotified().second
        if (lastTold != key && prefs.notifyEnabled.first() && Notifier.canNotify(app)) {
            if (Notifier.showChange(app, next)) {
                prefs.setLastNotified(MarketTime.isoDay(), key)
            }
        }
        prefs.setLastSeenStatus(key)
    }

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

    private companion object {
        /** Below this, a resume refresh would only add requests without new truth. */
        const val RESUME_REFRESH_MS = 5 * 60 * 1000L
    }
}

@Composable
fun rememberMainViewModel(): MainViewModel =
    androidx.lifecycle.viewmodel.compose.viewModel()
