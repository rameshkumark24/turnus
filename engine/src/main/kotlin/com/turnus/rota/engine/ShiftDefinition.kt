package com.turnus.rota.engine

/**
 * A kind of shift, as the engine needs to know it.
 *
 * `:data` owns the persisted `shift_type` row, including colour and sort order;
 * this is the subset the engine needs to generate calendar output. Keeping it
 * separate is what lets `:engine` stay free of Android and of Room.
 *
 * A definition with no [startMinute] or [durationMinute] is an all-day marker —
 * useful for rotas where people record which shift they are on but not when it
 * runs.
 */
data class ShiftDefinition(
    val id: String,
    val code: String,
    val name: String,
    val startMinute: Int? = null,
    val durationMinute: Int? = null,
    /**
     * Unpaid break inside the shift, in minutes. Zero when there is none.
     *
     * A twelve-hour shift with a thirty-minute unpaid break is eleven and a
     * half hours on the payslip, and a monthly total that ignores that is over
     * by seven or eight hours — every month, in the same direction. That is
     * worse than having no total at all, because it is close enough to be
     * believed.
     *
     * It changes the hours only. The shift still occupies the whole twelve
     * hours in the exported calendar and the reminder still fires before the
     * start of it, because the break is time at work; it is only the counting
     * that treats it differently.
     */
    val breakMinutes: Int = 0,
) {
    init {
        require(id.isNotBlank()) { "shift id must not be blank" }
        require(startMinute == null || startMinute in 0..1439) {
            "startMinute must be within a day, was $startMinute"
        }
        require(durationMinute == null || durationMinute in 1..1440) {
            "durationMinute must be 1..1440, was $durationMinute"
        }
        require((startMinute == null) == (durationMinute == null)) {
            "a shift needs both a start and a duration, or neither"
        }
        require(breakMinutes >= 0) { "breakMinutes must not be negative, was $breakMinutes" }
        // A break shorter than the shift, and none at all on a shift with no
        // times: a marker with a break would deduct from a length it does not
        // have, and a break equal to the shift is a shift nobody works.
        require(durationMinute != null || breakMinutes == 0) {
            "a shift with no times cannot have a break"
        }
        require(durationMinute == null || breakMinutes < durationMinute) {
            "a break must be shorter than the shift, was $breakMinutes of $durationMinute"
        }
    }

    /** True when this shift has real clock times, rather than being an all-day marker. */
    val isTimed: Boolean get() = startMinute != null && durationMinute != null

    /**
     * The minutes an hours total counts: the shift's length less its unpaid
     * break. Null for a marker with no times, which counts nothing.
     */
    val paidMinute: Int? get() = durationMinute?.minus(breakMinutes)

    /**
     * True when the shift runs past midnight into the following day.
     *
     * This is the one place real elapsed time matters: a night shift crossing a
     * DST boundary is 23 or 25 hours long. Cycle position never cares, but hour
     * totals and calendar end times do.
     */
    val crossesMidnight: Boolean
        get() = isTimed && (startMinute!! + durationMinute!!) > 1440
}
