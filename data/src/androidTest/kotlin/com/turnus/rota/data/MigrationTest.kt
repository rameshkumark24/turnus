package com.turnus.rota.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The upgrade path, run against real SQLite.
 *
 * A migration is the one piece of this app that cannot be rolled back. It runs
 * unattended, exactly once, on a stranger's phone, and the release that ran it
 * cannot be un-shipped. The property worth testing is therefore not that the
 * new column exists — it is that **the rows that were already there survive
 * unchanged**, because the failure everyone remembers is the update that lost
 * their rota.
 *
 * This is what `exportSchema = true` has been paying for since the first
 * commit: `runMigrationsAndValidate` compares the migrated database against the
 * schema Room expects and fails on any difference, including the ones that are
 * easy to get wrong by hand — a missing `NOT NULL`, a default that does not
 * match the entity annotation.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TurnusDatabase::class.java,
    )

    @Test
    fun addingTheBreakColumnKeepsEveryShift() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            // Written as raw SQL against the v1 schema on purpose. Using the
            // entity would compile against today's columns and so would not be
            // testing an upgrade from anything.
            db.execSQL(
                """
                INSERT INTO shift_type
                    (id, code, name, color, start_minute, duration_minute,
                     is_working, sort_order, created_at, updated_at)
                VALUES
                    ('day', 'D', 'Day', -2055364, 420, 720, 1, 0, 1000, 1000),
                    ('night', 'N', 'Night', -12756352, 1140, 720, 1, 1, 1000, 1000),
                    ('marker', 'X', 'Marker', -9733497, NULL, NULL, 1, 2, 1000, 1000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO pattern
                    (id, name, anchor_day, slots, slot_count, is_active, created_at, updated_at)
                VALUES ('p1', 'Four on four off', 20691, 'day,day,-,-', 4, 1, 1000, 1000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO day_override
                    (pattern_id, day, shift_type_id, overrides_shift, note, created_at, updated_at)
                VALUES ('p1', 20700, 'night', 1, 'swapped with Dave', 1000, 1000)
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            TurnusDatabase.MIGRATION_1_2,
        )

        migrated.query("SELECT COUNT(*) FROM shift_type").use { cursor ->
            cursor.moveToFirst()
            assertEquals("a shift was lost in the migration", 3, cursor.getInt(0))
        }

        // Every existing shift gets a zero break, which is what they have always
        // effectively had. Anything else would silently change the hours of
        // every month a user has already looked at.
        migrated.query("SELECT id, break_minutes FROM shift_type ORDER BY sort_order").use { cursor ->
            while (cursor.moveToNext()) {
                assertEquals(
                    "'${cursor.getString(0)}' did not get a zero break",
                    0,
                    cursor.getInt(1),
                )
            }
        }

        // The columns the migration does not touch must be untouched, not
        // merely present: an ALTER TABLE that rebuilt the table could quietly
        // drop a null or reorder something.
        migrated.query(
            "SELECT start_minute, duration_minute FROM shift_type WHERE id = 'day'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(420, cursor.getInt(0))
            assertEquals(720, cursor.getInt(1))
        }
        migrated.query(
            "SELECT start_minute IS NULL, duration_minute IS NULL FROM shift_type WHERE id = 'marker'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("a marker's null times survived as nulls", 1, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
        }

        // The rota and the days changed on it are the whole point of the app.
        migrated.query("SELECT anchor_day, slots FROM pattern WHERE id = 'p1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(20691, cursor.getLong(0))
            assertEquals("day,day,-,-", cursor.getString(1))
        }
        migrated.query("SELECT note FROM day_override WHERE pattern_id = 'p1' AND day = 20700")
            .use { cursor ->
                cursor.moveToFirst()
                assertEquals("swapped with Dave", cursor.getString(0))
            }

        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
