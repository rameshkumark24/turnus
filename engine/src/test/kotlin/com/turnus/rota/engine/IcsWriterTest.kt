package com.turnus.rota.engine

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IcsWriterTest {

    // ---------------------------------------------------------------- structure

    @Test
    fun `emits a well formed calendar wrapper`() {
        val ics = writeJanuary()
        val lines = ics.split(CRLF)

        assertEquals("BEGIN:VCALENDAR", lines.first())
        assertContains(lines, "VERSION:2.0")
        assertContains(lines, "PRODID:-//Turnus//Shift Work Calendar//EN")
        assertContains(lines, "CALSCALE:GREGORIAN")
        // Trailing CRLF means the final element is an empty string.
        assertEquals("END:VCALENDAR", lines[lines.lastIndex - 1])
    }

    /** RFC 5545 requires CRLF, not LF. Some clients silently reject LF-only files. */
    @Test
    fun `uses CRLF line endings throughout`() {
        val ics = writeJanuary()
        assertTrue(ics.endsWith(CRLF))
        assertFalse(
            Regex("(?<!\r)\n").containsMatchIn(ics),
            "found a bare LF not preceded by CR",
        )
    }

    @Test
    fun `writes one event per working day and none for days off`() {
        // 4 on, 4 off anchored at 1 Jan: 1-4 work, 5-8 off, 9-12 work...
        val ics = writeJanuary()
        val events = Regex("BEGIN:VEVENT").findAll(ics).count()
        val ends = Regex("END:VEVENT").findAll(ics).count()

        assertEquals(events, ends, "unbalanced VEVENT blocks")
        // January has 31 days; the 8-day cycle gives 4 working days per cycle.
        val expected = ShiftEngine
            .resolveRange(pattern(), Overrides.EMPTY, jan(1), jan(31))
            .count { it.isWorking }
        assertEquals(expected, events)
    }

    // ---------------------------------------------------------------- times

    @Test
    fun `writes floating local times with no zone marker`() {
        val ics = writeJanuary()
        // Day shift starts 06:00, runs 12h.
        assertContains(ics, "DTSTART:20260101T060000")
        assertContains(ics, "DTEND:20260101T180000")
        assertFalse(ics.contains("TZID"), "floating time must not carry a TZID")
        assertFalse(
            Regex("DTSTART:[0-9T]+Z").containsMatchIn(ics),
            "DTSTART must not be UTC-anchored",
        )
    }

    /** A night shift is the case that actually crosses a date boundary. */
    @Test
    fun `a shift crossing midnight ends on the following day`() {
        val night = ShiftDefinition("night", "N", "Night", startMinute = 22 * 60, durationMinute = 600)
        val nights = Pattern("p", "nights", jan(1), listOf("night"))

        val ics = IcsWriter.write(
            pattern = nights,
            overrides = Overrides.EMPTY,
            shifts = mapOf("night" to night),
            from = jan(1),
            to = jan(1),
            stamp = STAMP,
        )

        assertContains(ics, "DTSTART:20260101T220000")
        assertContains(ics, "DTEND:20260102T080000")
        assertTrue(night.crossesMidnight)
    }

    @Test
    fun `an untimed shift becomes an all day event with an exclusive end`() {
        val marker = ShiftDefinition("d", "D", "On duty")
        val every = Pattern("p", "every day", jan(1), listOf("d"))

        val ics = IcsWriter.write(every, Overrides.EMPTY, mapOf("d" to marker), jan(1), jan(1), STAMP)

        assertContains(ics, "DTSTART;VALUE=DATE:20260101")
        assertContains(ics, "DTEND;VALUE=DATE:20260102")
    }

    @Test
    fun `writes the supplied stamp rather than reading a clock`() {
        assertContains(writeJanuary(), "DTSTAMP:20260904T101530Z")
    }

    // ---------------------------------------------------------------- identity

    /** Stable UIDs are what stop a re-export duplicating the whole rota. */
    @Test
    fun `uids are stable across exports and unique within one`() {
        val first = uidsOf(writeJanuary())
        val second = uidsOf(writeJanuary())

        assertEquals(first, second, "uids changed between identical exports")
        assertEquals(first.size, first.toSet().size, "duplicate uid within one calendar")
    }

    @Test
    fun `uid changes when the day changes`() {
        val uids = uidsOf(writeJanuary())
        assertTrue(uids.any { it.endsWith("-${jan(1).value}@turnus.app") })
        assertTrue(uids.none { it.endsWith("-${jan(5).value}@turnus.app") }, "5 Jan is a day off")
    }

    // ---------------------------------------------------------------- escaping

    @Test
    fun `escapes the characters RFC 5545 reserves in text`() {
        val awkward = ShiftDefinition("d", "D", "Late, long; shift")
        val every = Pattern("p", "p", jan(1), listOf("d"))

        val ics = IcsWriter.write(every, Overrides.EMPTY, mapOf("d" to awkward), jan(1), jan(1), STAMP)
        val summary = ics.split(CRLF).first { it.startsWith("SUMMARY:") }

        assertEquals("""SUMMARY:Late\, long\; shift""", summary)
    }

    @Test
    fun `escapes a literal backslash without eating the following character`() {
        val awkward = ShiftDefinition("d", "D", """Back\slash""")
        val every = Pattern("p", "p", jan(1), listOf("d"))

        val ics = IcsWriter.write(every, Overrides.EMPTY, mapOf("d" to awkward), jan(1), jan(1), STAMP)
        val summary = ics.split(CRLF).first { it.startsWith("SUMMARY:") }

        assertEquals("""SUMMARY:Back\\slash""", summary)
    }

    // ---------------------------------------------------------------- folding

    @Test
    fun `no physical line exceeds 75 octets`() {
        val long = ShiftDefinition(
            "d", "D",
            "A deliberately overlong shift name that will certainly need folding " +
                "because it runs well past seventy five octets of UTF-8",
        )
        val every = Pattern("p", "p", jan(1), listOf("d"))
        val ics = IcsWriter.write(every, Overrides.EMPTY, mapOf("d" to long), jan(1), jan(3), STAMP)

        ics.split(CRLF).filter { it.isNotEmpty() }.forEach { line ->
            assertTrue(
                line.toByteArray(Charsets.UTF_8).size <= 75,
                "line of ${line.toByteArray(Charsets.UTF_8).size} octets: $line",
            )
        }
    }

    /**
     * Folding by character count instead of octet count would split a multi-byte
     * sequence and corrupt the name — which is every German and Nordic user.
     */
    @Test
    fun `folding preserves non ascii content exactly`() {
        val name = "Spätschicht Übergabe Ærøskøbing Nachtschicht Frühdienst Zusatzstunden Überstunden"
        val shift = ShiftDefinition("d", "D", name)
        val every = Pattern("p", "p", jan(1), listOf("d"))

        val ics = IcsWriter.write(every, Overrides.EMPTY, mapOf("d" to shift), jan(1), jan(1), STAMP)

        val unfolded = ics.replace(CRLF + " ", "")
        assertContains(unfolded, "SUMMARY:$name")
    }

    // ---------------------------------------------------------------- guards

    @Test
    fun `rejects a scheduled shift with no definition`() {
        val orphan = Pattern("p", "p", jan(1), listOf("missing"))
        val failure = assertFailsWith<IllegalArgumentException> {
            IcsWriter.write(orphan, Overrides.EMPTY, emptyMap(), jan(1), jan(1), STAMP)
        }
        assertContains(failure.message ?: "", "missing")
    }

    @Test
    fun `rejects a reversed range`() {
        assertFailsWith<IllegalArgumentException> {
            IcsWriter.write(pattern(), Overrides.EMPTY, shifts(), jan(31), jan(1), STAMP)
        }
    }

    @Test
    fun `an override is reflected in the exported calendar`() {
        val off = Overrides.EMPTY.with(jan(1), null)
        val ics = IcsWriter.write(pattern(), off, shifts(), jan(1), jan(1), STAMP)
        assertFalse(ics.contains("BEGIN:VEVENT"), "a day taken off must not produce an event")
    }

    // ---------------------------------------------------------------- helpers

    private fun jan(day: Int) = DayNumber.of(2026, 1, day)

    private fun pattern() = Pattern(
        id = "p1",
        name = "4 on, 4 off",
        anchor = jan(1),
        slots = Presets.FOUR_ON_FOUR_OFF.slots,
    )

    /**
     * The exported file must never carry a day's note.
     *
     * A note is free text, and people write things in it they would not send to
     * their manager — sickness, a hospital appointment, a funeral. The export
     * exists to be handed to other people, so this is the one place where the
     * app's most sensitive field and its most widely shared artefact meet.
     *
     * Currently structural: [Overrides] carries shift ids, not notes, so there
     * is nothing to leak. This test exists so that stays true — the obvious
     * future change is to give Overrides a note for some other feature, and
     * this fails the moment a DESCRIPTION appears.
     */
    @Test
    fun `the export carries no notes and no description field`() {
        val ics = writeJanuary()
        assertTrue(ics.isNotEmpty(), "nothing was written, so this proves nothing")
        assertFalse(
            ics.split(CRLF).any { it.startsWith("DESCRIPTION") },
            "a DESCRIPTION line appeared; a day's note must never leave the device",
        )
        assertFalse(
            ics.split(CRLF).any { it.startsWith("COMMENT") || it.startsWith("X-ALT-DESC") },
            "a free-text field appeared that could carry a note",
        )
    }

    private fun shifts() = mapOf(
        ShiftCode.DAY to ShiftDefinition(
            id = ShiftCode.DAY,
            code = "D",
            name = "Day shift",
            startMinute = 6 * 60,
            durationMinute = 12 * 60,
        ),
    )

    private fun writeJanuary() = IcsWriter.write(
        pattern = pattern(),
        overrides = Overrides.EMPTY,
        shifts = shifts(),
        from = jan(1),
        to = jan(31),
        stamp = STAMP,
    )

    private fun uidsOf(ics: String) = ics.split(CRLF)
        .filter { it.startsWith("UID:") }
        .map { it.removePrefix("UID:") }

    private companion object {
        const val CRLF = "\r\n"
        val STAMP: Instant = Instant.parse("2026-09-04T10:15:30Z")
    }
}
