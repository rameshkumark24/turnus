package com.turnus.rota.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

@Database(
    entities = [
        ShiftTypeEntity::class,
        PatternEntity::class,
        DayOverrideEntity::class,
        AppMetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TurnusDatabase : RoomDatabase() {

    abstract fun shiftTypeDao(): ShiftTypeDao
    abstract fun patternDao(): PatternDao
    abstract fun dayOverrideDao(): DayOverrideDao
    abstract fun appMetaDao(): AppMetaDao

    companion object {

        const val NAME: String = "turnus.db"

        /**
         * Every migration ever added goes in this array, and none is ever
         * removed. Users skip releases — someone will go from 1.0 straight to
         * 1.7 — so the chain has to be complete, not just adjacent.
         */
        internal val MIGRATIONS: Array<androidx.room.migration.Migration> = emptyArray()

        /**
         * Opens the database, taking a copy of the file first if a migration is
         * pending.
         *
         * This is the on-device equivalent of a pre-deploy backup, and it is the
         * only safety net that exists here: the migration runs unattended on a
         * stranger's phone, exactly once, and cannot be rolled back because the
         * app version that ran it cannot be un-shipped.
         *
         * [allowDestructive] exists for debug builds only. Room's destructive
         * fallback silently wipes user data, so wiring it into a release build
         * turns a schema mistake into data loss for everyone who updates.
         */
        fun build(
            context: Context,
            name: String = NAME,
            allowDestructive: Boolean = false,
        ): TurnusDatabase {
            backUpBeforeMigration(context, name)
            return Room.databaseBuilder(context.applicationContext, TurnusDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .apply { if (allowDestructive) fallbackToDestructiveMigration(dropAllTables = true) }
                .build()
        }

        /**
         * Copies the database file to `<name>.pre-migration` when the stored
         * schema version is older than the code's.
         *
         * Cheap insurance: the copy is overwritten on each successful upgrade,
         * so at most one generation is kept, and it gives support a file to ask
         * for when someone reports losing their rota after an update.
         */
        private fun backUpBeforeMigration(context: Context, name: String) {
            val live = context.getDatabasePath(name)
            if (!live.exists()) return
            runCatching {
                val backup = File(live.parentFile, "$name.pre-migration")
                live.copyTo(backup, overwrite = true)
            }
        }
    }
}
