package com.turnus.rota.engine

/**
 * Resolves what a person is working on any given civil date.
 *
 * Every function here is pure. No generated shift is ever stored: the calendar
 * is recomputed from [Pattern] plus [Overrides] on each read. That is what makes
 * correcting a misaligned rota a one-field edit instead of a data migration.
 */
object ShiftEngine {

    /**
     * Index into [Pattern.slots] for [day].
     *
     * Uses [Math.floorMod], **not** `%`. Kotlin's `%` is a remainder, so
     * `-1 % 8 == -1`: any user browsing to a month before their anchor date
     * would index out of bounds. Anchors are routinely set to a date in the
     * future ("the first day of my next cycle"), so this is not a rare path.
     */
    fun slotIndex(pattern: Pattern, day: DayNumber): Int =
        Math.floorMod(day.daysSince(pattern.anchor), pattern.cycleLength.toLong()).toInt()

    /** The scheduled shift-type id for [day], ignoring overrides. `null` = off. */
    fun scheduled(pattern: Pattern, day: DayNumber): String? =
        pattern.slots[slotIndex(pattern, day)]

    /** The effective shift-type id for [day] once [overrides] are applied. */
    fun resolve(pattern: Pattern, overrides: Overrides, day: DayNumber): String? =
        if (day in overrides) overrides[day] else scheduled(pattern, day)

    /**
     * Resolves an inclusive range, for rendering a month or a whole year.
     *
     * @throws IllegalArgumentException if [from] is after [to].
     */
    fun resolveRange(
        pattern: Pattern,
        overrides: Overrides,
        from: DayNumber,
        to: DayNumber,
    ): List<ResolvedDay> {
        require(from <= to) { "from ($from) must not be after to ($to)" }
        return (from.value..to.value).map { raw ->
            val day = DayNumber(raw)
            ResolvedDay(
                day = day,
                shiftTypeId = resolve(pattern, overrides, day),
                isOverridden = day in overrides,
            )
        }
    }

    /**
     * The next day on or after [from] on which the user is working, or `null` if
     * there is none within [searchLimitDays].
     *
     * The limit exists because a pattern of all-off days would otherwise loop
     * forever. Overrides can make an all-off pattern have working days, so the
     * search cannot be short-circuited on the pattern alone.
     */
    fun nextWorkingDay(
        pattern: Pattern,
        overrides: Overrides,
        from: DayNumber,
        searchLimitDays: Int = 366,
    ): DayNumber? {
        for (offset in 0 until searchLimitDays) {
            val day = from + offset.toLong()
            if (resolve(pattern, overrides, day) != null) return day
        }
        return null
    }
}

/** One resolved calendar cell. */
data class ResolvedDay(
    val day: DayNumber,
    val shiftTypeId: String?,
    val isOverridden: Boolean,
) {
    val isWorking: Boolean get() = shiftTypeId != null
}
