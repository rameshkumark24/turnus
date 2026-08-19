package com.turnus.rota.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShiftEngineTest {

    // ---------------------------------------------------------------- regression

    /**
     * The floorMod bug, pinned as an example so it can never come back.
     *
     * With Kotlin's `%` this returns -1 and throws IndexOutOfBounds. Anchors are
     * routinely set in the future ("the first day of my next cycle"), so the very
     * first thing such a user does — scroll back a month — hits this path.
     */
    @Test
    fun `resolves days before the anchor without going out of bounds`() {
        val pattern = Pattern(
            id = "p",
            name = "4 on 4 off",
            anchor = DayNumber.of(2026, 9, 1),
            slots = Presets.FOUR_ON_FOUR_OFF.slots,
        )

        assertEquals(7, ShiftEngine.slotIndex(pattern, DayNumber.of(2026, 8, 31)))
        assertEquals(0, ShiftEngine.slotIndex(pattern, DayNumber.of(2026, 8, 24)))
        assertEquals(1, ShiftEngine.slotIndex(pattern, DayNumber.of(2026, 8, 25)))

        // A full year before the anchor must still resolve.
        val longBefore = DayNumber.of(2025, 9, 1)
        assertTrue(ShiftEngine.slotIndex(pattern, longBefore) in pattern.slots.indices)
    }

    // ---------------------------------------------------------------- invariants

    /** Invariant 1: the cycle repeats with period `cycleLength`. */
    @Test
    fun `slot index is periodic in the cycle length`() {
        forEachRandomCase { pattern, day ->
            val period = pattern.cycleLength.toLong()
            assertEquals(
                ShiftEngine.slotIndex(pattern, day),
                ShiftEngine.slotIndex(pattern, day + period),
                "not periodic at $day for cycle ${pattern.cycleLength}",
            )
            assertEquals(
                ShiftEngine.slotIndex(pattern, day),
                ShiftEngine.slotIndex(pattern, day - period * 37),
                "not periodic backwards at $day",
            )
        }
    }

    /** Invariant 2: the anchor is slot zero, by definition. */
    @Test
    fun `anchor maps to slot zero`() {
        val rnd = Random(SEED)
        repeat(5_000) {
            val pattern = randomPattern(rnd)
            assertEquals(0, ShiftEngine.slotIndex(pattern, pattern.anchor))
            assertEquals(pattern.slots[0], ShiftEngine.scheduled(pattern, pattern.anchor))
        }
    }

    /** Invariant 3: the index is always in range, for any date, forwards or back. */
    @Test
    fun `slot index is always within bounds`() {
        forEachRandomCase { pattern, day ->
            val index = ShiftEngine.slotIndex(pattern, day)
            assertTrue(
                index in pattern.slots.indices,
                "index $index out of bounds for cycle ${pattern.cycleLength} at $day",
            )
        }
    }

    /** Resolution is deterministic — same inputs, same answer, always. */
    @Test
    fun `resolution is deterministic`() {
        forEachRandomCase { pattern, day ->
            assertEquals(
                ShiftEngine.scheduled(pattern, day),
                ShiftEngine.scheduled(pattern, day),
            )
        }
    }

    // ---------------------------------------------------------------- overrides

    @Test
    fun `an override replaces the scheduled shift`() {
        val pattern = fourOnFourOff(anchor = DayNumber.of(2026, 1, 1))
        val workingDay = DayNumber.of(2026, 1, 1)
        assertEquals(ShiftCode.DAY, ShiftEngine.scheduled(pattern, workingDay))

        val overrides = Overrides.EMPTY.with(workingDay, ShiftCode.NIGHT)
        assertEquals(ShiftCode.NIGHT, ShiftEngine.resolve(pattern, overrides, workingDay))
    }

    /**
     * The distinction a bare map cannot express: an entry present with a null
     * value means "I took this working day off", and must not fall back to the
     * generated pattern.
     */
    @Test
    fun `an explicit off override is not the same as no override`() {
        val pattern = fourOnFourOff(anchor = DayNumber.of(2026, 1, 1))
        val workingDay = DayNumber.of(2026, 1, 1)

        val takenOff = Overrides.EMPTY.with(workingDay, null)
        assertTrue(workingDay in takenOff)
        assertNull(ShiftEngine.resolve(pattern, takenOff, workingDay))

        val untouched = Overrides.EMPTY
        assertTrue(workingDay !in untouched)
        assertEquals(ShiftCode.DAY, ShiftEngine.resolve(pattern, untouched, workingDay))
    }

    @Test
    fun `overrides never leak onto neighbouring days`() {
        val pattern = fourOnFourOff(anchor = DayNumber.of(2026, 1, 1))
        val day = DayNumber.of(2026, 1, 10)
        val overrides = Overrides.EMPTY.with(day, ShiftCode.LATE)

        assertEquals(
            ShiftEngine.scheduled(pattern, day - 1),
            ShiftEngine.resolve(pattern, overrides, day - 1),
        )
        assertEquals(
            ShiftEngine.scheduled(pattern, day + 1),
            ShiftEngine.resolve(pattern, overrides, day + 1),
        )
    }

    @Test
    fun `removing an override restores the generated shift`() {
        val pattern = fourOnFourOff(anchor = DayNumber.of(2026, 1, 1))
        val day = DayNumber.of(2026, 1, 2)
        val overrides = Overrides.EMPTY.with(day, ShiftCode.NIGHT).without(day)
        assertEquals(ShiftEngine.scheduled(pattern, day), ShiftEngine.resolve(pattern, overrides, day))
    }

    // ---------------------------------------------------------------- ranges

    @Test
    fun `resolveRange is contiguous and correctly sized`() {
        val pattern = fourOnFourOff(anchor = DayNumber.of(2026, 1, 1))
        val from = DayNumber.of(2026, 1, 1)
        val to = DayNumber.of(2026, 12, 31)

        val days = ShiftEngine.resolveRange(pattern, Overrides.EMPTY, from, to)

        assertEquals(365, days.size)
        assertEquals(from, days.first().day)
        assertEquals(to, days.last().day)
        days.zipWithNext { a, b ->
            assertEquals(1L, b.day.daysSince(a.day), "gap between ${a.day} and ${b.day}")
        }
    }

    @Test
    fun `resolveRange agrees with resolve for every day`() {
        val rnd = Random(SEED)
        repeat(200) {
            val pattern = randomPattern(rnd)
            val from = DayNumber(rnd.nextLong(-10_000, 10_000))
            val to = from + rnd.nextLong(0, 400)
            ShiftEngine.resolveRange(pattern, Overrides.EMPTY, from, to).forEach { resolved ->
                assertEquals(
                    ShiftEngine.resolve(pattern, Overrides.EMPTY, resolved.day),
                    resolved.shiftTypeId,
                )
            }
        }
    }

    @Test
    fun `resolveRange rejects a reversed range`() {
        val pattern = fourOnFourOff(anchor = DayNumber.of(2026, 1, 1))
        assertFailsWith<IllegalArgumentException> {
            ShiftEngine.resolveRange(
                pattern,
                Overrides.EMPTY,
                DayNumber.of(2026, 2, 1),
                DayNumber.of(2026, 1, 1),
            )
        }
    }

    // ---------------------------------------------------------------- next shift

    @Test
    fun `finds the next working day`() {
        val pattern = fourOnFourOff(anchor = DayNumber.of(2026, 1, 1))
        // Days 1-4 work, 5-8 off, 9 works again.
        val firstOffDay = DayNumber.of(2026, 1, 5)
        assertEquals(
            DayNumber.of(2026, 1, 9),
            ShiftEngine.nextWorkingDay(pattern, Overrides.EMPTY, firstOffDay),
        )
        // Already working today: returns today.
        val working = DayNumber.of(2026, 1, 2)
        assertEquals(working, ShiftEngine.nextWorkingDay(pattern, Overrides.EMPTY, working))
    }

    @Test
    fun `returns null when a pattern has no working days`() {
        val allOff = Pattern("p", "leave", DayNumber.of(2026, 1, 1), listOf(null, null, null))
        assertNull(ShiftEngine.nextWorkingDay(allOff, Overrides.EMPTY, DayNumber.of(2026, 1, 1)))
    }

    @Test
    fun `an override can create a working day inside an all-off pattern`() {
        val allOff = Pattern("p", "leave", DayNumber.of(2026, 1, 1), listOf(null, null, null))
        val day = DayNumber.of(2026, 1, 15)
        val overrides = Overrides.EMPTY.with(day, ShiftCode.DAY)
        assertEquals(day, ShiftEngine.nextWorkingDay(allOff, overrides, DayNumber.of(2026, 1, 1)))
    }

    // ---------------------------------------------------------------- shifting

    /** "My rota moved by a day" must be a one-field edit, not a data migration. */
    @Test
    fun `shifting a pattern moves every day by the same amount`() {
        val pattern = fourOnFourOff(anchor = DayNumber.of(2026, 1, 1))
        val shifted = pattern.shiftedBy(1)
        val rnd = Random(SEED)
        repeat(2_000) {
            val day = DayNumber(rnd.nextLong(-5_000, 5_000))
            assertEquals(
                ShiftEngine.scheduled(pattern, day),
                ShiftEngine.scheduled(shifted, day + 1),
            )
        }
    }

    // ---------------------------------------------------------------- guards

    @Test
    fun `a pattern must have at least one slot`() {
        assertFailsWith<IllegalArgumentException> {
            Pattern("p", "empty", DayNumber.of(2026, 1, 1), emptyList())
        }
    }

    @Test
    fun `a single-slot pattern is valid and constant`() {
        val always = Pattern("p", "every day", DayNumber.of(2026, 1, 1), listOf(ShiftCode.DAY))
        val rnd = Random(SEED)
        repeat(1_000) {
            val day = DayNumber(rnd.nextLong(-40_000, 40_000))
            assertEquals(ShiftCode.DAY, ShiftEngine.scheduled(always, day))
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun fourOnFourOff(anchor: DayNumber) = Pattern(
        id = "p",
        name = "4 on, 4 off",
        anchor = anchor,
        slots = Presets.FOUR_ON_FOUR_OFF.slots,
    )

    private fun randomPattern(rnd: Random): Pattern {
        val length = rnd.nextInt(1, 41)
        return Pattern(
            id = "p$length",
            name = "random",
            anchor = DayNumber(rnd.nextLong(-20_000, 20_000)),
            slots = List(length) { if (rnd.nextBoolean()) null else "shift${rnd.nextInt(0, 4)}" },
        )
    }

    /** Runs [check] over a wide spread of random patterns and dates. */
    private fun forEachRandomCase(check: (Pattern, DayNumber) -> Unit) {
        val rnd = Random(SEED)
        repeat(CASES) {
            val pattern = randomPattern(rnd)
            // Deliberately spans dates long before and long after the anchor.
            val day = DayNumber(rnd.nextLong(-40_000, 40_000))
            check(pattern, day)
        }
    }

    private companion object {
        const val SEED = 20260820L
        const val CASES = 20_000
    }
}
