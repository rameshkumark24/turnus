package com.turnus.rota.engine

/**
 * Sparse exceptions layered over a [Pattern]: swaps, sickness, leave, overtime.
 *
 * A present entry whose value is `null` means **explicitly off** — which is not
 * the same as having no entry at all. A bare `Map<DayNumber, String?>` cannot
 * express that distinction through `get()`, because both cases return `null`.
 * This wrapper forces the containment check, so "I took this working day off"
 * can never silently collapse back into the generated pattern.
 *
 * Overrides are data layered *over* the pattern. The pattern is never mutated.
 */
data class Overrides(val entries: Map<DayNumber, String?> = emptyMap()) {

    operator fun contains(day: DayNumber): Boolean = entries.containsKey(day)

    /** Only meaningful when [contains] is true. */
    operator fun get(day: DayNumber): String? = entries[day]

    val size: Int get() = entries.size

    fun with(day: DayNumber, shiftTypeId: String?): Overrides =
        Overrides(entries + (day to shiftTypeId))

    fun without(day: DayNumber): Overrides =
        Overrides(entries - day)

    fun inRange(from: DayNumber, to: DayNumber): Overrides =
        Overrides(entries.filterKeys { it in from..to })

    companion object {
        val EMPTY = Overrides()
    }
}
