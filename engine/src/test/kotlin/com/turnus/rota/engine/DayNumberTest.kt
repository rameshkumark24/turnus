package com.turnus.rota.engine

import java.time.LocalDate
import java.util.TimeZone
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DayNumberTest {

    /** Invariant 4: round-tripping a day number through date parts is lossless. */
    @Test
    fun `round trips through calendar parts across a wide range`() {
        val rnd = Random(SEED)
        repeat(20_000) {
            // ~110 years either side of the epoch.
            val original = DayNumber(rnd.nextLong(-40_000, 40_000))
            val date = original.toLocalDate()
            val rebuilt = DayNumber.of(date.year, date.monthValue, date.dayOfMonth)
            assertEquals(original, rebuilt, "seed=$SEED failed for $date")
        }
    }

    /**
     * Invariant 5: civil dates are zone-free. A rota date must mean the same
     * thing everywhere, including across a DST transition.
     */
    @Test
    fun `is identical under every default time zone`() {
        val original = TimeZone.getDefault()
        try {
            val zones = listOf(
                "UTC",
                "Asia/Kolkata",       // half-hour offset, no DST
                "Pacific/Auckland",   // southern-hemisphere DST
                "America/Los_Angeles",
                "Europe/Berlin",
                "Pacific/Chatham",    // 12:45 offset
            )
            // Includes DST transition days in both hemispheres and a leap day.
            val samples = listOf(
                DayNumber.of(2026, 3, 29),
                DayNumber.of(2026, 10, 25),
                DayNumber.of(2026, 11, 1),
                DayNumber.of(2028, 2, 29),
                DayNumber.of(2026, 12, 31),
                DayNumber.of(2027, 1, 1),
            )
            val expected = samples.map { it.value }

            zones.forEach { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val actual = listOf(
                    DayNumber.of(2026, 3, 29),
                    DayNumber.of(2026, 10, 25),
                    DayNumber.of(2026, 11, 1),
                    DayNumber.of(2028, 2, 29),
                    DayNumber.of(2026, 12, 31),
                    DayNumber.of(2027, 1, 1),
                ).map { it.value }
                assertEquals(expected, actual, "day numbers changed under $zone")
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    /** Consecutive civil dates are always exactly one apart — no DST gaps. */
    @Test
    fun `consecutive dates differ by exactly one across DST and year boundaries`() {
        var date = LocalDate.of(2025, 1, 1)
        val end = LocalDate.of(2030, 1, 1)
        while (date.isBefore(end)) {
            val next = date.plusDays(1)
            val delta = DayNumber.from(next).daysSince(DayNumber.from(date))
            assertEquals(1L, delta, "gap of $delta days between $date and $next")
            date = next
        }
    }

    @Test
    fun `leap day exists and non-leap February 29 does not`() {
        assertEquals(
            1L,
            DayNumber.of(2028, 3, 1).daysSince(DayNumber.of(2028, 2, 29)),
        )
        assertTrue(
            runCatching { DayNumber.of(2027, 2, 29) }.isFailure,
            "2027 is not a leap year",
        )
    }

    @Test
    fun `arithmetic is symmetric`() {
        val rnd = Random(SEED)
        repeat(10_000) {
            val base = DayNumber(rnd.nextLong(-40_000, 40_000))
            val delta = rnd.nextLong(-5_000, 5_000)
            assertEquals(base, (base + delta) - delta)
            assertEquals(delta, (base + delta).daysSince(base))
        }
    }

    private companion object {
        const val SEED = 20260820L
    }
}
