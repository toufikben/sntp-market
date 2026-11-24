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
     * One notification, once a day, and only when the state differs from what we
     * last told the user. Repeating "open" every morning trains people to swipe
     * it away, which destroys the only channel we have.
     */
    fun showMorning(context: Context, state: MarketState) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val title: String
        val body: String
        when (state.status) {
            MarketStatus.OPEN -> {
                title = context.getString(R.string.status_open)
                body = state.closesAtLocal
                    ?.let { context.getString(R.string.notif_closes_at, it) }
                    ?: context.getString(R.string.trust_traders)
            }
            MarketStatus.CLOSED -> {
                title = context.getString(R.string.status_closed)
                body = state.opensAtLocal
                    ?.let { context.getString(R.string.notif_opens_at, it) }
                    ?: context.getString(R.string.settings_disclaimer)
            }
            MarketStatus.UNKNOWN -> {
                title = context.getString(R.string.status_unknown)
                body = context.getString(R.string.notif_no_confirmation)
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)

        if (state.source == Source.SEED) {
            builder.setSubText(context.getString(R.string.notif_seed_subtext))
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
        }
    }
}
