package com.turnus.rota.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.Pattern
import com.turnus.rota.engine.ReminderSettings
import com.turnus.rota.engine.ShiftCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Backup and restore, against real SQLite.
 *
 * The property worth testing is not that the JSON parses. It is that a rota
 * saved and restored is the *same rota* — same anchor, same cycle, same changed
 * days, same shift times — because a backup that quietly loses a day is worse
 * than no backup at all: the user finds out months later, at work, on the wrong
 * day.
 *
 * The other half is that a bad file changes nothing. Restore deletes everything
 * before it inserts, so a failure that got as far as the delete would take the
 * user's rota with it.
 */
@RunWith(AndroidJUnit4::class)
class RotaBackupTest {

    private lateinit var db: TurnusDatabase
    private lateinit var repository: RotaRepository
    private var ids = 0

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TurnusDatabase::class.java).build()
        repository = RotaRepository(db, now = { CLOCK }, newId = { "id${ids++}" })
        runBlocking { repository.seedDefaultsIfEmpty() }
    }

    @After
    fun close() = db.close()

    // --------------------------------------------------------------- roundtrip

    @Test
    fun aSavedRotaComesBackIdentical() = runBlocking {
        val pattern = seedRota()
        repository.setOverride(DayNumber(20_100), shiftTypeId = ShiftCode.NIGHT)
        repository.setOverride(DayNumber(20_101), shiftTypeId = null)
        repository.setNote(DayNumber(20_102), "swapped with Dave")
        repository.saveReminderSettings(
            ReminderSettings(enabled = true, leadMinutes = 120, mutedShiftTypeIds = setOf(ShiftCode.LATE)),
        )

        val before = repository.snapshot("test")
        val text = RotaBackup.encode(before)

        // Wipe it the way a new phone would.
        repository.restore(emptyRota())
        assertEquals(0, repository.snapshot("test").changedDays.size)

        val decoded = RotaBackup.decode(text)
        assertTrue("decode failed: $decoded", decoded is BackupResult.Success)
        repository.restore((decoded as BackupResult.Success).snapshot)

        val after = repository.snapshot("test")
        assertEquals(before.shiftTypes, after.shiftTypes)
        assertEquals(before.patterns, after.patterns)
        assertEquals(before.changedDays, after.changedDays)
        assertEquals(before.settings, after.settings)

        // And the restored rota is still the *active* one, not an orphan.
        assertEquals(pattern.anchor, repository.activePattern()?.anchor)
        assertEquals(pattern.slots, repository.activePattern()?.slots)
    }

    @Test
    fun aNoteOnlyDayKeepsItsNoteAndItsShift() = runBlocking {
        seedRota()
        repository.setNote(DayNumber(20_105), "half day")

        val restored = roundTrip()
        repository.restore(restored)

        val stored = repository.overrideFor(DayNumber(20_105))
        assertNotNull(stored)
        assertEquals("half day", stored!!.note)
        // The distinction the whole day_override design exists for: a note must
        // not pin the day to a shift.
        assertEquals(false, stored.overridesShift)
    }

    /**
     * A field added to the entity but forgotten in the restore mapping compiles
     * fine — Kotlin fills it from the default — and then quietly drops the
     * user's data. That happened to `break_minutes`, which would have moved
     * every hours total on the first restore.
     */
    @Test
    fun unpaidBreaksSurviveARoundTrip() = runBlocking {
        seedRota()
        repository.updateShiftType(
            id = ShiftCode.DAY,
            code = "D",
            name = "Day",
            color = 1,
            startMinute = 7 * 60,
            durationMinute = 12 * 60,
            breakMinutes = 45,
        )

        repository.restore(roundTrip())

        val restored = repository.shiftDefinitions().getValue(ShiftCode.DAY)
        assertEquals(45, restored.breakMinutes)
        assertEquals(12 * 60 - 45, restored.paidMinute)
    }

    @Test
    fun auditTimestampsSurviveARestore() = runBlocking {
        seedRota()
        val before = repository.snapshot("test")
        repository.restore(roundTrip())
        val after = repository.snapshot("test")
        assertEquals(
            before.shiftTypes.map { it.createdAtMillis },
            after.shiftTypes.map { it.createdAtMillis },
        )
    }

    // ------------------------------------------------------------ bad input

    @Test
    fun anythingThatIsNotJsonIsNotABackup() {
        listOf("", "   ", "hello", "<html></html>", "{", "[1,2,3]").forEach {
            assertEquals("for input '$it'", BackupResult.NotABackup, RotaBackup.decode(it))
        }
    }

    @Test
    fun jsonThatIsNotOursIsNotABackup() {
        assertEquals(
            BackupResult.NotABackup,
            RotaBackup.decode("""{"format":"someone.else","version":1}"""),
        )
    }

    @Test
    fun aNewerFormatIsReportedRatherThanGuessedAt() {
        val result = RotaBackup.decode(
            """{"format":"${RotaBackup.FORMAT}","version":99,"shiftTypes":[]}""",
        )
        assertEquals(BackupResult.TooNew(99), result)
    }

    @Test
    fun aPatternReferringToAMissingShiftIsDamaged() = runBlocking {
        seedRota()
        val text = RotaBackup.encode(
            repository.snapshot("test").let { snapshot ->
                snapshot.copy(shiftTypes = snapshot.shiftTypes.filterNot { it.id == ShiftCode.NIGHT })
            },
        )
        assertTrue(RotaBackup.decode(text) is BackupResult.Damaged)
    }

    @Test
    fun aBackupWithNoRotaIsDamaged() = runBlocking {
        seedRota()
        val text = RotaBackup.encode(repository.snapshot("test").copy(patterns = emptyList()))
        assertTrue(RotaBackup.decode(text) is BackupResult.Damaged)
    }

    @Test
    fun twoActiveRotasAreDamaged() = runBlocking {
        val pattern = seedRota()
        val snapshot = repository.snapshot("test")
        val duplicate = snapshot.patterns.first().copy(id = "second")
        val text = RotaBackup.encode(snapshot.copy(patterns = snapshot.patterns + duplicate))
        assertTrue(RotaBackup.decode(text) is BackupResult.Damaged)
        assertEquals(pattern.id, repository.activePattern()?.id)
    }

    // ------------------------------------------------------- nothing by halves

    @Test
    fun aRejectedRestoreLeavesTheRotaUntouched(): Unit = runBlocking {
        val pattern = seedRota()
        repository.setOverride(DayNumber(20_100), shiftTypeId = ShiftCode.NIGHT)
        val before = repository.snapshot("test")

        val broken = before.copy(
            patterns = before.patterns.map { it.copy(slots = listOf("no-such-shift")) },
        )
        assertThrows(BackupRejectedException::class.java) {
            runBlocking { repository.restore(broken) }
        }

        val after = repository.snapshot("test")
        assertEquals(before.shiftTypes, after.shiftTypes)
        assertEquals(before.patterns, after.patterns)
        assertEquals(before.changedDays, after.changedDays)
        assertEquals(pattern.id, repository.activePattern()?.id)
    }

    /**
     * The offer is answered once. Turning reminders on and later off is an
     * answer, so `enabled = false` alone must not bring the card back — which
     * is why this is a separate fact from the reminder settings.
     */
    @Test
    fun theReminderOfferIsRememberedIndependentlyOfTheSetting() = runBlocking {
        seedRota()
        assertEquals(false, repository.observeReminderPromptSeen().first())

        repository.markReminderPromptSeen()
        assertEquals(true, repository.observeReminderPromptSeen().first())

        // Enabled then disabled again: still answered, still must not re-ask.
        repository.saveReminderSettings(ReminderSettings(enabled = true))
        repository.saveReminderSettings(ReminderSettings(enabled = false))
        assertEquals(true, repository.observeReminderPromptSeen().first())
    }

    /** It travels with a backup, so restoring on a new phone does not re-ask. */
    @Test
    fun theReminderOfferSurvivesABackup() = runBlocking {
        seedRota()
        repository.markReminderPromptSeen()

        repository.restore(roundTrip())

        assertEquals(true, repository.observeReminderPromptSeen().first())
    }

    /** But a fresh start asks again — that is what "delete everything" means. */
    @Test
    fun deletingEverythingAsksAgain() = runBlocking {
        seedRota()
        repository.markReminderPromptSeen()

        repository.deleteEverything()

        assertEquals(false, repository.observeReminderPromptSeen().first())
    }

    @Test
    fun deleteEverythingLeavesAWorkingFirstRunApp() = runBlocking {
        seedRota()
        repository.setOverride(DayNumber(20_100), shiftTypeId = ShiftCode.NIGHT)
        repository.setNote(DayNumber(20_101), "hospital")
        repository.saveReminderSettings(ReminderSettings(enabled = true, leadMinutes = 120))

        repository.deleteEverything()

        // Gone: the rota, the changed days, the notes, the settings.
        assertNull(repository.activePattern())
        assertNull(repository.overrideFor(DayNumber(20_100)))
        assertNull(repository.overrideFor(DayNumber(20_101)))
        assertEquals(ReminderSettings(), repository.reminderSettings())

        // But not broken: the default shifts are back, so what the user sees is
        // a first run rather than an empty screen that looks like a bug.
        assertEquals(4, repository.shiftDefinitions().size)
    }

    @Test
    fun restoreReplacesRatherThanMerges() = runBlocking {
        seedRota()
        repository.setOverride(DayNumber(20_100), shiftTypeId = ShiftCode.NIGHT)
        val saved = repository.snapshot("test")

        // A day the backup knows nothing about, added after it was taken.
        repository.setOverride(DayNumber(20_200), shiftTypeId = ShiftCode.LATE)
        assertNotNull(repository.overrideFor(DayNumber(20_200)))

        repository.restore(saved)

        // Merging would have kept it. Replacing is what a user asking to go
        // back to a known state actually means.
        assertNull(repository.overrideFor(DayNumber(20_200)))
        assertNotNull(repository.overrideFor(DayNumber(20_100)))
    }

    // ------------------------------------------------------------- stable order

    /**
     * Two shifts that tie on sort_order come back in the same order every time.
     *
     * A tie cannot happen through the app, which assigns sort_order by
     * appending. It can happen through a file: a restored backup is written by
     * whatever produced it, and this one is deliberately built the way a
     * hand-edited or merged backup would be. SQLite promises nothing about the
     * order of tied rows, so without a second key the shift list can reshuffle
     * between two reads of an unchanged database — and the same rota can encode
     * to two different share codes.
     */
    @Test
    fun tiedShiftsComeBackInTheSameOrderEveryTime() = runBlocking {
        seedRota()
        val snapshot = repository.snapshot("test")
        val tied = snapshot.shiftTypes.mapIndexed { index, shift ->
            // Every one of them on the same rung.
            shift.copy(id = "shift-${'a' + index}", sortOrder = 0)
        }
        // The pattern has to name the new ids, or validation refuses the file.
        val byOldId = snapshot.shiftTypes.map { it.id }.zip(tied.map { it.id }).toMap()
        repository.restore(
            snapshot.copy(
                shiftTypes = tied,
                patterns = snapshot.patterns.map { pattern ->
                    pattern.copy(slots = pattern.slots.map { it?.let(byOldId::getValue) })
                },
                changedDays = emptyList(),
            ),
        )

        val reads = List(5) { repository.shiftStyles().map { it.id } }
        assertEquals("order was not stable: $reads", 1, reads.distinct().size)
        assertEquals(reads.first().sorted(), reads.first())
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun seedRota(): Pattern {
        val pattern = Pattern(
            id = "rota",
            name = "Four on four off",
            anchor = DayNumber(20_000),
            slots = listOf(
                ShiftCode.DAY, ShiftCode.DAY, ShiftCode.NIGHT, ShiftCode.NIGHT,
                null, null, null, null,
            ),
        )
        repository.saveActivePattern(pattern)
        return pattern
    }

    /** Save, encode, decode — what actually happens between two phones. */
    private suspend fun roundTrip(): BackupSnapshot {
        val text = RotaBackup.encode(repository.snapshot("test"))
        return (RotaBackup.decode(text) as BackupResult.Success).snapshot
    }

    /** A minimal valid rota, used to stand in for a wiped install. */
    private suspend fun emptyRota(): BackupSnapshot {
        val snapshot = repository.snapshot("test")
        return snapshot.copy(
            patterns = snapshot.patterns.map { it.copy(slots = listOf(ShiftCode.DAY)) },
            changedDays = emptyList(),
            settings = emptyMap(),
        )
    }

    private companion object {
        const val CLOCK = 1_700_000_000_000L
    }
}
