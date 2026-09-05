package com.turnus.rota.engine

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RemindersTest {

    // ------------------------------------------------------------- properties

    /**
     * A reminder must exist for exactly the days the user works, minus the ones
     * they muted. Too few and someone misses a shift; too many and they mute
     * the app, which is the same outcome one step later.
     */
    @Test
    fun `plans exactly the working days that are not muted`() {
        forEachCase { pattern, overrides, definitions, settings, from, days ->
            val plan = Reminders.plan(pattern, overrides, definitions, settings, from, days)

            val expected = (0 until days)
                .map { from + it.toLong() }
                .mapNotNull { day -> ShiftEngine.resolve(pattern, overrides, day)?.let { day to it } }
                .filter { (_, shift) -> shift !in settings.mutedShiftTypeIds }
                .filter { (_, shift) -> shift in definitions }

            assertEquals(expected.size, plan.size, "wrong number of reminders")
            assertEquals(expected.map { it.first }, plan.map { it.day })
            assertEquals(expected.map { it.second }, plan.map { it.shiftTypeId })
        }
    }

    /** The whole promise of the feature: fire exactly the requested lead ahead. */
    @Test
    fun `a timed shift fires exactly the lead time before it starts`() {
        forEachCase { pattern, overrides, definitions, settings, from, days ->
            Reminders.plan(pattern, overrides, definitions, settings, from, days).forEach { reminder ->
                val definition = definitions.getValue(reminder.shiftTypeId)
                if (!definition.isTimed) return@forEach

                val start = LocalDateTime.of(reminder.day.toLocalDate(), java.time.LocalTime.MIDNIGHT)
                    .plusMinutes(definition.startMinute!!.toLong())

                assertEquals(
                    settings.leadMinutes.toLong(),
                    Duration.between(reminder.at, start).toMinutes(),
                    "reminder for ${reminder.day} is not ${settings.leadMinutes} minutes before $start",
                )
            }
        }
    }

    @Test
    fun `an all-day shift gives notice the evening before`() {
        forEachCase { pattern, overrides, definitions, settings, from, days ->
            Reminders.plan(pattern, overrides, definitions, settings, from, days).forEach { reminder ->
                if (definitions.getValue(reminder.shiftTypeId).isTimed) return@forEach

                assertEquals(
                    reminder.day.toLocalDate().minusDays(1),
                    reminder.at.toLocalDate(),
                    "an all-day notice must land on the day before the shift",
                )
                assertEquals(
                    settings.allDayNoticeMinute,
                    reminder.at.toLocalTime().toSecondOfDay() / 60,
                )
            }
        }
    }

    @Test
    fun `reminders come out in order`() {
        forEachCase { pattern, overrides, definitions, settings, from, days ->
            val plan = Reminders.plan(pattern, overrides, definitions, settings, from, days)
            plan.zipWithNext { a, b ->
                assertTrue(a.day < b.day, "days out of order: ${a.day} then ${b.day}")
            }
        }
    }

    // -------------------------------------------------------------- behaviour

    @Test
    fun `switching reminders off plans nothing`() {
        val plan = Reminders.plan(
            pattern = pattern(),
            overrides = Overrides.EMPTY,
            definitions = definitions(),
            settings = ReminderSettings(enabled = false),
            from = DayNumber.of(2026, 9, 1),
            days = 30,
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `a night shift is reminded the same evening`() {
        val plan = Reminders.plan(
            pattern = Pattern("p", "nights", DayNumber.of(2026, 9, 1), listOf(ShiftCode.NIGHT)),
            overrides = Overrides.EMPTY,
            definitions = definitions(),
            settings = ReminderSettings(enabled = true, leadMinutes = 60),
            from = DayNumber.of(2026, 9, 1),
            days = 1,
        )

        // Night starts 22:00, so an hour's notice is 21:00 the same day.
        assertEquals(LocalDateTime.of(2026, 9, 1, 21, 0), plan.single().at)
    }

    /**
     * The case that makes lead time interesting: twelve hours' notice of an
     * 06:00 start is the evening before, not a negative time on the same day.
     */
    @Test
    fun `a long lead rolls back into the previous day`() {
        val plan = Reminders.plan(
            pattern = Pattern("p", "earlies", DayNumber.of(2026, 9, 10), listOf(ShiftCode.EARLY)),
            overrides = Overrides.EMPTY,
            definitions = definitions(),
            settings = ReminderSettings(enabled = true, leadMinutes = 12 * 60),
            from = DayNumber.of(2026, 9, 10),
            days = 1,
        )

        // Early starts 06:00; twelve hours before is 18:00 on the 9th.
        assertEquals(LocalDateTime.of(2026, 9, 9, 18, 0), plan.single().at)
    }

    @Test
    fun `a muted shift plans nothing while the others still do`() {
        val plan = Reminders.plan(
            pattern = Pattern(
                "p", "mixed", DayNumber.of(2026, 9, 1),
                listOf(ShiftCode.DAY, ShiftCode.NIGHT, null),
            ),
            overrides = Overrides.EMPTY,
            definitions = definitions(),
            settings = ReminderSettings(enabled = true, mutedShiftTypeIds = setOf(ShiftCode.NIGHT)),
            from = DayNumber.of(2026, 9, 1),
            days = 6,
        )

        assertEquals(listOf(ShiftCode.DAY, ShiftCode.DAY), plan.map { it.shiftTypeId })
    }

    @Test
    fun `an override is reminded as the shift actually worked`() {
        val overrides = Overrides.EMPTY.with(DayNumber.of(2026, 9, 2), ShiftCode.NIGHT)
        val plan = Reminders.plan(
            pattern = Pattern("p", "days", DayNumber.of(2026, 9, 1), listOf(ShiftCode.DAY)),
            overrides = overrides,
            definitions = definitions(),
            settings = ReminderSettings(enabled = true, leadMinutes = 0),
            from = DayNumber.of(2026, 9, 1),
            days = 3,
        )

        assertEquals(ShiftCode.NIGHT, plan[1].shiftTypeId)
        assertEquals(LocalDateTime.of(2026, 9, 2, 22, 0), plan[1].at, "the night's own start time")
    }

    @Test
    fun `a day taken off is not reminded`() {
        val overrides = Overrides.EMPTY.with(DayNumber.of(2026, 9, 2), null)
        val plan = Reminders.plan(
            pattern = Pattern("p", "days", DayNumber.of(2026, 9, 1), listOf(ShiftCode.DAY)),
            overrides = overrides,
            definitions = definitions(),
            settings = ReminderSettings(enabled = true),
            from = DayNumber.of(2026, 9, 1),
            days = 3,
        )

        assertEquals(listOf(DayNumber.of(2026, 9, 1), DayNumber.of(2026, 9, 3)), plan.map { it.day })
    }

    /**
     * A shift type deleted out from under a stored override would otherwise
     * fire a notification naming nothing.
     */
    @Test
    fun `a shift with no definition is skipped rather than guessed at`() {
        val plan = Reminders.plan(
            pattern = Pattern("p", "ghost", DayNumber.of(2026, 9, 1), listOf("deleted-shift")),
            overrides = Overrides.EMPTY,
            definitions = definitions(),
            settings = ReminderSettings(enabled = true),
            from = DayNumber.of(2026, 9, 1),
            days = 5,
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `rejects nonsense settings`() {
        assertFailsWith<IllegalArgumentException> { ReminderSettings(leadMinutes = -1) }
        assertFailsWith<IllegalArgumentException> {
            ReminderSettings(leadMinutes = ReminderSettings.MAX_LEAD_MINUTES + 1)
        }
        assertFailsWith<IllegalArgumentException> { ReminderSettings(allDayNoticeMinute = 1440) }
        assertFailsWith<IllegalArgumentException> {
            Reminders.plan(
                pattern(), Overrides.EMPTY, definitions(),
                ReminderSettings(enabled = true), DayNumber(0), days = -1,
            )
        }
    }

    // ------------------------------------------------------------------- time

    /**
     * The clocks going forward can delete the wall-clock time a reminder was
     * planned for. It has to still fire, and still fire before the shift.
     */
    @Test
    fun `a reminder inside a spring-forward gap still resolves`() {
        val zone = ZoneId.of("Europe/London")
        val gapStart = requireNotNull(
            zone.rules.nextTransition(
                LocalDateTime.of(2026, 3, 1, 0, 0).atZone(zone).toInstant(),
            ),
        )
        assertTrue(gapStart.isGap, "expected the March transition to be a gap")

        // A wall-clock time that does not exist in this zone at all.
        val missing = gapStart.dateTimeBefore.plusMinutes(30)
        assertTrue(zone.rules.getValidOffsets(missing).isEmpty(), "$missing should not exist")

        val instant = Reminders.instantOf(
            Reminder(DayNumber.from(missing.toLocalDate()), ShiftCode.DAY, missing),
            zone,
        )

        assertEquals(
            gapStart.instant,
            instant,
            "a reminder in the gap should fire the moment the gap ends",
        )
    }

    /**
     * The clocks going back make the time happen twice. Taking the earlier one
     * consistently is what keeps "an hour before" an hour rather than two.
     */
    @Test
    fun `a reminder inside a fall-back overlap takes the earlier of the two`() {
        val zone = ZoneId.of("Europe/London")
        val overlap = requireNotNull(
            zone.rules.nextTransition(
                LocalDateTime.of(2026, 10, 1, 0, 0).atZone(zone).toInstant(),
            ),
        )
        assertTrue(overlap.isOverlap, "expected the October transition to be an overlap")

        val ambiguous = overlap.dateTimeAfter.plusMinutes(30)
        assertEquals(2, zone.rules.getValidOffsets(ambiguous).size, "$ambiguous should be ambiguous")

        val instant = Reminders.instantOf(
            Reminder(DayNumber.from(ambiguous.toLocalDate()), ShiftCode.DAY, ambiguous),
            zone,
        )

        assertEquals(
            ambiguous.toInstant(zone.rules.getValidOffsets(ambiguous).first()),
            instant,
            "the earlier offset should win",
        )
        assertTrue(instant.isBefore(overlap.instant.plus(Duration.ofMinutes(30))))
    }

    /** Whatever the zone or the date, resolving must produce an answer. */
    @Test
    fun `resolving never throws in any zone`() {
        val rnd = Random(SEED)
        val zones = listOf(
            "Europe/London", "Europe/Berlin", "America/New_York", "Australia/Lord_Howe",
            "Asia/Kolkata", "Pacific/Chatham", "America/Santiago", "UTC",
        ).map(ZoneId::of)

        repeat(20_000) {
            val zone = zones[rnd.nextInt(zones.size)]
            val day = DayNumber(rnd.nextLong(-10_000, 30_000))
            val at = LocalDateTime.of(day.toLocalDate(), java.time.LocalTime.MIDNIGHT)
                .plusMinutes(rnd.nextLong(0, 1440))

            Reminders.instantOf(Reminder(day, ShiftCode.DAY, at), zone)
        }
    }

    // ----------------------------------------------------------------- driver

    private fun forEachCase(
        cases: Int = 5_000,
        check: (Pattern, Overrides, Map<String, ShiftDefinition>, ReminderSettings, DayNumber, Int) -> Unit,
    ) {
        val rnd = Random(SEED)
        val ids = listOf(ShiftCode.DAY, ShiftCode.NIGHT, ShiftCode.EARLY, ShiftCode.LATE)

        repeat(cases) {
            val slots = List(rnd.nextInt(1, 17)) {
                if (rnd.nextBoolean()) null else ids[rnd.nextInt(ids.size)]
            }
            val anchor = DayNumber(rnd.nextLong(-20_000, 20_000))
            val from = anchor + rnd.nextLong(-400, 400)

            val overrides = (0 until rnd.nextInt(0, 5)).fold(Overrides.EMPTY) { acc, _ ->
                acc.with(
                    from + rnd.nextLong(0, 40),
                    if (rnd.nextBoolean()) null else ids[rnd.nextInt(ids.size)],
                )
            }

            // Some runs drop a definition, so the "shift with no definition"
            // path is exercised by the properties and not only by one example.
            val available = if (rnd.nextInt(6) == 0) ids.drop(1) else ids
            val definitions = available.associateWith { id ->
                if (rnd.nextInt(4) == 0) {
                    ShiftDefinition(id, id.take(1).uppercase(), id)
                } else {
                    ShiftDefinition(id, id.take(1).uppercase(), id, rnd.nextInt(0, 1440), rnd.nextInt(1, 1441))
                }
            }

            val settings = ReminderSettings(
                enabled = true,
                leadMinutes = rnd.nextInt(0, ReminderSettings.MAX_LEAD_MINUTES + 1),
                mutedShiftTypeIds = ids.filter { rnd.nextInt(5) == 0 }.toSet(),
                allDayNoticeMinute = rnd.nextInt(0, 1440),
            )

            check(Pattern("p", "random", anchor, slots), overrides, definitions, settings, from, rnd.nextInt(0, 45))
        }
    }

    private fun pattern() =
        Pattern("p", "test", DayNumber.of(2026, 9, 1), listOf(ShiftCode.DAY, null))

    private fun definitions() = mapOf(
        ShiftCode.DAY to ShiftDefinition(ShiftCode.DAY, "D", "Day", 8 * 60, 8 * 60),
        ShiftCode.NIGHT to ShiftDefinition(ShiftCode.NIGHT, "N", "Night", 22 * 60, 10 * 60),
        ShiftCode.EARLY to ShiftDefinition(ShiftCode.EARLY, "E", "Early", 6 * 60, 8 * 60),
        ShiftCode.LATE to ShiftDefinition(ShiftCode.LATE, "L", "Late", 14 * 60, 8 * 60),
        "allday" to ShiftDefinition("allday", "A", "All day"),
    )

    private companion object {
        const val SEED = 20260905L
    }
}
