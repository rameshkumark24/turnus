package com.turnus.rota

import android.app.Application
import com.turnus.rota.data.RotaRepository
import com.turnus.rota.data.TurnusDatabase
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.Presets
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        scope.launch { installStarterData() }
    }

    /**
     * Seeds the default shift types, and — temporarily — a starter pattern.
     *
     * The shift-type seeding is permanent: presets reference the engine's
     * canonical ids, so those rows have to exist before any preset resolves.
     *
     * The starter pattern is SCAFFOLDING. It exists so the app opens onto a
     * populated grid while the setup flow does not exist yet, and it must be
     * deleted the moment the anchor picker lands — shipping it would silently
     * decide a user's rota for them, which is precisely the thing this app
     * must never get wrong.
     */
    private suspend fun installStarterData() {
        repository.seedDefaultsIfEmpty()

        if (repository.activePattern() == null) {
            repository.saveActivePattern(
                Presets.FOUR_ON_FOUR_OFF.toPattern(
                    id = "starter",
                    anchor = DayNumber.today(),
                ),
            )
        }
    }
}
