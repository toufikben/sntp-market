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
import dz.sntp.market.data.model.Caveat
import dz.sntp.market.data.model.MarketState
import dz.sntp.market.data.model.MarketStatus
import dz.sntp.market.data.model.Source
import dz.sntp.market.util.MarketTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * screen ([dz.sntp.market.data.repo.MarketRepository.cachedStateOnly]), so it
 * cannot claim to be fresher than the app. It never fetches: a widget that opens
 * a socket on every launcher redraw would drain the battery of exactly the users
 * who need it.
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

        /**
         * Redraws every placed widget. Called after the app refreshes its cache.
         *
         * This updates the views directly instead of broadcasting APPWIDGET_UPDATE
         * back to ourselves. The broadcast round-trip needs the process to stay
         * alive long enough to receive it, which is not guaranteed right after a
         * refresh on a phone that reclaims memory aggressively — the widget would
         * keep showing the previous state until the launcher's own tick.
         *
         * Suspends until the redraw is written, on purpose. It used to launch its
         * own unowned coroutine and return, so [MorningWorker] finished doWork and
         * the system was free to tear the process down while the DataStore read was
         * still pending — on precisely the Xiaomi/Oppo phones that kill background
         * work, which is why the 05:00 widget update was the one that went missing.
         */
        suspend fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, StatusWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            val app = context.applicationContext as? SntpApp ?: return
            withContext(Dispatchers.IO) {
                val state = runCatching { app.marketRepository.cachedStateOnly() }.getOrNull()
                val views = build(context, state)
                ids.forEach { id ->
                    runCatching { mgr.updateAppWidget(id, views) }
                }
            }
        }

        private fun build(context: Context, state: MarketState?): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_status)

            // Nothing cached yet is not "unknown market", it is "the app has never
            // reached the server on this phone". Saying so is the only honest text —
            // and no source line at all, because there is no source behind nothing.
            if (state == null) {
                // "لا بيانات اليوم" is literally true here: there is no data for
                // today. The detail line says why, which is the actionable part.
                v.setTextViewText(R.id.widget_status, context.getString(R.string.status_no_data))
                v.setTextViewText(R.id.widget_detail, context.getString(R.string.widget_no_data))
                v.setTextViewText(R.id.widget_trust, "")
                v.setTextColor(R.id.widget_status, context.getColor(R.color.widget_unknown))
                v.setTextColor(R.id.widget_trust, context.getColor(R.color.widget_text_secondary))
                return withClick(context, v)
            }

            // The app's own rule, so the widget and the card cannot disagree: an
            // unknown with no reporters and no confidence is "no data today", not
            // "unconfirmed" — the second implies someone looked and could not agree.
            val noData = state.hasNoDataToday

            val statusText = when {
                state.status == MarketStatus.OPEN -> context.getString(R.string.status_open)
                state.status == MarketStatus.CLOSED -> context.getString(R.string.status_closed)
                noData -> context.getString(R.string.status_no_data)
                else -> context.getString(R.string.status_unknown)
            }
            val statusColor = when (state.status) {
                MarketStatus.OPEN -> R.color.widget_open
                MarketStatus.CLOSED -> R.color.widget_closed
                MarketStatus.UNKNOWN -> R.color.widget_unknown
            }

            v.setTextViewText(R.id.widget_status, statusText)
            v.setTextColor(R.id.widget_status, context.getColor(statusColor))
            // "لا بيانات اليوم" used to repeat on the second line. Suppressed only
            // when it would say the same thing twice — with the usual window now
            // filled in for an unknown state, that second line carries real
            // information and must stay.
            val detail = detailOf(context, state)
            v.setTextViewText(
                R.id.widget_detail,
                if (detail == statusText) "" else detail,
            )
            v.setTextViewText(R.id.widget_trust, trustOf(context, state))
            // An unconfirmed row keeps its status word — the app does the same, the
            // last known state is still the answer — but the caveat must not be the
            // quietest thing on the card. On the app it is a banner; here the only
            // lever is colour, so the warning line takes the amber that means
            // "not confirmed" instead of the grey used for provenance.
            //
            // Keyed on the caveat, not on freshness: a seed row is stale in the
            // freshness sense and is *not* a warning — its own text already says
            // «جدول معتاد — غير مؤكد», and painting it amber put the loudest colour
            // on the line that has the least to warn about.
            v.setTextColor(
                R.id.widget_trust,
                context.getColor(
                    if (state.caveat != Caveat.NONE) {
                        R.color.widget_unknown
                    } else {
                        R.color.widget_text_secondary
                    }
                ),
            )
            return withClick(context, v)
        }

        /**
         * The second line. On a confirmed state it is the closing or opening hour;
         * on an unconfirmed one it is the usual window, labelled as usual. Those
         * two must never look alike — that is the app's entire premise.
         *
         * With no hour known the line is left empty rather than filled with a
         * source label or the legal disclaimer: both would read as if they were
         * the hour, which is worse than a blank.
         *
         * Times are wrapped in LTR isolates for the same reason StatusCard does it:
         * "07:00 — 13:00" is a left-to-right run inside an Arabic sentence, and
         * without the isolate bidi reorders it so the two hours swap places — on
         * the one line the whole product is read for.
         */
        private fun detailOf(context: Context, state: MarketState): String =
            when (state.status) {
                MarketStatus.OPEN -> state.closesAtLocal
                    ?.let { context.getString(R.string.closes_at, isolate(it)) }
                    ?: ""

                MarketStatus.CLOSED -> state.opensAtLocal
                    ?.let { context.getString(R.string.opens_at, isolate(it)) }
                    ?: ""

                MarketStatus.UNKNOWN -> {
                    val o = state.opensAtLocal
                    val c = state.closesAtLocal
                    when {
                        o != null && c != null ->
                            context.getString(R.string.usual_hours, isolate(o), isolate(c))
                        // Same label the card uses. This used to print nothing at all
                        // when only the opening hour was known, so the widget went
                        // blank over an hour the app was showing.
                        o != null -> context.getString(R.string.usual_opens_at, isolate(o))
                        else -> ""
                    }
                }
            }

        /** Left-to-right isolate around a time so bidi cannot reorder it. */
        private fun isolate(time: String): String = "\u2066$time\u2069"

        /**
         * The locale the widget's own strings are in, not the phone's.
         *
         * This app ships `values/` (Arabic) and `values-fr/`. A phone set to
         * English shows Arabic text while its locale is "en", so formatting the
         * day name with the device locale printed an English month inside an
         * Arabic line — the same trap [displayLocale] avoids on the card, and the
         * widget cannot call that one: it is `@Composable`.
         */
        private fun widgetLocale(context: Context): java.util.Locale {
            val locales = context.resources.configuration.locales
            val lang = if (locales.isEmpty) "" else locales[0].language
            return if (lang == "fr") java.util.Locale.FRENCH else java.util.Locale.forLanguageTag("ar-DZ")
        }

        /**
         * Third line: where the answer came from, and how old it is when that
         * matters. A stale row keeps the status word but says out loud that it is
         * no longer confirmed.
         */
        private fun trustOf(context: Context, state: MarketState): String {
            // The rule is [MarketState.caveat], not a fourth copy of it. The seed
            // check comes first there because a seed row is the usual timetable, not
            // a report that has aged: saying "last known information, now unconfirmed"
            // over it claimed a confirmation existed and lapsed, when nothing was ever
            // confirmed.
            if (state.caveat != Caveat.NONE) {
                return context.getString(R.string.widget_stale)
            }
            val source = when (state.source) {
                Source.ADMIN -> context.getString(R.string.trust_admin)
                Source.TRADERS -> context.getString(R.string.trust_traders)
                Source.SEED -> context.getString(R.string.trust_seed)
            }
            val stamp = MarketTime.stampWithDay(state.updatedAtEpochMs, widgetLocale(context))
                ?: return source
            // Same bidi reason as the hours: a bare HH:mm in an Arabic line.
            return "$source · ${isolate(stamp)}"
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
