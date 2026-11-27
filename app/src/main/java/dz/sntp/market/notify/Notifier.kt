package dz.sntp.market.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dz.sntp.market.MainActivity
import dz.sntp.market.R
import dz.sntp.market.data.model.MarketState
import dz.sntp.market.data.model.Freshness
import dz.sntp.market.data.model.MarketStatus
import dz.sntp.market.data.model.Source

object Notifier {

    const val CHANNEL_DAILY = "sntp_daily"
    private const val NOTIF_ID = 4101
    private const val NOTIF_ID_CHANGE = 4102

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = mgr.getNotificationChannel(CHANNEL_DAILY)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_DAILY,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            enableVibration(true)
            setShowBadge(true)
        }
        mgr.createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean {
        // Two independent switches, and both must be on. The permission is the
        // Android 13+ one; `areNotificationsEnabled()` is the app-level toggle on
        // the same settings page (and covers pre-13 entirely). Checking only the
        // permission meant that a user who turned the app's notifications off in
        // Settings still passed this gate: `notify()` then did nothing and threw
        // nothing, so [post] reported success, [MorningWorker] spent the day's only
        // slot on a notification that never appeared, and turning them back on
        // later that day produced silence until tomorrow.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false

        // A third switch, and the one most likely to be off on the phones this app
        // targets: the channel itself. Android lets the user silence one channel
        // while the app stays enabled, and `notify()` on a blocked channel posts
        // nothing and reports no error — so the worker spent the day's only slot on
        // a notification nobody would see. Channel importance is also the only way
        // to notice that the user turned the channel down to "silent".
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return true
            val channel = mgr.getNotificationChannel(CHANNEL_DAILY) ?: return true
            if (channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    /**
     * One notification, once a day. Returns false when nothing was posted, so the
     * caller does not spend the day's only slot on a notification the system
     * dropped (permission revoked, channel blocked).
     */
    fun showMorning(context: Context, state: MarketState): Boolean {
        if (!canNotify(context)) return false
        ensureChannel(context)
        return post(context, NOTIF_ID, titleOf(context, state), bodyOf(context, state), state)
    }

    /**
     * The other half of the product's promise: tell the user when the market opens
     * or shuts inside the day, not only at dawn. Without this, a trader who checked
     * at 07:00 and found it shut learned nothing when it opened at 09:00.
     *
     * A separate id so it stacks next to the morning notification instead of
     * replacing it.
     */
    fun showChange(context: Context, state: MarketState): Boolean {
        if (!canNotify(context)) return false
        ensureChannel(context)
        val title = context.getString(R.string.notif_changed_title, titleOf(context, state))
        return post(context, NOTIF_ID_CHANGE, title, bodyOf(context, state), state)
    }

    private fun titleOf(context: Context, state: MarketState): String = when (state.status) {
        MarketStatus.OPEN -> context.getString(R.string.status_open)
        MarketStatus.CLOSED -> context.getString(R.string.status_closed)
        MarketStatus.UNKNOWN -> context.getString(R.string.status_unknown)
    }

    private fun bodyOf(context: Context, state: MarketState): String = when (state.status) {
        MarketStatus.OPEN -> state.closesAtLocal
            ?.let { context.getString(R.string.notif_closes_at, it) }
            ?: ""
        MarketStatus.CLOSED -> state.opensAtLocal
            ?.let { context.getString(R.string.notif_opens_at, it) }
            ?: ""
        MarketStatus.UNKNOWN -> context.getString(R.string.notif_no_confirmation)
    }

    private fun post(
        context: Context,
        id: Int,
        title: String,
        body: String,
        state: MarketState,
    ): Boolean {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(R.drawable.ic_stat_market)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)

        // The morning worker runs without a network constraint on purpose (the
        // inside of the market has no signal), so the state it posts is often read
        // from disk rather than fetched. Labelling only the seed left the far more
        // common case unmarked: a notification reading "السوق مفتوح" built from
        // yesterday's row, with nothing saying so. A notification is the one place
        // the user cannot see the stale banner, so it has to carry the caveat.
        val caveat = when {
            state.source == Source.SEED -> R.string.notif_seed_subtext
            state.freshness == Freshness.LIVE -> 0
            else -> R.string.notif_stale_subtext
        }
        if (caveat != 0) builder.setSubText(context.getString(caveat))

        return runCatching {
            NotificationManagerCompat.from(context).notify(id, builder.build())
            true
        }.getOrDefault(false)
    }
}
