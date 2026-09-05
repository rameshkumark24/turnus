package com.turnus.rota.engine

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * What the user wants to be told, and how far ahead.
 *
 * [mutedShiftTypeIds] rather than a list of wanted ids: a shift type added
 * later should start reminding by default. Opting out is a decision someone
 * made; opting in is not something they can make about a shift that did not
 * exist when they set this up.
 */
data class ReminderSettings(
    val enabled: Boolean = false,
    /** Minutes before the shift starts. May exceed a day. */
    val leadMinutes: Int = 60,
    val mutedShiftTypeIds: Set<String> = emptySet(),
    /**
     * When to give notice of an all-day shift — one with no clock time.
     * Minutes past midnight *on the previous day*, because "your shift is
     * tomorrow" is only useful while there is still an evening to act on it.
     */
    val allDayNoticeMinute: Int = 18 * 60,
) {
    init {
        require(leadMinutes in 0..MAX_LEAD_MINUTES) {
            "leadMinutes must be 0..$MAX_LEAD_MINUTES, was $leadMinutes"
        }
        require(allDayNoticeMinute in 0..1439) {
            "allDayNoticeMinute must be within a day, was $allDayNoticeMinute"
        }
    }

    companion object {
        /** A week. Beyond that a "reminder" is a different feature. */
        const val MAX_LEAD_MINUTES: Int = 7 * 24 * 60
    }
}

/**
 * One reminder, as wall-clock time.
 *
 * Deliberately a [LocalDateTime] and not an instant. Turning wall-clock into an
 * instant needs a zone, and a zone is a thing that changes underneath a stored
 * schedule — someone flies, or their region's DST rules are amended. Planning
 * in local time and resolving at the point of scheduling means the answer is
 * always computed against the rules in force now, rather than frozen at the
 * moment the rota was saved.
 */
data class Reminder(
    /** The day being *worked*, which is not always the day the reminder fires. */
    val day: DayNumber,
    val shiftTypeId: String,
    val at: LocalDateTime,
)

/**
 * Turns a rota into a list of reminders.
 *
 * Every function here is pure and takes no clock. Nothing is filtered for
 * being in the past, because "past" is a question about now and belongs to the
 * caller that is doing the scheduling — an engine that quietly dropped days
 * would be impossible to test without freezing time.
 */
object Reminders {

    /**
     * Plans reminders for [days] days starting at [from].
     *
     * Days the user is off, and shifts they have muted, produce nothing. A
     * working day whose shift type is unknown is skipped rather than guessed
     * at: it means a shift was deleted out from under a stored override, and
     * inventing a time for it would fire a notification naming nothing.
     */
    fun plan(
        pattern: Pattern,
        overrides: Overrides,
        definitions: Map<String, ShiftDefinition>,
        settings: ReminderSettings,
        from: DayNumber,
        days: Int,
    ): List<Reminder> {
        require(days >= 0) { "days must not be negative, was $days" }
        if (!settings.enabled) return emptyList()

        return (0 until days).mapNotNull { offset ->
            val day = from + offset.toLong()
            val shiftTypeId = ShiftEngine.resolve(pattern, overrides, day) ?: return@mapNotNull null
            if (shiftTypeId in settings.mutedShiftTypeIds) return@mapNotNull null
            val definition = definitions[shiftTypeId] ?: return@mapNotNull null

            Reminder(
                day = day,
                shiftTypeId = shiftTypeId,
                at = fireTime(day, definition, settings),
            )
        }
    }

    /**
     * The wall-clock moment a reminder for [day] should fire.
     *
     * A lead time longer than the shift's start rolls back into the previous
     * day, which is the normal case rather than an edge one: a twelve-hour
     * notice of an 06:00 start is 18:00 the evening before, and that is exactly
     * the reminder someone working earlies wants.
     */
    private fun fireTime(
        day: DayNumber,
        definition: ShiftDefinition,
        settings: ReminderSettings,
    ): LocalDateTime {
        val date = day.toLocalDate()
        return if (definition.isTimed) {
            LocalDateTime.of(date, LocalTime.MIDNIGHT)
                .plusMinutes((definition.startMinute!! - settings.leadMinutes).toLong())
        } else {
            LocalDateTime.of(date.minusDays(1), LocalTime.MIDNIGHT)
                .plusMinutes(settings.allDayNoticeMinute.toLong())
        }
    }

    /**
     * Resolves a reminder to the instant an alarm should be set for.
     *
     * Both awkward cases are decided here rather than left to chance:
     *
     * - **The clocks go forward.** The wall-clock time may not exist at all.
     *   The reminder fires at the first instant that does — the moment the gap
     *   ends. This is deliberately *not* `atZone`, which pushes the time
     *   forward by the whole length of the gap: an hour's notice of an 02:30
     *   shift would be planned for 01:30, which does not exist, and would then
     *   be shoved to 02:30 — arriving exactly as the shift starts. Clamping to
     *   the end of the gap keeps thirty minutes of it. A reminder can survive
     *   being early; being late is the one thing it cannot do.
     * - **The clocks go back.** The time happens twice. The earlier of the two
     *   is taken, consistently — and because the shift's own start resolves the
     *   same way, the gap between reminder and shift stays the length the user
     *   asked for instead of silently gaining an hour.
     */
    fun instantOf(reminder: Reminder, zone: ZoneId): Instant {
        val offsets = zone.rules.getValidOffsets(reminder.at)
        return when {
            // A gap: no valid offset. The transition's instant is the first
            // moment the clock is valid again.
            offsets.isEmpty() -> zone.rules.getTransition(reminder.at).instant
            // Unambiguous, or an overlap where the first entry is the earlier
            // offset — which is the one to take.
            else -> reminder.at.toInstant(offsets.first())
        }
    }
}
