package com.turnus.rota.notify

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.turnus.rota.MainActivity
import com.turnus.rota.R
import com.turnus.rota.TurnusApplication
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.ShiftEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Posts the reminder when its alarm fires.
 *
 * The shift is re-read from the database rather than carried in the intent. An
 * alarm set three weeks ago describes a rota that may since have been edited,
 * and a notification saying "Night shift" for a day the user swapped to a Day
 * is worse than no notification: it is a wrong answer delivered with
 * confidence.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val day = dayFrom(intent.data) ?: return

        val application = context.applicationContext as? TurnusApplication ?: return
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                notify(context, application, day)
            } catch (failure: Exception) {
                // A broadcast receiver that throws takes the process down, and
                // this one runs unattended while the app is not even open.
                Log.e(TAG, "could not post reminder for $day", failure)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun notify(context: Context, application: TurnusApplication, day: DayNumber) {
        // One consistent read of pattern, overrides, definitions and settings.
        // Fetched separately, an edit landing between the calls could resolve
        // the day against one rota and name it from another.
        val inputs = application.repository.reminderInputs(day, days = 0) ?: return
        if (!inputs.settings.enabled) return

        val shiftTypeId = ShiftEngine.resolve(inputs.pattern, inputs.overrides, day) ?: return
        if (shiftTypeId in inputs.settings.mutedShiftTypeIds) return

        val definition = inputs.definitions[shiftTypeId] ?: return
        val name = definition.name

        // Checked at post time rather than assumed: the permission can be
        // revoked at any point after the alarm was set, and posting without it
        // throws on API 33+.
        //
        // The version guard is not decoration. POST_NOTIFICATIONS does not
        // exist before API 33, and asking the platform about a permission it
        // has never heard of does not answer "granted" — so without this, every
        // reminder on Android 8 through 12 would have been silently dropped,
        // on exactly the older handsets this app's users are most likely to own.
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Log.i(TAG, "notification permission not granted; nothing posted")
            return
        }

        val start = definition.startMinute
            ?.let { LocalTime.MIDNIGHT.plusMinutes(it.toLong()).format(TIME) }

        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText(
                when {
                    start != null && day == DayNumber.today() -> "Starts at $start today"
                    start != null -> "Starts at $start on ${day.toLocalDate().format(DATE)}"
                    else -> "You are working on ${day.toLocalDate().format(DATE)}"
                },
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()

        NotificationManagerCompat.from(context).notify(day.value.toInt(), notification)
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val ACTION_REMIND = "com.turnus.rota.action.REMIND"
        private const val TAG = "ReminderReceiver"
        private const val SCHEME = "turnus"

        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")

        /** The day travels in the intent's data so PendingIntents stay distinct. */
        fun uriFor(day: DayNumber): Uri = "$SCHEME://reminder/${day.value}".toUri()

        private fun dayFrom(uri: Uri?): DayNumber? =
            uri?.lastPathSegment?.toLongOrNull()?.let(::DayNumber)
    }
}
