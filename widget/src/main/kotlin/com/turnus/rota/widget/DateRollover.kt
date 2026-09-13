package com.turnus.rota.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.turnus.rota.engine.DayNumber
import java.time.ZoneId

/**
 * Redraws the widget when the date changes.
 *
 * Everything on the widget is relative to today — "Off today", "Back
 * tomorrow", a week strip that starts on today — and nothing redrew it at
 * midnight. The app refreshes it when left, the boot receiver when the phone
 * starts or the clock moves, and the daily worker at whatever hour WorkManager
 * picks. So for someone who does not open the app, the person a widget is for,
 * Monday night's "Back tomorrow" was still on the home screen on Tuesday.
 *
 * `ACTION_DATE_CHANGED` would be the obvious signal, but manifest receivers
 * stopped hearing it in Android 8, and a widget has no running process to
 * register one at runtime. So one alarm is set for just after the next local
 * midnight, and set again every time the widget draws.
 *
 * Not a wake-up alarm, and not exact. A widget nobody is looking at does not
 * need to change at 00:00; it needs to be right when the screen next comes on,
 * which is when a non-wake-up alarm is delivered. That costs nothing while the
 * phone sleeps, and needs no exact-alarm permission.
 */
internal object DateRollover {

    const val ACTION = "com.turnus.rota.widget.action.DATE_ROLLOVER"

    /** The smallest window Android honours for an alarm that is not exact. */
    private const val WINDOW_MS = 10 * 60 * 1000L

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        // A minute past midnight rather than on it, so the redraw cannot read
        // the clock a moment early and draw yesterday again. A timezone change
        // redraws the widget through the boot receiver, which sets this again
        // against the new zone.
        val triggerAt = (DayNumber.today(zone) + 1L).toLocalDate()
            .atStartOfDay(zone)
            .plusMinutes(1)
            .toInstant()
            .toEpochMilli()
        alarms.setWindow(AlarmManager.RTC, triggerAt, WINDOW_MS, pendingIntent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, RotaWidgetReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
