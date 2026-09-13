package com.turnus.rota.engine

import java.time.format.TextStyle
import java.util.Locale
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The next-change sentence on the calendar and on the widget.
 *
 * Its subject is always a point in time — tomorrow, a weekday, a date — so it
 * has to take a preposition that fits one. Both screens once built it
 * separately, untested, and both said "Back in Tuesday" on every rest day. The
 * properties hold the grammar across thousands of days; the examples pin the
 * exact wording in one locale.
 */
class ChangeHeadlineTest {

    private val random = Random(20260913)
    private val us = Locale.US
    private val tuesday = DayNumber.of(2026, 9, 15)

    // ------------------------------------------------------------- properties

    @Test
    fun `a rest day says back tomorrow or back on, never back in`() {
        repeat(CASES) {
            val away = random.nextInt(1, 400)
            val text = Outlook.changeHeadline(nowWorking = false, daysAway = away, nextStart = randomDay(), locale = us)
            assertFalse(text.startsWith("Back in"), "$away days away read \"$text\"")
            if (away == 1) {
                assertEquals("Back tomorrow", text)
            } else {
                assertTrue(text.startsWith("Back on "), "$away days away read \"$text\"")
            }
        }
    }

    @Test
    fun `a working run always says off from`() {
        repeat(CASES) {
            val away = random.nextInt(1, 400)
            val text = Outlook.changeHeadline(nowWorking = true, daysAway = away, nextStart = randomDay(), locale = us)
            assertTrue(text.startsWith("Off from "), "$away days away read \"$text\"")
        }
    }

    /** Inside the week, the day named is the weekday the next run actually starts on. */
    @Test
    fun `the weekday named is the day the next run starts`() {
        repeat(CASES) {
            val start = randomDay()
            val away = random.nextInt(2, 7)
            val weekday = start.toLocalDate().dayOfWeek.getDisplayName(TextStyle.FULL, us)
            assertEquals("Back on $weekday", Outlook.changeHeadline(false, away, start, us))
        }
    }

    // --------------------------------------------------------------- examples

    @Test
    fun `tomorrow is said, not dated`() {
        assertEquals("Back tomorrow", Outlook.changeHeadline(false, 1, tuesday, us))
        assertEquals("Off from tomorrow", Outlook.changeHeadline(true, 1, tuesday, us))
    }

    @Test
    fun `inside a week the weekday is named`() {
        assertEquals("Back on Tuesday", Outlook.changeHeadline(false, 2, tuesday, us))
        assertEquals("Off from Tuesday", Outlook.changeHeadline(true, 6, tuesday, us))
    }

    @Test
    fun `a week or more away gives the date`() {
        assertEquals("Back on Tue 15 Sep", Outlook.changeHeadline(false, 7, tuesday, us))
        assertEquals("Off from Tue 15 Sep", Outlook.changeHeadline(true, 30, tuesday, us))
    }

    @Test
    fun `a day count below one is read as tomorrow, not thrown`() {
        assertEquals("Back tomorrow", Outlook.changeHeadline(false, 0, tuesday, us))
    }

    private fun randomDay(): DayNumber = DayNumber(random.nextLong(-20_000, 60_000))

    private companion object {
        const val CASES = 5_000
    }
}
