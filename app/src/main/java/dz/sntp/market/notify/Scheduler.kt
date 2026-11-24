package dz.sntp.market.notify

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import dz.sntp.market.util.MarketTime

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
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(MorningWorker.NAME)
    }
}
