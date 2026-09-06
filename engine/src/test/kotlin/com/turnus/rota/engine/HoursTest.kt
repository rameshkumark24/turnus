package com.turnus.rota.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HoursTest {

    // ------------------------------------------------------------- properties

    /**
     * The whole contract, checked against an independent sum over thousands of
     * random rotas: every working day contributes its shift's duration, every
     * day off contributes nothing, and a shift with no times contributes a
     * shift but no minutes.
     */
    @Test
    fun `totals agree with a day-by-day sum`() {
        val rnd = Random(SEED)
        repeat(5_000) {
            val definitions = randomDefinitions(rnd)
            val days = randomDays(rnd, definitions)

            val total = Hours.total(days, definitions)

            var shifts = 0
            var minutes = 0
            var untimed = 0
            days.forEach { day ->
                val id = day.shiftTypeId ?: return@forEach
                shifts++
                val paid = definitions[id]?.paidMinute
                if (paid == null) untimed++ else minutes += paid
            }

            assertEquals(shifts, total.shifts, "shift count")
            assertEquals(minutes, total.minutes, "minutes")
            assertEquals(untimed, total.untimedShifts, "untimed count")
        }
    }

    /** Days off are free. Adding any number of them moves nothing. */
    @Test
    fun `days off change no total`() {
        val rnd = Random(SEED)
        repeat(1_000) {
            val definitions = randomDefinitions(rnd)
            val days = randomDays(rnd, definitions)
            val before = Hours.total(days, definitions)

            val padded = days + List(rnd.nextInt(1, 20)) {
                ResolvedDay(DayNumber(90_000L + it), shiftTypeId = null, isOverridden = false)
            }

            assertEquals(before, Hours.total(padded, definitions))
        }
    }

    /**
     * Order cannot matter. A month read backwards is the same month, and this
     * is the cheapest guard against an accumulator that depends on sequence.
     */
    @Test
    fun `order does not change the total`() {
        val rnd = Random(SEED)
        repeat(1_000) {
            val definitions = randomDefinitions(rnd)
            val days = randomDays(rnd, definitions)
            assertEquals(
                Hours.total(days, definitions),
                Hours.total(days.reversed(), definitions),
            )
        }
    }

    /** Splitting a range in two and adding the parts gives the whole. */
    @Test
    fun `totals are additive across a split`() {
        val rnd = Random(SEED)
        repeat(1_000) {
            val definitions = randomDefinitions(rnd)
            val days = randomDays(rnd, definitions)
            if (days.isEmpty()) return@repeat
            val at = rnd.nextInt(days.size)

            val whole = Hours.total(days, definitions)
            val left = Hours.total(days.take(at), definitions)
            val right = Hours.total(days.drop(at), definitions)

            assertEquals(whole.shifts, left.shifts + right.shifts)
            assertEquals(whole.minutes, left.minutes + right.minutes)
            assertEquals(whole.untimedShifts, left.untimedShifts + right.untimedShifts)
        }
    }

    // ------------------------------------------------------------------ breaks

    /**
     * The error this exists to stop: half an hour a shift, always in the same
     * direction, is most of a working day a month.
     */
    @Test
    fun `an unpaid break comes off every shift`() {
        val withBreak = ShiftDefinition(
            id = "day",
            code = "D",
            name = "Day",
            startMinute = 7 * 60,
            durationMinute = 12 * 60,
            breakMinutes = 30,
        )
        val days = (0 until 15).map { ResolvedDay(DayNumber(20_000L + it), "day", false) }

        val total = Hours.total(days, mapOf("day" to withBreak))

        assertEquals(15, total.shifts)
        // Twelve hours rostered, eleven and a half counted, fifteen times over.
        assertEquals(15 * (12 * 60 - 30), total.minutes)
        assertEquals(172, total.wholeHours)
        assertEquals(30, total.minutesPastTheHour)
    }

    @Test
    fun `no break leaves the total exactly as it was`() {
        val plain = ShiftDefinition("day", "D", "Day", 7 * 60, 8 * 60)
        val days = (0 until 10).map { ResolvedDay(DayNumber(20_000L + it), "day", false) }
        assertEquals(80 * 60, Hours.total(days, mapOf("day" to plain)).minutes)
    }

    /** The break belongs to the shift, so changing it changes every day at once. */
    @Test
    fun `the total moves by the break times the number of shifts`() {
        val rnd = Random(SEED)
        repeat(500) {
            val duration = rnd.nextInt(2, 1441)
            val breakMinutes = rnd.nextInt(0, duration)
            val count = rnd.nextInt(0, 40)

            val without = ShiftDefinition("s", "S", "S", 0, duration)
            val with = without.copy(breakMinutes = breakMinutes)
            val days = (0 until count).map { ResolvedDay(DayNumber(20_000L + it), "s", false) }

            assertEquals(
                Hours.total(days, mapOf("s" to without)).minutes - breakMinutes * count,
                Hours.total(days, mapOf("s" to with)).minutes,
            )
        }
    }

    @Test
    fun `a break cannot be as long as the shift`() {
        assertFailsWith<IllegalArgumentException> {
            ShiftDefinition("s", "S", "S", 0, 480, breakMinutes = 480)
        }
        assertFailsWith<IllegalArgumentException> {
            ShiftDefinition("s", "S", "S", 0, 480, breakMinutes = 600)
        }
        assertFailsWith<IllegalArgumentException> {
            ShiftDefinition("s", "S", "S", 0, 480, breakMinutes = -1)
        }
    }

    /** A marker has no length to take a break out of. */
    @Test
    fun `a shift with no times cannot have a break`() {
        assertFailsWith<IllegalArgumentException> {
            ShiftDefinition("s", "S", "S", breakMinutes = 30)
        }
    }

    /**
     * The break is unpaid time, not absent time. The shift still runs its full
     * length in the exported calendar, and the reminder still fires before the
     * start of it — only the counting differs.
     */
    @Test
    fun `a break does not change when the shift runs`() {
        val shift = ShiftDefinition("n", "N", "Night", 19 * 60, 12 * 60, breakMinutes = 45)
        assertEquals(12 * 60, shift.durationMinute)
        assertEquals(19 * 60, shift.startMinute)
        assertTrue(shift.crossesMidnight)
        assertEquals(12 * 60 - 45, shift.paidMinute)
    }

    // ------------------------------------------------------------- particulars

    @Test
    fun `an empty range totals nothing and is complete`() {
        val total = Hours.total(emptyList(), emptyMap())
        assertEquals(Hours.Total.NONE, total)
        assertTrue(total.isComplete)
    }

    @Test
    fun `a full month of twelve hour shifts adds up`() {
        val day = ShiftDefinition("day", "D", "Day", startMinute = 7 * 60, durationMinute = 12 * 60)
        val days = (0 until 15).map { ResolvedDay(DayNumber(20_000L + it), "day", false) }

        val total = Hours.total(days, mapOf("day" to day))

        assertEquals(15, total.shifts)
        assertEquals(180 * 60, total.minutes)
        assertEquals(180, total.wholeHours)
        assertEquals(0, total.minutesPastTheHour)
        assertTrue(total.isComplete)
    }

    /** A night shift running past midnight is still one shift of its own length. */
    @Test
    fun `a shift crossing midnight counts once, at its rostered length`() {
        val night = ShiftDefinition("night", "N", "Night", startMinute = 19 * 60, durationMinute = 12 * 60)
        assertTrue(night.crossesMidnight, "the fixture is not testing what it claims")

        val days = (0 until 4).map { ResolvedDay(DayNumber(20_000L + it), "night", false) }
        val total = Hours.total(days, mapOf("night" to night))

        assertEquals(4, total.shifts)
        assertEquals(48 * 60, total.minutes)
    }

    @Test
    fun `half hours survive the split into hours and minutes`() {
        val short = ShiftDefinition("s", "S", "Short", startMinute = 9 * 60, durationMinute = 90)
        val days = (0 until 3).map { ResolvedDay(DayNumber(20_000L + it), "s", false) }

        val total = Hours.total(days, mapOf("s" to short))

        assertEquals(270, total.minutes)
        assertEquals(4, total.wholeHours)
        assertEquals(30, total.minutesPastTheHour)
    }

    /**
     * The case the import feature creates: a shift taken from a workmate's code
     * arrives with no times. The total must say it is short rather than quietly
     * under-reporting the month.
     */
    @Test
    fun `a shift with no times is counted but adds no minutes`() {
        val timed = ShiftDefinition("day", "D", "Day", startMinute = 7 * 60, durationMinute = 8 * 60)
        val marker = ShiftDefinition("relief", "R", "Relief")

        val days = listOf(
            ResolvedDay(DayNumber(20_000), "day", false),
            ResolvedDay(DayNumber(20_001), "relief", false),
            ResolvedDay(DayNumber(20_002), "relief", false),
            ResolvedDay(DayNumber(20_003), null, false),
        )

        val total = Hours.total(days, mapOf("day" to timed, "relief" to marker))

        assertEquals(3, total.shifts)
        assertEquals(8 * 60, total.minutes)
        assertEquals(2, total.untimedShifts)
        assertFalse(total.isComplete, "two shifts have no times, so the total is short")
    }

    /**
     * Unreachable through the repository, which will not store a day naming a
     * shift that does not exist — but the caller is a screen, and being honest
     * about an incomplete total beats crashing on someone's calendar.
     */
    @Test
    fun `a day naming an unknown shift is treated as untimed rather than throwing`() {
        val days = listOf(ResolvedDay(DayNumber(20_000), "ghost", false))
        val total = Hours.total(days, emptyMap())

        assertEquals(1, total.shifts)
        assertEquals(0, total.minutes)
        assertEquals(1, total.untimedShifts)
        assertFalse(total.isComplete)
    }

    /** An overridden day is still just a day with a shift id; the source is irrelevant. */
    @Test
    fun `an overridden day counts as the shift it was changed to`() {
        val day = ShiftDefinition("day", "D", "Day", startMinute = 7 * 60, durationMinute = 8 * 60)
        val night = ShiftDefinition("night", "N", "Night", startMinute = 19 * 60, durationMinute = 12 * 60)
        val definitions = mapOf("day" to day, "night" to night)

        val scheduled = Hours.total(listOf(ResolvedDay(DayNumber(20_000), "day", false)), definitions)
        val swapped = Hours.total(listOf(ResolvedDay(DayNumber(20_000), "night", true)), definitions)

        assertEquals(8 * 60, scheduled.minutes)
        assertEquals(12 * 60, swapped.minutes)
    }

    // ------------------------------------------------------------------ helpers

    private fun randomDefinitions(rnd: Random): Map<String, ShiftDefinition> =
        (0 until rnd.nextInt(1, 6)).associate { index ->
            val id = "shift$index"
            // A third of them are markers with no clock times, so the untimed
            // path is exercised as often as the timed one.
            val timed = rnd.nextInt(3) != 0
            val duration = if (timed) rnd.nextInt(1, 1441) else null
            id to ShiftDefinition(
                id = id,
                code = "S$index",
                name = "Shift $index",
                startMinute = if (timed) rnd.nextInt(0, 1440) else null,
                durationMinute = duration,
                // Often zero, sometimes a real break, never the whole shift.
                breakMinutes = if (duration != null && rnd.nextBoolean()) {
                    rnd.nextInt(0, duration)
                } else {
                    0
                },
            )
        }

    private fun randomDays(
        rnd: Random,
        definitions: Map<String, ShiftDefinition>,
    ): List<ResolvedDay> {
        val ids = definitions.keys.toList()
        return List(rnd.nextInt(0, 60)) { index ->
            ResolvedDay(
                day = DayNumber(20_000L + index),
                // Some days off, and occasionally an id nothing defines.
                shiftTypeId = when (rnd.nextInt(10)) {
                    0, 1, 2 -> null
                    3 -> "unknown-$index"
                    else -> ids[rnd.nextInt(ids.size)]
                },
                isOverridden = rnd.nextBoolean(),
            )
        }
    }

    private companion object {
        const val SEED = 987_654_321
    }
}
