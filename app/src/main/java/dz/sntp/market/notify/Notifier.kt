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
            "إشعار الصباح",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "حالة سوق سانتيبي قبل الفتح"
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
        when (state.state) {
            MarketState.OPEN -> {
                title = context.getString(R.string.status_open)
                body = state.closesAtLocal?.let { "يغلق على $it" }
                    ?: context.getString(R.string.trust_traders)
            }
            MarketState.CLOSED -> {
                title = context.getString(R.string.status_closed)
                body = state.opensAtLocal?.let { "الفتح $it" }
                    ?: context.getString(R.string.settings_disclaimer)
            }
            MarketState.UNKNOWN -> {
                title = context.getString(R.string.status_unknown)
                body = "لا يوجد تأكيد من التجار بعد"
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
            builder.setSubText("جدول معتاد — غير مؤكد")
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
        }
    }
}
