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
    }

    /** True when this shift has real clock times, rather than being an all-day marker. */
    val isTimed: Boolean get() = startMinute != null && durationMinute != null

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
