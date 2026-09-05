package com.turnus.rota.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
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
            backUpIfMigrationPending(context, name)
            return Room.databaseBuilder(context.applicationContext, TurnusDatabase::class.java, name)
                .addMigrations(*MIGRATIONS)
                .apply { if (allowDestructive) fallbackToDestructiveMigration(dropAllTables = true) }
                .build()
        }

        /**
         * Copies the database aside when, and only when, the file on disk is
         * older than the schema this build expects.
         *
         * The version check is the whole point. Copying on every open would
         * overwrite the saved copy with the already-migrated file on the very
         * next launch — destroying the one artifact worth having when someone
         * reports losing their rota after an update.
         *
         * All three files are copied. Room runs in WAL mode from API 26, so
         * committed transactions can still be sitting in `-wal`; taking the
         * main file alone yields a backup silently missing the user's most
         * recent edits.
         *
         * Failures are logged rather than swallowed: a copy that never happened
         * must not look identical to one that did.
         */
        private fun backUpIfMigrationPending(context: Context, name: String) {
            val live = context.getDatabasePath(name)
            if (!live.exists()) return

            val stored = runCatching {
                SQLiteDatabase.openDatabase(live.path, null, SQLiteDatabase.OPEN_READONLY)
                    .use { it.version }
            }.getOrElse { failure ->
                Log.w(TAG, "Could not read schema version; skipping pre-migration copy", failure)
                return
            }

            if (stored >= SCHEMA_VERSION) return

            runCatching {
                listOf("", "-wal", "-shm").forEach { suffix ->
                    val source = File(live.path + suffix)
                    if (source.exists()) {
                        source.copyTo(File(live.path + suffix + BACKUP_SUFFIX), overwrite = true)
                    }
                }
            }.onFailure { failure ->
                Log.e(TAG, "Pre-migration backup failed for v$stored -> v$SCHEMA_VERSION", failure)
            }.onSuccess {
                Log.i(TAG, "Backed up database before migrating v$stored -> v$SCHEMA_VERSION")
            }
        }

        private const val TAG = "TurnusDatabase"
        private const val BACKUP_SUFFIX = ".pre-migration"
        private const val SCHEMA_VERSION = 1
    }
}
