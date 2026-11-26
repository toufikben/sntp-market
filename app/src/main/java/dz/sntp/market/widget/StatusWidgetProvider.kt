package dz.sntp.market.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dz.sntp.market.MainActivity
import dz.sntp.market.R
import dz.sntp.market.SntpApp
import dz.sntp.market.data.model.Freshness
import dz.sntp.market.data.model.MarketState
import dz.sntp.market.data.model.MarketStatus
import dz.sntp.market.data.model.Source
import dz.sntp.market.util.MarketTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The home-screen answer, for the phones this app is actually installed on.
 *
 * The nine gaps list asked for a widget because the whole product is one question
 * asked before leaving the house; making the trader open an app to read one word
 * is the difference between using it and not. Xiaomi and Oppo also kill
 * background work aggressively, so a widget that reads the cache is the only
 * surface guaranteed to be there at 05:00.
 *
 * It reads the same cached row and applies the same freshness rules as the
 * screen ([MarketRepository.cachedStateOnly]), so it cannot claim to be fresher
 * than the app. It never fetches: a widget that opens a socket on every launcher
 * redraw would drain the battery of exactly the users who need it.
 */
class StatusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // DataStore is suspend-only, so the broadcast is kept alive while the
        // single read completes. Without goAsync the process can be torn down
        // mid-read and the widget stays on its placeholder.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = runCatching {
                    (context.applicationContext as SntpApp).marketRepository.cachedStateOnly()
                }.getOrNull()
                val views = build(context, state)
                appWidgetIds.forEach { id ->
                    runCatching { appWidgetManager.updateAppWidget(id, views) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {

        /** Redraws every placed widget. Called after the app refreshes its cache. */
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, StatusWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            val intent = Intent(context, StatusWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        private fun build(context: Context, state: MarketState?): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_status)

            // Nothing cached yet is not "unknown market", it is "the app has never
            // reached the server on this phone". Saying so is the only honest text.
            if (state == null) {
                v.setTextViewText(R.id.widget_status, context.getString(R.string.status_unknown))
                v.setTextViewText(R.id.widget_detail, context.getString(R.string.widget_no_data))
                v.setTextViewText(R.id.widget_trust, context.getString(R.string.trust_seed))
                v.setTextColor(R.id.widget_status, context.getColor(R.color.widget_unknown))
                v.setTextColor(R.id.widget_trust, context.getColor(R.color.widget_text_secondary))
                return withClick(context, v)
            }

            val statusText = when (state.status) {
                MarketStatus.OPEN -> context.getString(R.string.status_open)
                MarketStatus.CLOSED -> context.getString(R.string.status_closed)
                MarketStatus.UNKNOWN -> context.getString(R.string.status_unknown)
            }
            val statusColor = when (state.status) {
                MarketStatus.OPEN -> R.color.widget_open
                MarketStatus.CLOSED -> R.color.widget_closed
                MarketStatus.UNKNOWN -> R.color.widget_unknown
            }

            v.setTextViewText(R.id.widget_status, statusText)
            v.setTextColor(R.id.widget_status, context.getColor(statusColor))
            v.setTextViewText(R.id.widget_detail, detailOf(context, state))
            v.setTextViewText(R.id.widget_trust, trustOf(context, state))
            v.setTextColor(R.id.widget_trust, context.getColor(R.color.widget_text_secondary))
            return withClick(context, v)
        }

        /**
         * The second line. On a confirmed state it is the closing or opening hour;
         * on an unconfirmed one it is the usual window, labelled as usual. Those
         * two must never look alike — that is the app's entire premise.
         */
        private fun detailOf(context: Context, state: MarketState): String =
            when (state.status) {
                MarketStatus.OPEN -> state.closesAtLocal
                    ?.let { context.getString(R.string.closes_at, it) }
                    ?: context.getString(R.string.trust_traders)

                MarketStatus.CLOSED -> state.opensAtLocal
                    ?.let { context.getString(R.string.opens_at, it) }
                    ?: context.getString(R.string.settings_disclaimer)

                MarketStatus.UNKNOWN -> {
                    val o = state.opensAtLocal
                    val c = state.closesAtLocal
                    if (o != null && c != null) {
                        context.getString(R.string.usual_hours, o, c)
                    } else {
                        context.getString(R.string.status_no_data)
                    }
                }
            }

        /**
         * Third line: where the answer came from, and how old it is when that
         * matters. A stale row keeps the status word but says out loud that it is
         * no longer confirmed.
         */
        private fun trustOf(context: Context, state: MarketState): String {
            if (state.freshness == Freshness.STALE || state.freshness == Freshness.OFFLINE) {
                return context.getString(R.string.widget_stale)
            }
            val source = when (state.source) {
                Source.ADMIN -> context.getString(R.string.trust_admin)
                Source.TRADERS -> context.getString(R.string.trust_traders)
                Source.SEED -> context.getString(R.string.trust_seed)
            }
            val stamp = MarketTime.stamp(state.updatedAtEpochMs)
                ?: return source
            return "$source · $stamp"
        }

        private fun withClick(context: Context, v: RemoteViews): RemoteViews {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.widget_root, pending)
            return v
        }
    }
}
