package com.turnus.rota.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The manifest entry point for the widget.
 *
 * [refresh] is the hook everything else calls: the app after an edit, the daily
 * top-up worker, and the boot receiver. A widget that only updated on its own
 * schedule would show yesterday's shift for up to half an hour after someone
 * corrected their rota, which is exactly the moment they are checking it.
 *
 * The one update nobody else triggers is the date changing, which
 * [DateRollover] asks for and this receiver turns into an ordinary [refresh].
 */
class RotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RotaWidget()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DateRollover.ACTION) {
            super.onReceive(context, intent)
            return
        }
        if (placedIds(context).isEmpty()) {
            DateRollover.cancel(context)
            return
        }
        // Through refresh rather than a plain update broadcast: a new date is
        // new data, and a widget session still open from a draw a few seconds
        // before midnight would otherwise redraw yesterday. Harmless if
        // anything else sends this action: the only effect is a redraw.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    /** The last widget is gone, so nothing needs redrawing at midnight. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        DateRollover.cancel(context)
    }

    companion object {
        /**
         * Redraws every placed widget from a fresh read of the rota.
         *
         * The widgets come from Android's own list, not Glance's `updateAll`.
         * Glance finds a widget class's instances through a table keyed by
         * that class's name, written by this receiver when the system sends
         * it an update. R8 renames [RotaWidget] differently in each release,
         * so after an app update the table names a class that no longer
         * exists, and `updateAll` finds nothing until the system next sends
         * an update — which, with `updatePeriodMillis` at 0, may be never.
         * Meanwhile the launcher shows the placeholder layout, a made-up week.
         * Seen on the emulator: 1.0.2 installed over 1.0.1 left the widget on
         * that placeholder through the update's own refresh and two edits.
         *
         * Bumping [GENERATION] before each update is what makes "fresh" true
         * for a widget whose Glance session is still open: the update reloads
         * its state, and the new number makes it read the rota again.
         */
        suspend fun refresh(context: Context) {
            val glance = GlanceAppWidgetManager(context)
            val widget = RotaWidget()
            for (appWidgetId in placedIds(context)) {
                val id = glance.getGlanceIdBy(appWidgetId)
                updateAppWidgetState(context, id) { state ->
                    state[GENERATION] = (state[GENERATION] ?: 0L) + 1
                }
                widget.update(context, id)
            }
        }

        /** The widgets on the home screen right now, as the launcher knows them. */
        private fun placedIds(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, RotaWidgetReceiver::class.java))
    }
}
