package com.turnus.rota.engine

/**
 * A repeating rota.
 *
 * [slots] is the ordered cycle. Each entry is a shift-type id, or `null` for a
 * day off. [anchor] is the civil date on which slot 0 falls.
 *
 * The cycle length is simply `slots.size`, which is the reason 6-, 8-, 10-, 12-
 * and 35-day rotas need no special handling anywhere in the engine. Adding a new
 * shape of rotation means adding data, never code.
 */
data class Pattern(
    val id: String,
    val name: String,
    val anchor: DayNumber,
    val slots: List<String?>,
) {
    init {
        require(slots.isNotEmpty()) { "A pattern needs at least one slot" }
    }

    val cycleLength: Int get() = slots.size

    /** Days in one cycle on which the user is working. */
    val workingDaysPerCycle: Int get() = slots.count { it != null }

    /**
     * Returns a copy shifted by [days], preserving which slot falls on which
     * date relative to the user's intent.
     *
     * This is the fix for "my rota moved by a day" — the single most common
     * correction users need, and a one-field edit precisely because generated
     * shifts are never stored.
     */
    fun shiftedBy(days: Long): Pattern = copy(anchor = anchor + days)
}
