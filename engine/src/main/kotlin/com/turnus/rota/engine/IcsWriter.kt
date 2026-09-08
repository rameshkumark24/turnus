package com.turnus.rota.engine

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Generates an RFC 5545 iCalendar feed from a rota.
 *
 * Events are **materialised** — one VEVENT per working day — rather than
 * expressed as an RRULE. A rotation like 4-on-4-off has no clean single
 * recurrence rule, and calendar clients disagree about the exotic ones. Two
 * years of explicit events is boring, larger, and works everywhere.
 *
 * Times are written as floating local time: no `Z` suffix, no `TZID`. RFC 5545
 * calls this "form 1", and it means "this wall-clock time, wherever you are" —
 * which is what a shift is. A 06:00 start is 06:00 on the clock at the
 * workplace. It also means no VTIMEZONE block, and so no DST rules to get wrong.
 */
object IcsWriter {

    private const val CRLF = "\r\n"
    private const val MAX_OCTETS = 75

    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val UTC_STAMP = DateTimeFormatter
        .ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)

    /**
     * Writes the calendar for [from]..[to] inclusive.
     *
     * [stamp] is passed in rather than read from a clock so the output is
     * deterministic and therefore testable. It is an *instant* — epoch-based —
     * unlike every rota date here, which is a civil [DayNumber].
     *
     * @throws IllegalArgumentException if the range is reversed, or if any
     *   scheduled shift id is missing from [shifts]. Integrity is the caller's
     *   job; failing loudly beats writing a calendar with holes in it.
     */
    fun write(
        pattern: Pattern,
        overrides: Overrides,
        shifts: Map<String, ShiftDefinition>,
        from: DayNumber,
        to: DayNumber,
        stamp: Instant,
        calendarName: String = "Turnus",
    ): String {
        require(from <= to) { "from ($from) must not be after to ($to)" }

        val days = ShiftEngine.resolveRange(pattern, overrides, from, to)

        val missing = days.mapNotNull { it.shiftTypeId }
            .filterNot { shifts.containsKey(it) }
            .distinct()
        require(missing.isEmpty()) { "no ShiftDefinition supplied for: $missing" }

        val dtstamp = UTC_STAMP.format(stamp)
        val lines = mutableListOf<String>()

        lines += "BEGIN:VCALENDAR"
        lines += "VERSION:2.0"
        lines += "PRODID:-//Turnus//Shift Work Calendar//EN"
        lines += "CALSCALE:GREGORIAN"
        lines += "METHOD:PUBLISH"
        lines += "X-WR-CALNAME:${escape(calendarName)}"

        days.filter { it.isWorking }.forEach { day ->
            lines += event(pattern, shifts.getValue(day.shiftTypeId!!), day, dtstamp)
        }

        lines += "END:VCALENDAR"

        return lines.joinToString(CRLF) { fold(it) } + CRLF
    }

    private fun event(
        pattern: Pattern,
        shift: ShiftDefinition,
        day: ResolvedDay,
        dtstamp: String,
    ): List<String> {
        val out = mutableListOf<String>()
        out += "BEGIN:VEVENT"
        out += "UID:${uid(pattern, day.day)}"
        out += "DTSTAMP:$dtstamp"

        if (shift.isTimed) {
            val start = day.day.toLocalDate()
                .atStartOfDay()
                .plusMinutes(shift.startMinute!!.toLong())
            val end = start.plusMinutes(shift.durationMinute!!.toLong())
            out += "DTSTART:${DATE_TIME.format(start)}"
            out += "DTEND:${DATE_TIME.format(end)}"
        } else {
            // All-day events use DATE values, and DTEND is exclusive.
            out += "DTSTART;VALUE=DATE:${DATE.format(day.day.toLocalDate())}"
            out += "DTEND;VALUE=DATE:${DATE.format((day.day + 1).toLocalDate())}"
        }

        // SUMMARY is the shift's name and nothing else.
        //
        // No DESCRIPTION, and deliberately so. A day's note is free text and
        // people write things in it that they would not put in a calendar they
        // are about to e-mail their manager — "off sick, hospital", a court
        // date, a funeral. The exported file leaves the device by definition,
        // so the note must not be in it. [Overrides] does not carry notes,
        // which makes that structural rather than a matter of remembering, and
        // IcsWriterTest holds it that way.
        out += "SUMMARY:${escape(shift.name)}"
        out += "TRANSP:OPAQUE"
        out += "END:VEVENT"
        return out
    }

    /**
     * Stable per day and pattern, so re-importing an updated export refreshes
     * the existing events instead of duplicating them. This is the difference
     * between exporting twice and having two rotas in your calendar.
     */
    private fun uid(pattern: Pattern, day: DayNumber): String =
        "turnus-${sanitise(pattern.id)}-${day.value}@turnus.app"

    private fun sanitise(raw: String): String =
        raw.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { "pattern" }

    /**
     * RFC 5545 TEXT escaping.
     *
     * Written with single-character appends rather than chained string
     * replacement, so there is not one multi-backslash literal to miscount —
     * and so a backslash cannot have its own escape re-escaped by a later step.
     */
    private fun escape(value: String): String {
        val out = StringBuilder(value.length + 8)
        value.forEach { ch ->
            when (ch) {
                BACKSLASH -> out.append(BACKSLASH).append(BACKSLASH)
                ';' -> out.append(BACKSLASH).append(';')
                ',' -> out.append(BACKSLASH).append(',')
                '\n' -> out.append(BACKSLASH).append('n')
                '\r' -> Unit // CRLF is already covered by the newline branch
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private const val BACKSLASH = '\\'

    /**
     * Folds to 75 **octets** per line, continuing with CRLF plus one space.
     *
     * Octets, not characters: folding by character length would split a
     * multi-byte UTF-8 sequence and corrupt any non-ASCII shift name — which is
     * every German and Scandinavian user this app is aimed at.
     */
    private fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= MAX_OCTETS) return line

        val out = StringBuilder()
        var octets = 0
        var i = 0
        while (i < line.length) {
            val charCount = Character.charCount(line.codePointAt(i))
            val piece = line.substring(i, i + charCount)
            val size = piece.toByteArray(Charsets.UTF_8).size
            if (octets + size > MAX_OCTETS) {
                out.append(CRLF).append(' ')
                octets = 1 // the leading space counts toward the next line
            }
            out.append(piece)
            octets += size
            i += charCount
        }
        return out.toString()
    }
}
