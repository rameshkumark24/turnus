package com.turnus.rota.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.turnus.rota.data.RotaRepository
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.Reminder
import com.turnus.rota.engine.Reminders
import java.time.ZoneId

/**
 * Sets the alarms for the next [WINDOW_DAYS] days of shifts.
 *
 * A rolling window rather than a chain of one-at-a-time alarms. A chain is
 * cheaper, but it breaks permanently the first time a single link is dropped —
 * by a force-stop, a crash, or an OEM battery manager — and the user finds out
 * by missing a shift. A window degrades instead: losing one alarm costs one
 * reminder, and the next top-up repairs it.
 *
 * The window is rebuilt from scratch every time rather than diffed. Diffing
 * would mean tracking which alarms are currently set, which is state that can
 * drift out of agreement with the system; cancelling a fixed span of days and
 * re-setting it cannot.
 */
object ReminderScheduler {

    /** Roughly a month of shifts. */
    const val WINDOW_DAYS: Int = 30

    /**
     * Cancelled either side of the window, because the window slides. Yesterday's
     * schedule reached days this one does not, and an alarm nobody cancels still
     * fires.
     */
    private const val CANCEL_MARGIN_DAYS = 3

    private const val TAG = "ReminderScheduler"

    /**
     * Rebuilds the whole window. Safe to call as often as you like — on app
     * open, on boot, after any edit, from the daily worker.
     */
    suspend fun reschedule(context: Context, repository: RotaRepository) {
        val today = DayNumber.today()
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (alarms == null) {
            Log.w(TAG, "no AlarmManager; reminders cannot be scheduled")
            return
        }

        clearWindow(context, alarms, today)

        val inputs = repository.reminderInputs(today, WINDOW_DAYS) ?: return
        if (!inputs.settings.enabled) return

        val zone = ZoneId.systemDefault()
        val nowMillis = System.currentTimeMillis()

        val planned = Reminders.plan(
            pattern = inputs.pattern,
            overrides = inputs.overrides,
            definitions = inputs.definitions,
            settings = inputs.settings,
            from = today,
            days = WINDOW_DAYS,
        )

        var set = 0
        planned.forEach { reminder ->
            val triggerAt = Reminders.instantOf(reminder, zone).toEpochMilli()
            // The engine plans the window without a clock, so the first few
            // reminders in it have usually already been and gone. Setting an
            // alarm in the past fires it immediately, which would greet anyone
            // opening the app with a notification about a shift they are
            // already on.
            if (triggerAt <= nowMillis) return@forEach

            set(context, alarms, reminder, triggerAt)
            set++
        }
        Log.i(TAG, "scheduled $set reminders of ${planned.size} planned")
    }

    /** Drops every alarm in the window, used when reminders are switched off. */
    fun cancelAll(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        clearWindow(context, alarms, DayNumber.today())
    }

    private fun clearWindow(context: Context, alarms: AlarmManager, today: DayNumber) {
        val from = -CANCEL_MARGIN_DAYS
        val until = WINDOW_DAYS + CANCEL_MARGIN_DAYS
        (from until until).forEach { offset ->
            val day = today + offset.toLong()
            pendingIntent(context, day, create = false)?.let {
                alarms.cancel(it)
                it.cancel()
            }
        }
    }

    private fun set(
        context: Context,
        alarms: AlarmManager,
        reminder: Reminder,
        triggerAt: Long,
    ) {
        val intent = pendingIntent(context, reminder.day, create = true) ?: return

        // Exact alarms are a permission the user can refuse, and on Android 14+
        // they are not granted by default. Rather than nagging or failing
        // silently, an inexact alarm is set instead: it can drift by a few
        // minutes under Doze, which is worse than exact but far better than no
        // reminder at all. The settings screen says which one is in force.
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarms.canScheduleExactAlarms()

        try {
            if (canBeExact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            }
        } catch (denied: SecurityException) {
            // canScheduleExactAlarms can go stale between the check and the
            // call — the user can revoke it from settings while this runs.
            Log.w(TAG, "exact alarm refused, falling back to inexact", denied)
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
    }

    /**
     * One alarm slot per day, keyed by the day worked.
     *
     * The request code is the day number itself, so re-scheduling the same day
     * replaces its alarm rather than adding a second one — the property that
     * makes "rebuild the whole window" safe to run repeatedly.
     */
    private fun pendingIntent(context: Context, day: DayNumber, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            // In the data, not the extras: PendingIntent equality ignores
            // extras, so two days would otherwise collide onto one alarm.
            data = ReminderReceiver.uriFor(day)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE

        return PendingIntent.getBroadcast(context, day.value.toInt(), intent, flags)
    }
}
