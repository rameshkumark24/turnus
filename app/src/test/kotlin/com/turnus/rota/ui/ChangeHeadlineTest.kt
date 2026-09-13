package com.turnus.rota.ui

import com.turnus.rota.ui.month.changeHeadline
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The next-change headline on the calendar and the widget.
 *
 * Its subject is always a point in time — tomorrow, a weekday, a date — so the
 * sentence has to take a preposition that fits one. It read "Back in Tuesday"
 * on every rest day for the whole of the pre-release, on the line a person
 * sees more than any other. The day names are formatted here the same way the
 * screen formats them, so these tests pin the grammar and not the locale.
 */
class ChangeHeadlineTest {

    private val tuesday = LocalDate.of(2026, 9, 15)
    private val weekday = tuesday.format(DateTimeFormatter.ofPattern("EEEE"))
    private val shortDate = tuesday.format(DateTimeFormatter.ofPattern("EEE d MMM"))

    @Test
    fun `a rest day ending tomorrow says back tomorrow`() {
        assertEquals("Back tomorrow", changeHeadline(nowWorking = false, daysAway = 1, target = tuesday))
    }

    @Test
    fun `a rest day ending this week names the day with on`() {
        assertEquals("Back on $weekday", changeHeadline(nowWorking = false, daysAway = 2, target = tuesday))
        assertEquals("Back on $weekday", changeHeadline(nowWorking = false, daysAway = 6, target = tuesday))
    }

    @Test
    fun `a rest day ending later gives the date with on`() {
        assertEquals("Back on $shortDate", changeHeadline(nowWorking = false, daysAway = 7, target = tuesday))
    }

    @Test
    fun `a working run reads off from, whichever way the day is named`() {
        assertEquals("Off from tomorrow", changeHeadline(nowWorking = true, daysAway = 1, target = tuesday))
        assertEquals("Off from $weekday", changeHeadline(nowWorking = true, daysAway = 3, target = tuesday))
        assertEquals("Off from $shortDate", changeHeadline(nowWorking = true, daysAway = 12, target = tuesday))
    }

    @Test
    fun `no headline ever says back in`() {
        (1..30).forEach { away ->
            val text = changeHeadline(nowWorking = false, daysAway = away, target = tuesday)
            assert(!text.startsWith("Back in")) { "day $away read \"$text\"" }
        }
    }
}
