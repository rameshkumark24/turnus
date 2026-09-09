package com.turnus.rota.ui

import com.turnus.rota.engine.DayNumber
import com.turnus.rota.ui.month.hoursCaption
import java.time.YearMonth
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The caption over the hours total.
 *
 * Worth its own tests only because of when it is wrong: at a month boundary,
 * which cannot be reached on a device without waiting for one. It used to read
 * the clock inside a `remember` keyed on neither the clock nor anything that
 * changed with it, so a grid that had correctly moved its ring to 1 October
 * went on captioning September "THIS MONTH" — the screen disagreeing with
 * itself, in the one hour a year anybody would notice.
 */
class HoursCaptionTest {

    private val uk = Locale.UK

    @Test
    fun `the month holding today is this month`() {
        assertEquals("THIS MONTH", hoursCaption(sep, DayNumber.of(2026, 9, 9), uk))
    }

    @Test
    fun `any other month is named`() {
        assertEquals("OCTOBER", hoursCaption(oct, DayNumber.of(2026, 9, 9), uk))
        assertEquals("SEPTEMBER", hoursCaption(sep, DayNumber.of(2026, 10, 1), uk))
    }

    /**
     * The rollover, which is the reason this is a function and not a lambda in
     * a composable: the grid stays on September while the day becomes the first
     * of October, and the caption has to stop claiming it is this month.
     */
    @Test
    fun `a month rolling over under a grid left on screen renames the caption`() {
        val lastNight = DayNumber.of(2026, 9, 30)
        val thisMorning = lastNight + 1

        assertEquals("THIS MONTH", hoursCaption(sep, lastNight, uk))
        assertEquals("SEPTEMBER", hoursCaption(sep, thisMorning, uk))
        // And the grid the user pages to next is the one that is now current.
        assertEquals("THIS MONTH", hoursCaption(oct, thisMorning, uk))
    }

    @Test
    fun `a year boundary is not mistaken for the same month`() {
        val dec = YearMonth.of(2026, 12)
        assertEquals("THIS MONTH", hoursCaption(dec, DayNumber.of(2026, 12, 31), uk))
        assertEquals("DECEMBER", hoursCaption(dec, DayNumber.of(2027, 1, 1), uk))
        assertEquals("DECEMBER", hoursCaption(dec, DayNumber.of(2027, 12, 31), uk))
    }

    /** The month name is the reader's, not the developer's. */
    @Test
    fun `the month name follows the locale`() {
        assertEquals("OKTOBER", hoursCaption(oct, DayNumber.of(2026, 9, 9), Locale.GERMANY))
    }

    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)
}
