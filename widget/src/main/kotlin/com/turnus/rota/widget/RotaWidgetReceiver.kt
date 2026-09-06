package com.turnus.rota.widget

import android.content.Context
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
 */
class RotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RotaWidget()

    companion object {
        suspend fun refresh(context: Context) {
            RotaWidget().updateAll(context)
        }
    }
}
