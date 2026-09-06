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
        )
    }
}
