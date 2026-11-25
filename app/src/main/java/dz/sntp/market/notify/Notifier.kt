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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
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
            ?: context.getString(R.string.trust_traders)
        MarketStatus.CLOSED -> state.opensAtLocal
            ?.let { context.getString(R.string.notif_opens_at, it) }
            ?: context.getString(R.string.settings_disclaimer)
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

        if (state.source == Source.SEED) {
            builder.setSubText(context.getString(R.string.notif_seed_subtext))
        }

        return runCatching {
            NotificationManagerCompat.from(context).notify(id, builder.build())
            true
        }.getOrDefault(false)
    }
}
