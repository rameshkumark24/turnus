package com.turnus.rota.engine

/**
 * What a stretch of rota adds up to.
 *
 * "How many hours am I on this month?" is the second question a shift worker
 * asks their calendar, straight after "am I in tomorrow?" — it is how they
 * check a payslip, plan overtime, and work out whether a swap leaves them
 * short. The days are already resolved; this is the arithmetic over them.
 *
 * ### Rostered minutes, not elapsed time
 *
 * A total here is the sum of each shift's *defined* duration. It is
 * deliberately not the clock time that will elapse, and the difference is real:
 * twice a year, in any zone that observes it, a night shift crossing a daylight
 * saving boundary lasts eleven or thirteen hours rather than twelve.
 *
 * Rostered is the right answer for three reasons. Elapsed time cannot be
 * computed without a zone and a clock, which this module does not have and must
 * not acquire. Rotas are rostered in hours and pay usually follows the roster.
 * And a monthly total that quietly moved by an hour twice a year would read as
 * a bug in the app rather than as a fact about the clock — the user would trust
 * the number less, not more.
 *
 * ### Shifts with no times
 *
 * Some shifts are markers rather than hours: a rota where people record which
 * shift they are on but not when it runs, or a shift that arrived from a
 * workmate's share code and has not been given times yet. Those contribute a
 * shift but no minutes, and are counted separately in [Total.untimedShifts] so
 * a screen can say the total is short rather than showing a confidently wrong
 * number.
 */
object Hours {

    /**
     * @property shifts working days in the range
     * @property minutes rostered minutes across those days
     * @property untimedShifts working days whose shift has no clock times, and
     *   which therefore contributed nothing to [minutes]
     */
    data class Total(
        val shifts: Int,
        val minutes: Int,
        val untimedShifts: Int,
    ) {
        /** False when at least one shift has no times, so [minutes] is an undercount. */
        val isComplete: Boolean get() = untimedShifts == 0

        /**
         * Split for display. `%` is safe here in a way it never is on a day
         * delta: durations are 1..1440 and counts are non-negative, so [minutes]
         * cannot be negative and cannot produce the negative remainder that
         * makes `%` the most likely bug in this codebase.
         */
        val wholeHours: Int get() = minutes / 60
        val minutesPastTheHour: Int get() = minutes % 60

        companion object {
            val NONE = Total(shifts = 0, minutes = 0, untimedShifts = 0)
        }
    }

    /**
     * Adds up [days], looking each working day's shift up in [definitions].
     *
     * A day naming a shift that is not in [definitions] is counted as a shift
     * with no times rather than throwing. The repository's invariants make it
     * impossible to store such a day, so reaching this is a bug elsewhere — but
     * the caller is a screen someone is looking at, and a total that is honest
     * about being incomplete beats a crash on the calendar.
     */
    fun total(days: List<ResolvedDay>, definitions: Map<String, ShiftDefinition>): Total {
        var shifts = 0
        var minutes = 0
        var untimed = 0

        days.forEach { day ->
            val shiftTypeId = day.shiftTypeId ?: return@forEach
            shifts++
            val duration = definitions[shiftTypeId]?.durationMinute
            if (duration == null) untimed++ else minutes += duration
        }

        return Total(shifts = shifts, minutes = minutes, untimedShifts = untimed)
    }
}
