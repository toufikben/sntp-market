package dz.sntp.market.notify

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import dz.sntp.market.util.MarketTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Schedules the daily check. WorkManager batches, so we ask for a window rather
 * than an exact minute: the notification must be there before the trader leaves
 * home, not at 05:00:00 precisely.
 *
 * No network constraint on purpose. The inside of the market has no usable
 * signal, and a worker that refuses to run without a connection is a worker that
 * never tells the user anything. [MorningWorker] falls back to the cached state
 * and labels it as such.
 */
object Scheduler {

    /**
     * Makes sure a daily job exists, without disturbing one that already does.
     *
     * Used on launch, where the job is almost always already enqueued: re-enqueueing
     * there would cancel a run that is due right now, and a user who opens the app
     * when he wakes at 05:05 would lose that morning's notification and get it
     * tomorrow instead. [scheduleMorning] is for the cases where the anchor must
     * actually move.
     */
    suspend fun ensureMorning(context: Context, hour: Int, minute: Int) {
        val existing = withContext(Dispatchers.IO) {
            runCatching {
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(MorningWorker.NAME)
                    .get()
            }.getOrDefault(emptyList())
        }
        val alive = existing.any {
            !it.state.isFinished
        }
        if (!alive) scheduleMorning(context, hour, minute)
    }

    /**
     * (Re)anchors the daily check to [hour]:[minute].
     *
     * CANCEL_AND_REENQUEUE rather than UPDATE, for a reason that costs the user the
     * product: WorkManager computes each periodic run from the moment the previous
     * one *actually started*. A run delayed by Doze, by the phone being off, or by
     * the battery killer on exactly the Xiaomi and Oppo phones this targets shifts
     * every run after it, permanently — a 05:00 notification becomes a 07:30 one
     * after a few weeks, and UPDATE preserves the original anchor so re-enqueueing
     * never pulls it back. Re-enqueueing resets the anchor.
     */
    fun scheduleMorning(context: Context, hour: Int, minute: Int) {
        val zone = TimeZone.getTimeZone(MarketTime.ZONE)
        val now = Calendar.getInstance(zone)
        val target = Calendar.getInstance(zone).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        val delay = target.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<MorningWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MorningWorker.NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request,
        )
    }

    /**
     * True while today's notification hour has not passed yet.
     *
     * The guard for re-anchoring on resume: re-enqueueing cancels the existing work,
     * which would kill a run that is due right now or already posting. Before the
     * hour there is nothing in flight to lose.
     */
    fun isBeforeTarget(hour: Int, minute: Int): Boolean {
        val zone = TimeZone.getTimeZone(MarketTime.ZONE)
        val now = Calendar.getInstance(zone)
        val target = Calendar.getInstance(zone).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return now.timeInMillis < target.timeInMillis
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MorningWorker.NAME)
    }
}
