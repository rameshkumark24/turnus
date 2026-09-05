package com.turnus.rota.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutlookTest {

    // ------------------------------------------------------------- properties

    /**
     * The defining invariant: the run reported must actually be a run. Every
     * day inside it shares the reference day's working state, and the days
     * immediately outside it do not.
     */
    @Test
    fun `the current stretch is exactly the unbroken run around the day`() {
        forEachCase { pattern, overrides, day ->
            val summary = Outlook.summarise(pattern, overrides, day)
            val current = summary.current
            if (!current.complete) return@forEachCase

            assertTrue(current.start <= day, "run starts after the day it contains")
            assertTrue(day <= current.endInclusive, "run ends before the day it contains")

            for (offset in 0 until current.length) {
                val inside = current.start + offset.toLong()
                assertEquals(
                    current.isWorking,
                    ShiftEngine.resolve(pattern, overrides, inside) != null,
                    "day $inside inside the run disagrees about working",
                )
            }
            assertEquals(
                !current.isWorking,
                ShiftEngine.resolve(pattern, overrides, current.start - 1) != null,
                "the day before the run has the same state, so the run started earlier",
            )
            assertEquals(
                !current.isWorking,
                ShiftEngine.resolve(pattern, overrides, current.endInclusive + 1) != null,
                "the day after the run has the same state, so the run ran on",
            )
        }
    }

    /** The next run has to begin the day the current one ends, and be its opposite. */
    @Test
    fun `the next stretch abuts the current one and flips state`() {
        forEachCase { pattern, overrides, day ->
            val summary = Outlook.summarise(pattern, overrides, day)
            val next = summary.next ?: return@forEachCase

            assertEquals(summary.current.endInclusive + 1, next.start)
            assertEquals(!summary.current.isWorking, next.isWorking)
            assertEquals(
                next.isWorking,
                ShiftEngine.resolve(pattern, overrides, next.start) != null,
            )
        }
    }

    @Test
    fun `position falls inside the run and counts from its start`() {
        forEachCase { pattern, overrides, day ->
            val summary = Outlook.summarise(pattern, overrides, day)
            val position = summary.position ?: return@forEachCase

            assertEquals((day.daysSince(summary.current.start) + 1).toInt(), position)
            assertTrue(position >= 1, "position $position is not 1-based")
            assertTrue(
                position <= summary.current.length,
                "position $position past run of ${summary.current.length}",
            )
        }
    }

    /** `mixed` is what stops a caller labelling "two days then two nights" as "Day". */
    @Test
    fun `mixed is set exactly when the run holds more than one shift`() {
        forEachCase { pattern, overrides, day ->
            val current = Outlook.summarise(pattern, overrides, day).current
            if (!current.complete) return@forEachCase

            val shifts = (0 until current.length).map {
                ShiftEngine.resolve(pattern, overrides, current.start + it.toLong())
            }
            assertEquals(shifts.distinct().size > 1, current.mixed, "mixed disagrees with $shifts")
            assertEquals(shifts.first(), current.shiftTypeId, "shiftTypeId is not the run's first day")
        }
    }

    /** Two ways of asking the same question must not disagree. */
    @Test
    fun `agrees with nextWorkingDay when the day is off`() {
        forEachCase { pattern, overrides, day ->
            if (ShiftEngine.resolve(pattern, overrides, day) != null) return@forEachCase
            val next = Outlook.summarise(pattern, overrides, day).next ?: return@forEachCase

            assertEquals(
                ShiftEngine.nextWorkingDay(pattern, overrides, day),
                next.start,
                "the next working stretch does not start on the next working day",
            )
        }
    }

    // -------------------------------------------------------------- behaviour

    @Test
    fun `reads a plain four on four off`() {
        val pattern = Pattern(
            id = "p",
            name = "4 on 4 off",
            anchor = DayNumber.of(2026, 9, 1),
            slots = Presets.FOUR_ON_FOUR_OFF.slots,
        )

        // 3 Sep is the third of the four on.
        val summary = Outlook.summarise(pattern, Overrides.EMPTY, DayNumber.of(2026, 9, 3))

        assertEquals(DayNumber.of(2026, 9, 1), summary.current.start)
        assertEquals(4, summary.current.length)
        assertEquals(3, summary.position)
        assertTrue(summary.current.isWorking)
        assertEquals(false, summary.current.mixed)

        val next = requireNotNull(summary.next)
        assertEquals(DayNumber.of(2026, 9, 5), next.start)
        assertEquals(4, next.length)
        assertEquals(false, next.isWorking)
        assertEquals(2, summary.daysUntilNext)
    }

    @Test
    fun `a days-then-nights run is one run and is marked mixed`() {
        val pattern = Pattern(
            id = "p",
            name = "2 days 2 nights 4 off",
            anchor = DayNumber.of(2026, 9, 1),
            slots = listOf("d", "d", "n", "n", null, null, null, null),
        )

        val summary = Outlook.summarise(pattern, Overrides.EMPTY, DayNumber.of(2026, 9, 2))

        assertEquals(4, summary.current.length, "days and nights back to back are one run")
        assertTrue(summary.current.mixed)
        assertEquals("d", summary.current.shiftTypeId, "the run's first day is a day shift")
    }

    /**
     * The reason this works in stretches rather than cycle positions: an
     * overtime shift in the middle of a break really does shorten the break,
     * and saying "four days off" then would be a comfortable lie.
     */
    @Test
    fun `an overtime shift splits a break in two`() {
        val pattern = Pattern(
            id = "p",
            name = "4 on 4 off",
            anchor = DayNumber.of(2026, 9, 1),
            slots = Presets.FOUR_ON_FOUR_OFF.slots,
        )
        val overtime = DayNumber.of(2026, 9, 7)
        val overrides = Overrides.EMPTY.with(overtime, ShiftCode.DAY)

        val summary = Outlook.summarise(pattern, overrides, DayNumber.of(2026, 9, 5))

        assertEquals(2, summary.current.length, "the break now runs 5-6 Sep, not 5-8")
        assertEquals(1, summary.position)
        assertEquals(overtime, requireNotNull(summary.next).start)
        assertEquals(1, summary.next?.length, "the overtime day stands alone")
    }

    /** A day taken off in the middle of a run breaks it, the same way round. */
    @Test
    fun `a day taken off splits a run of shifts`() {
        val pattern = Pattern(
            id = "p",
            name = "4 on 4 off",
            anchor = DayNumber.of(2026, 9, 1),
            slots = Presets.FOUR_ON_FOUR_OFF.slots,
        )
        // Present-but-null: explicitly off, not "no entry".
        val overrides = Overrides.EMPTY.with(DayNumber.of(2026, 9, 3), null)

        val summary = Outlook.summarise(pattern, overrides, DayNumber.of(2026, 9, 1))

        assertEquals(2, summary.current.length)
        assertEquals(DayNumber.of(2026, 9, 3), requireNotNull(summary.next).start)
        assertEquals(1, summary.next?.length)
        assertEquals(false, summary.next?.isWorking)
    }

    @Test
    fun `works before the anchor date`() {
        val pattern = Pattern(
            id = "p",
            name = "4 on 4 off",
            anchor = DayNumber.of(2026, 9, 1),
            slots = Presets.FOUR_ON_FOUR_OFF.slots,
        )

        // 30 Aug is slot floorMod(-2, 8) = 6, an off day, second of that break.
        val summary = Outlook.summarise(pattern, Overrides.EMPTY, DayNumber.of(2026, 8, 30))

        assertEquals(false, summary.current.isWorking)
        assertEquals(DayNumber.of(2026, 8, 28), summary.current.start)
        assertEquals(3, summary.position)
        assertEquals(DayNumber.of(2026, 9, 1), requireNotNull(summary.next).start)
    }

    // ------------------------------------------------------------- degenerate

    /** A rota with no day off never ends, so there is nothing truthful to report. */
    @Test
    fun `a rota with no days off reports an incomplete run and no next`() {
        val pattern = Pattern("p", "always on", DayNumber(0), listOf("d"))

        val summary = Outlook.summarise(pattern, Overrides.EMPTY, DayNumber(100), horizonDays = 30)

        assertEquals(false, summary.current.complete)
        assertNull(summary.next)
        assertNull(summary.position)
    }

    @Test
    fun `a rota with no working days reports an incomplete off run`() {
        val pattern = Pattern("p", "never on", DayNumber(0), listOf(null))

        val summary = Outlook.summarise(pattern, Overrides.EMPTY, DayNumber(100), horizonDays = 30)

        assertEquals(false, summary.current.isWorking)
        assertEquals(false, summary.current.complete)
        assertNull(summary.next)
    }

    @Test
    fun `rejects a non-positive horizon`() {
        val pattern = Pattern("p", "p", DayNumber(0), listOf("d", null))
        assertFailsWith<IllegalArgumentException> {
            Outlook.summarise(pattern, Overrides.EMPTY, DayNumber(0), horizonDays = 0)
        }
    }

    // ----------------------------------------------------------- longest break

    @Test
    fun `finds the longest run of days off`() {
        val pattern = Pattern(
            "p", "4 on 4 off", DayNumber.of(2026, 9, 1),
            Presets.FOUR_ON_FOUR_OFF.slots,
        )
        val days = ShiftEngine.resolveRange(
            pattern, Overrides.EMPTY,
            DayNumber.of(2026, 9, 1), DayNumber.of(2026, 9, 30),
        )

        val best = requireNotNull(Outlook.longestBreak(days))

        assertEquals(4, best.length)
        assertEquals(DayNumber.of(2026, 9, 5), best.start, "the first four-day break")
        assertEquals(false, best.isWorking)
        assertTrue(best.complete)
    }

    /** A break made longer by booked leave is exactly what this is for. */
    @Test
    fun `overrides can create the longest break`() {
        val pattern = Pattern(
            "p", "4 on 4 off", DayNumber.of(2026, 9, 1),
            Presets.FOUR_ON_FOUR_OFF.slots,
        )
        // 5-8 and 13-16 are already off; 9-12 is the block of shifts between
        // them. Booking those four off joins the two breaks into one.
        val overrides = (9..12).fold(Overrides.EMPTY) { acc, d ->
            acc.with(DayNumber.of(2026, 9, d), null)
        }
        val days = ShiftEngine.resolveRange(
            pattern, overrides,
            DayNumber.of(2026, 9, 1), DayNumber.of(2026, 9, 30),
        )

        val best = requireNotNull(Outlook.longestBreak(days))

        assertEquals(12, best.length, "5-16 Sep becomes one twelve-day break")
        assertEquals(DayNumber.of(2026, 9, 5), best.start)
    }

    /** A run touching the edge of the window may continue beyond it. */
    @Test
    fun `a break at the edge of the window is not complete`() {
        val pattern = Pattern("p", "all off", DayNumber(0), listOf(null))
        val days = ShiftEngine.resolveRange(
            pattern, Overrides.EMPTY, DayNumber(100), DayNumber(110),
        )

        val best = requireNotNull(Outlook.longestBreak(days))

        assertEquals(11, best.length)
        assertEquals(false, best.complete, "length is only a lower bound here")
    }

    @Test
    fun `a window with no days off has no break`() {
        val pattern = Pattern("p", "always on", DayNumber(0), listOf("d"))
        val days = ShiftEngine.resolveRange(
            pattern, Overrides.EMPTY, DayNumber(100), DayNumber(110),
        )

        assertNull(Outlook.longestBreak(days))
        assertNull(Outlook.longestBreak(emptyList()))
    }

    /** Whatever the rota, the reported break must really be all days off. */
    @Test
    fun `the reported break is genuinely unbroken and maximal`() {
        forEachCase { pattern, overrides, day ->
            val days = ShiftEngine.resolveRange(pattern, overrides, day, day + 120)
            val best = Outlook.longestBreak(days) ?: return@forEachCase

            val index = (best.start.value - day.value).toInt()
            (0 until best.length).forEach { offset ->
                assertTrue(!days[index + offset].isWorking, "a working day inside the break")
            }
            if (index > 0) assertTrue(days[index - 1].isWorking, "the break started earlier")
            val after = index + best.length
            if (after < days.size) assertTrue(days[after].isWorking, "the break ran on")

            // No longer run exists anywhere in the window.
            var run = 0
            var longest = 0
            days.forEach { if (it.isWorking) run = 0 else { run++; if (run > longest) longest = run } }
            assertEquals(longest, best.length)
        }
    }

    // ----------------------------------------------------------------- driver

    private fun forEachCase(
        cases: Int = 20_000,
        check: (Pattern, Overrides, DayNumber) -> Unit,
    ) {
        val rnd = Random(SEED)
        repeat(cases) {
            val slots = List(rnd.nextInt(1, 29)) {
                if (rnd.nextBoolean()) null else "shift${rnd.nextInt(0, 3)}"
            }
            val anchor = DayNumber(rnd.nextLong(-20_000, 20_000))
            val day = anchor + rnd.nextLong(-500, 500)
            val pattern = Pattern("p", "random", anchor, slots)

            // Overrides clustered near the day under test, so they actually
            // interact with the run being measured instead of landing years off.
            val overrides = (0 until rnd.nextInt(0, 6)).fold(Overrides.EMPTY) { acc, _ ->
                acc.with(
                    day + rnd.nextLong(-20, 20),
                    if (rnd.nextBoolean()) null else "shift${rnd.nextInt(0, 3)}",
                )
            }

            check(pattern, overrides, day)
        }
    }

    private companion object {
        const val SEED = 20260905L
    }
}
