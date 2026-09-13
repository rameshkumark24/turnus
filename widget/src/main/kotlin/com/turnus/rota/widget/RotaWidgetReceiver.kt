package com.turnus.rota.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll

/**
 * The manifest entry point for the widget.
 *
 * [refresh] is the hook everything else calls: the app after an edit, the daily
 * top-up worker, and the boot receiver. A widget that only updated on its own
 * schedule would show yesterday's shift for up to half an hour after someone
 * corrected their rota, which is exactly the moment they are checking it.
 *
 * The one update nobody else triggers is the date changing, which
 * [DateRollover] asks for and this receiver turns into an ordinary redraw.
 */
class RotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RotaWidget()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DateRollover.ACTION) {
            super.onReceive(context, intent)
            return
        }
        // Handed to Glance as the standard update broadcast, so its own async
        // handling does the drawing, with the widget ids read now rather than
        // when the alarm was set. Harmless if anything else sends it: the only
        // effect is a redraw.
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, RotaWidgetReceiver::class.java))
        if (ids.isEmpty()) {
            DateRollover.cancel(context)
            return
        }
        super.onReceive(
            context,
            Intent(intent)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
        )
    }

    /** The last widget is gone, so nothing needs redrawing at midnight. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        DateRollover.cancel(context)
    }

    companion object {
        suspend fun refresh(context: Context) {
            RotaWidget().updateAll(context)
        }
    }
}
