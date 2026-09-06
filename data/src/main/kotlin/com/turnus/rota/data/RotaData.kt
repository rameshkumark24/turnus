package com.turnus.rota.data

import android.content.Context

/**
 * The one [RotaRepository] the process may have.
 *
 * The widget and the app are separate modules but the same process, and Room
 * behaves badly when two instances are opened on one database file — the
 * invalidation tracker in each is blind to the other's writes, so a widget
 * could sit showing a shift the user changed minutes ago and never notice.
 *
 * The application installs the real instance at startup. The fallback exists
 * for the case where the process was started *for* a widget update: Android
 * still runs Application.onCreate first, so in practice it is never taken, but
 * a widget that crashed because of an ordering assumption would be a bad way to
 * find out the assumption was wrong.
 */
object RotaData {

    @Volatile
    private var instance: RotaRepository? = null

    fun install(repository: RotaRepository) {
        instance = repository
    }

    fun repository(context: Context): RotaRepository =
        instance ?: synchronized(this) {
            instance ?: RotaRepository(TurnusDatabase.build(context.applicationContext))
                .also { instance = it }
        }
}
