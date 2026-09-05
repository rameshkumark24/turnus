package com.turnus.rota

import android.app.Application
import com.turnus.rota.data.RotaRepository
import com.turnus.rota.data.TurnusDatabase
import com.turnus.rota.notify.NotificationChannels
import com.turnus.rota.notify.ReminderTopUpWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Holds the two long-lived objects the app needs.
 *
 * There is no DI framework here on purpose. The whole graph is a database and a
 * repository; a container would add a dependency, a compile step and a layer of
 * indirection to solve a problem this app does not have.
 */
class TurnusApplication : Application() {

    /**
     * For work that must finish even as the Activity goes away — rescheduling
     * alarms when the user leaves the app is exactly that.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: TurnusDatabase by lazy {
        TurnusDatabase.build(
            context = this,
            // Destructive fallback wipes user data. It is acceptable while the
            // schema is still moving in debug, and never in a release build.
            allowDestructive = BuildConfig.DEBUG,
        )
    }

    val repository: RotaRepository by lazy { RotaRepository(database) }

    override fun onCreate() {
        super.onCreate()
        // Seeds the default shift types only. Presets reference the engine's
        // canonical ids, so those rows must exist before any preset resolves.
        //
        // No starter pattern: the app must never decide a user's rota for them.
        // With no pattern saved, the root routes to setup instead.
        applicationScope.launch { repository.seedDefaultsIfEmpty() }

        // The channel must exist before the user goes looking for it in system
        // settings, not only after a reminder has already fired once.
        NotificationChannels.ensure(this)

        // KEEP, so this is a no-op once enqueued. It is the backstop for the
        // user who never opens the app: a settled rota gives them no reason to,
        // and the alarm window would otherwise run out a month after their last
        // visit.
        ReminderTopUpWorker.enqueue(this)
    }
}
