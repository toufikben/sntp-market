package dz.sntp.market.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dz.sntp.market.SntpApp
import dz.sntp.market.data.model.MarketStatus
import dz.sntp.market.data.repo.PendingVotes
import dz.sntp.market.util.MarketTime
import kotlinx.coroutines.flow.first

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

        // Cancel is asynchronous and a worker already enqueued can still run; the
        // switch the user turned off has to be honoured here, not only at the
        // scheduler.
        if (!prefs.notifyEnabled.first()) return Result.success()

        val today = MarketTime.isoDay()
        val (lastDay, _) = prefs.lastNotified()
        if (lastDay == today) return Result.success()   // already told them today

        val state = app.marketRepository.loadState()

        // Since the status no longer gates the notification, it is only kept for
        // the record; the day is the thing that makes it once-a-day.
        //
        // Once a day, always. This used to require a status change, which meant a
        // market that stayed open two days running produced no notification at
        // all: the trader went to sleep knowing yesterday, woke up knowing
        // nothing, and the app's only job did not happen.
        val shown = Notifier.showMorning(applicationContext, state)
        // Only a notification that really appeared spends the day. Marking it
        // after a silent drop would leave the user with nothing until tomorrow
        // even though they granted the permission in the meantime.
        if (shown) {
            val statusKey = when (state.status) {
                MarketStatus.OPEN -> "open"
                MarketStatus.CLOSED -> "closed"
                MarketStatus.UNKNOWN -> "unknown"
            }
            prefs.setLastNotified(today, statusKey)
            // The user has now been told, so a refresh later today must not push a
            // "changed" alert for the very status the morning notification named.
            prefs.setLastToldStatus(statusKey)
            prefs.setLastSeenStatus(statusKey)
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
