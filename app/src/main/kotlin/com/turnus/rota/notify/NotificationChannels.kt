package com.turnus.rota.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * The one channel this app has.
 *
 * Created at startup rather than lazily before the first notification: a
 * channel is what the user configures, and it has to exist in system settings
 * before they go looking for it, not only after a shift reminder has already
 * fired once.
 *
 * Importance is HIGH. A reminder that arrives silently, for a shift someone is
 * relying on being told about, has failed at the only job it has. The user can
 * turn that down per-channel; the app cannot turn it up afterwards.
 */
object NotificationChannels {

    const val REMINDERS = "shift_reminders"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            REMINDERS,
            "Shift reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Told before a shift starts"
            enableVibration(true)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
