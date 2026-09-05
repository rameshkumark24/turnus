package com.turnus.rota.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.Pattern
import com.turnus.rota.engine.ShiftCode
import com.turnus.rota.engine.ShiftEngine
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
 * The repository's three invariants, exercised against real SQLite.
 *
 * They live in Kotlin rather than in the schema because SQLite cannot express
 * them: `slots` is a delimited text column, so no foreign key can police the
 * ids inside it, and "at most one active pattern" is not a constraint SQLite
 * offers. Until now that reasoning was only a comment. These are instrumented
 * rather than Robolectric tests because the thing being checked *is* the
 * database — transactions, foreign keys, unique indexes — and a simulated one
 * would be checking the simulation.
 */
@RunWith(AndroidJUnit4::class)
class RotaRepositoryTest {

    private lateinit var db: TurnusDatabase
    private lateinit var repository: RotaRepository
    private var ids = 0

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TurnusDatabase::class.java)
            // Foreign keys are the point of several of these tests, and Room
            // only enforces them on a connection that has them switched on.
            .build()
        repository = RotaRepository(db, now = { CLOCK }, newId = { "id${ids++}" })
        runBlocking { repository.seedDefaultsIfEmpty() }
    }

    @After
    fun close() = db.close()

    // ------------------------------------------------------------- invariant 1

    @Test
    fun slotCountAlwaysMatchesTheStoredSlots() = runBlocking {
        listOf(
            listOf(ShiftCode.DAY),
            List(28) { if (it % 4 < 2) ShiftCode.NIGHT else null },
            listOf(null, ShiftCode.EARLY, null, ShiftCode.LATE, ShiftCode.DAY),
        ).forEachIndexed { index, slots ->
            repository.saveActivePattern(pattern(id = "p$index", slots = slots))

            val row = db.patternDao().getActive()!!
            assertEquals(
                "slot_count disagrees with the decoded slots",
                SlotCodec.decode(row.slots).size,
                row.slotCount,
            )
            assertEquals(slots.size, row.slotCount)
        }
    }

    // ------------------------------------------------------------- invariant 2

    @Test
    fun aPatternCannotReferenceAShiftTypeThatDoesNotExist() = runBlocking {
        val failure = assertThrows(UnknownShiftTypeException::class.java) {
            runBlocking {
                repository.saveActivePattern(
                    pattern(slots = listOf(ShiftCode.DAY, "no-such-shift")),
                )
            }
        }

        assertEquals(listOf("no-such-shift"), failure.ids)
        assertNull("the bad pattern must not have been written", db.patternDao().getActive())
    }

    @Test
    fun anOverrideCannotReferenceAShiftTypeThatDoesNotExist() = runBlocking {
        repository.saveActivePattern(pattern())

        assertThrows(UnknownShiftTypeException::class.java) {
            runBlocking { repository.setOverride(DayNumber.of(2026, 9, 5), "no-such-shift") }
        }
        assertNull(repository.overrideFor(DayNumber.of(2026, 9, 5)))
    }

    /**
     * The reason invariant 2 is enforced in Kotlin: a shift id inside `slots`
     * is text, so deleting the row it names would leave the pattern rendering
     * blank cells with nothing to explain them.
     */
    @Test
    fun aShiftTypeInUseByAPatternCannotBeDeleted() = runBlocking {
        repository.saveActivePattern(pattern(name = "My rota", slots = listOf(ShiftCode.DAY, null)))

        val failure = assertThrows(ShiftTypeInUseException::class.java) {
            runBlocking { repository.deleteShiftType(ShiftCode.DAY) }
        }

        assertEquals(listOf("My rota"), failure.usedByPatterns)
        assertNotNull(db.shiftTypeDao().getById(ShiftCode.DAY))
    }

    @Test
    fun aShiftTypeInUseByAnOverrideCannotBeDeleted() = runBlocking {
        repository.saveActivePattern(pattern(slots = listOf(ShiftCode.DAY, null)))
        repository.setOverride(DayNumber.of(2026, 9, 5), ShiftCode.NIGHT)

        val failure = assertThrows(ShiftTypeInUseException::class.java) {
            runBlocking { repository.deleteShiftType(ShiftCode.NIGHT) }
        }

        assertEquals(1, failure.usedByOverrideCount)
        assertTrue(failure.usedByPatterns.isEmpty())
    }

    @Test
    fun anUnusedShiftTypeCanBeDeleted() = runBlocking {
        val id = repository.createShiftType(code = "T", name = "Training", color = 0xFF00FF00.toInt())

        repository.deleteShiftType(id)

        assertNull(db.shiftTypeDao().getById(id))
    }

    // ------------------------------------------------------------- invariant 3

    @Test
    fun onlyOnePatternIsEverActive() = runBlocking {
        repeat(4) { index -> repository.saveActivePattern(pattern(id = "p$index")) }

        val active = db.patternDao().getAll().filter { it.isActive }

        assertEquals("more than one pattern was left active", 1, active.size)
        assertEquals("p3", active.single().id)
        assertEquals("the earlier patterns must be kept, not deleted", 4, db.patternDao().getAll().size)
    }

    // ------------------------------------------------------------------ seeding

    /**
     * Startup calls this from more than one place. Without the transactional
     * check-then-insert, the second caller's INSERT hits the unique code index
     * and takes the process down on first launch.
     */
    @Test
    fun seedingTwiceDoesNotDuplicateOrThrow() = runBlocking {
        val afterFirst = db.shiftTypeDao().getAll().size

        repeat(3) { repository.seedDefaultsIfEmpty() }

        assertEquals(afterFirst, db.shiftTypeDao().getAll().size)
        assertEquals(4, afterFirst)
    }

    @Test
    fun newShiftTypesTakeTheNextSortOrderEvenAfterADeletion() = runBlocking {
        val first = repository.createShiftType("T", "Training", 0xFF00FF00.toInt())
        repository.deleteShiftType(first)

        val second = repository.createShiftType("R", "Relief", 0xFF0000FF.toInt())

        val orders = db.shiftTypeDao().getAll().map { it.sortOrder }
        assertEquals("sort order was reused after a deletion", orders.distinct().size, orders.size)
        assertNotNull(db.shiftTypeDao().getById(second))
    }

    // ---------------------------------------------------------------- overrides

    /**
     * A present-but-null override means "I took this working day off", which is
     * not the same as having no row. Collapsing the two would silently undo the
     * user's edit on the next read.
     */
    @Test
    fun anExplicitDayOffIsNotTheSameAsNoOverride() = runBlocking {
        repository.saveActivePattern(pattern(slots = listOf(ShiftCode.DAY)))
        val day = DayNumber.of(2026, 9, 5)

        repository.setOverride(day, null)

        val stored = repository.overrideFor(day)
        assertNotNull("the row must exist", stored)
        assertNull(stored!!.shiftTypeId)
        assertTrue(stored.overridesShift)
        assertNull("the calendar must show the day as off", resolved(day))

        repository.clearOverride(day)
        assertNull(repository.overrideFor(day))
        assertEquals("the day falls back to the pattern", ShiftCode.DAY, resolved(day))
    }

    /**
     * A note must never pin the day's shift: someone who annotates a fortnight
     * and then corrects a rota running a day out would otherwise find every
     * annotated day stranded on its old shift.
     */
    @Test
    fun aNoteDoesNotPinTheShift() = runBlocking {
        repository.saveActivePattern(pattern(slots = listOf(ShiftCode.DAY, null)))
        val day = DayNumber.of(2026, 9, 5)
        val before = resolved(day)

        repository.setNote(day, "Swapped with Priya")

        val stored = repository.overrideFor(day)!!
        assertEquals("Swapped with Priya", stored.note)
        assertTrue("a note-only row must not override the shift", !stored.overridesShift)

        repository.shiftActivePatternBy(1)
        assertEquals(
            "the annotated day should have moved with the rota",
            resolvedFromPattern(day),
            resolved(day),
        )
        assertTrue(resolved(day) != before || before == null)
    }

    @Test
    fun aNoteOnAnOverriddenDayKeepsTheOverride() = runBlocking {
        repository.saveActivePattern(pattern(slots = listOf(ShiftCode.DAY, null)))
        val day = DayNumber.of(2026, 9, 5)
        repository.setOverride(day, ShiftCode.NIGHT)

        repository.setNote(day, "Overtime")

        val stored = repository.overrideFor(day)!!
        assertEquals(ShiftCode.NIGHT, stored.shiftTypeId)
        assertTrue(stored.overridesShift)
        assertEquals("Overtime", stored.note)
    }

    @Test
    fun clearingANoteOnAnUnchangedDayRemovesTheRowEntirely() = runBlocking {
        repository.saveActivePattern(pattern(slots = listOf(ShiftCode.DAY, null)))
        val day = DayNumber.of(2026, 9, 5)
        repository.setNote(day, "Something")

        repository.setNote(day, "   ")

        assertNull("a row carrying no information must not be kept", repository.overrideFor(day))
    }

    /** Deleting a pattern must take its exceptions with it — CASCADE, not orphans. */
    @Test
    fun overridesAreScopedToTheirPattern() = runBlocking {
        repository.saveActivePattern(pattern(id = "first", slots = listOf(ShiftCode.DAY)))
        val day = DayNumber.of(2026, 9, 5)
        repository.setOverride(day, ShiftCode.NIGHT)

        repository.saveActivePattern(pattern(id = "second", slots = listOf(ShiftCode.DAY)))

        assertNull("an override belongs to the pattern it was made against", repository.overrideFor(day))
    }

    // ------------------------------------------------------------------- anchor

    /** The "my rota is off by a day" fix: one field, no data migration. */
    @Test
    fun shiftingThePatternMovesEveryDayWithoutTouchingOverrides() = runBlocking {
        repository.saveActivePattern(
            pattern(anchor = DayNumber.of(2026, 9, 1), slots = listOf(ShiftCode.DAY, null)),
        )
        val day = DayNumber.of(2026, 9, 5)
        val overridden = DayNumber.of(2026, 9, 9)
        repository.setOverride(overridden, ShiftCode.NIGHT)
        val before = resolved(day)

        repository.shiftActivePatternBy(1)

        assertEquals(DayNumber.of(2026, 9, 2), repository.activePattern()!!.anchor)
        assertTrue("every generated day should have moved", resolved(day) != before)
        assertEquals(
            "an exception is pinned to its date and must not move",
            ShiftCode.NIGHT,
            resolved(overridden),
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun pattern(
        id: String = "p",
        name: String = "Test rota",
        anchor: DayNumber = DayNumber.of(2026, 9, 1),
        slots: List<String?> = listOf(ShiftCode.DAY, ShiftCode.DAY, null, null),
    ) = Pattern(id = id, name = name, anchor = anchor, slots = slots)

    private suspend fun resolved(day: DayNumber): String? =
        repository.observeCalendar(day, day).first().single().shiftTypeId

    private suspend fun resolvedFromPattern(day: DayNumber): String? =
        ShiftEngine.scheduled(repository.activePattern()!!, day)

    private companion object {
        const val CLOCK = 1_770_000_000_000L
    }
}
