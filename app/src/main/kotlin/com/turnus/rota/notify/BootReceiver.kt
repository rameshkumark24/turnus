package com.turnus.rota.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.turnus.rota.TurnusApplication
import com.turnus.rota.widget.RotaWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Rebuilds the alarm window after a reboot.
 *
 * Alarms do not survive a restart — the system drops every one of them, without
 * telling the app. Without this receiver a user who reboots their phone on a
 * Sunday silently stops being reminded, and only finds out by missing a shift.
 * That makes this mandatory rather than a nicety.
 *
 * Several actions are handled, not just BOOT_COMPLETED. `LOCKED_BOOT_COMPLETED`
 * arrives first on devices with direct boot, and `MY_PACKAGE_REPLACED` fires
 * after an app update, which also clears nothing but is the other moment the
 * window can be silently stale.
 *
 * ### The clock moving is the third moment, and the least obvious
 *
 * A reminder is planned as a wall-clock time — "06:00, the day of that shift" —
 * and then converted once, at scheduling time, into an instant. Change the
 * timezone and that instant no longer means 06:00: someone who flies from
 * London to Madrid keeps a window of alarms that all fire an hour early, and
 * nothing in Android reschedules them. The app already rebuilds the window
 * whenever the user leaves a session, so the gap is exactly the case where they
 * have not opened it since — which is every case that matters, because the
 * person this fails is asleep in a hotel relying on it.
 *
 * `TIMEZONE_CHANGED` is on Android's implicit-broadcast exception list, so a
 * manifest receiver still hears it. `TIME_SET` is **not** on that list, and is
 * declared anyway rather than left out: it costs one line, it is harmless if it
 * never arrives, and the app is running for some of the cases where it does.
 * Which of the two actually reaches this receiver is recorded in the plan, not
 * guessed at here.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return

        val application = context.applicationContext as? TurnusApplication ?: return
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                NotificationChannels.ensure(context)
                ReminderScheduler.reschedule(context, application.repository)
                ReminderTopUpWorker.enqueue(context)
                RotaWidgetReceiver.refresh(context)
            } catch (failure: Exception) {
                Log.e(TAG, "could not restore reminders after ${intent.action}", failure)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"

        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // Sent by several manufacturers instead of the standard action.
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            // The clock moved under a window of already-converted instants.
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
        )
    }
}
