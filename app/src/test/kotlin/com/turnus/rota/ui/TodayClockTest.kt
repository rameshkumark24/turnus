package com.turnus.rota.ui

import com.turnus.rota.engine.DayNumber
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The day, as a value rather than a reading of the machine's clock.
 *
 * These tests exist because of what they replaced. The date used to be read
 * inline wherever it was needed, which made every consumer of it untestable —
 * any assertion would have been against whatever day the build machine thought
 * it was. Two bugs hid behind that for two phases: a calendar that went on
 * ringing yesterday, and a screen that came back from elsewhere still holding
 * the day it left with.
 */
class TodayClockTest {

    @Test
    fun `reads the day it is given`() {
        val clock = TodayClock { DayNumber(20_704) }
        assertEquals(DayNumber(20_704), clock.today.value)
    }

    /** The whole point: something outside says the day moved, and it moves. */
    @Test
    fun `refresh picks up a day that has changed`() {
        var day = DayNumber(20_704)
        val clock = TodayClock { day }

        day = DayNumber(20_705)
        assertEquals(
            DayNumber(20_704),
            clock.today.value,
            "nothing may change until it is asked to",
        )

        clock.refresh()
        assertEquals(DayNumber(20_705), clock.today.value)
    }

    /**
     * The broadcasts driving this also fire when the clock is merely nudged by
     * a second, and every *change* re-subscribes the calendar queries behind
     * it. Repeated refreshing therefore has to be safe.
     *
     * What this asserts is that refreshing really does re-read and really does
     * leave the day alone; that an equal value produces no emission is
     * `StateFlow`'s own guarantee, and observing it would need a collector and
     * a test dispatcher this module does not depend on.
     */
    @Test
    fun `refreshing an unchanged day changes nothing`() {
        var reads = 0
        val clock = TodayClock {
            reads++
            DayNumber(20_704)
        }

        repeat(50) { clock.refresh() }

        assertEquals(51, reads, "each refresh must actually re-read the clock")
        assertEquals(DayNumber(20_704), clock.today.value)
    }

    /** One source, so two readers cannot disagree about what day it is. */
    @Test
    fun `every reader sees the same day`() {
        var day = DayNumber(20_704)
        val clock = TodayClock { day }
        val month = clock.today
        val year = clock.today

        day = DayNumber(20_800)
        clock.refresh()

        assertEquals(month.value, year.value)
        assertEquals(DayNumber(20_800), month.value)
    }
}
