package dz.sntp.market.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dz.sntp.market.SntpApp
import dz.sntp.market.data.model.MarketStatus
import dz.sntp.market.data.repo.PendingVotes
import dz.sntp.market.util.MarketTime

/**
 * The reliable half of the morning notification. FCM from the server is the fast
 * path, but on Xiaomi/Oppo/Huawei in Algeria it is frequently killed, so this
 * worker re-checks and fires if the server did not.
 */
class MorningWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = SntpApp.instance
        val prefs = app.prefs

        val today = MarketTime.isoDay()
        val (lastDay, lastStatus) = prefs.lastNotified()
        if (lastDay == today) return Result.success()   // already told them today

        val state = app.marketRepository.loadState()

        // Only speak when there is something to say.
        val statusKey = when (state.status) {
            MarketStatus.OPEN -> "open"
            MarketStatus.CLOSED -> "closed"
            MarketStatus.UNKNOWN -> "unknown"
        }

        val shouldNotify = lastStatus != statusKey || lastDay == null
        if (shouldNotify) {
            Notifier.showMorning(applicationContext, state)
            prefs.setLastNotified(today, statusKey)
        }

        // Opportunistic: flush votes recorded inside the market with no signal.
        if (PendingVotes.count(applicationContext) > 0) {
            runCatching { app.voteRepository.flushPending() }
        }

        return Result.success()
    }

    companion object {
        const val NAME = "sntp-morning"
    }
}
