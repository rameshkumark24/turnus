package com.turnus.rota.notify

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.turnus.rota.TurnusApplication
import com.turnus.rota.widget.RotaWidgetReceiver
import java.util.concurrent.TimeUnit

/**
 * Slides the reminder window forward once a day.
 *
 * The window is thirty days deep, so this is not what keeps reminders working
 * in the short term — opening the app does that. It is the backstop for the
 * user who does not open the app: someone on a settled rota has no reason to,
 * and without this their reminders would simply run out a month after their
 * last visit.
 *
 * WorkManager rather than another alarm because this job has no deadline. It
 * needs to happen roughly daily and can be batched with whatever else the
 * system is doing, which is exactly the trade WorkManager exists to make, and
 * it survives reboots on its own.
 */
class ReminderTopUpWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val application = applicationContext as? TurnusApplication ?: return Result.success()
        return try {
            NotificationChannels.ensure(applicationContext)
            ReminderScheduler.reschedule(applicationContext, application.repository)
            // Rolls the widget over the date boundary for someone who has not
            // opened the app: without this it would sit on a stale "today".
            RotaWidgetReceiver.refresh(applicationContext)
            Result.success()
        } catch (failure: Exception) {
            // Retry rather than failure: the usual cause is transient, and a
            // failed periodic job is not rescheduled until its next period,
            // which here would mean a day without a top-up.
            Log.w(TAG, "top-up failed, will retry", failure)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ReminderTopUp"
        private const val NAME = "reminder-top-up"

        /**
         * KEEP, not UPDATE: called on every app open and every boot, and
         * replacing the request each time would reset its period, so on a phone
         * that reboots often the job could keep being deferred and never run.
         */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderTopUpWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(6, TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
