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

    companion object {
        /**
         * The longest cycle the app will accept from outside itself.
         *
         * Forty is a guess, and an honest one: no rota anyone has named comes
         * close, and the number is written down here so the setup builder and
         * the share-code reader cannot drift apart about it — which they had,
         * with the builder enforcing it and an imported code enforcing nothing.
         *
         * Deliberately **not** a `require` in this constructor. A pattern is
         * also built when restoring a backup, and a file written by a version
         * that allowed more, or edited by hand, must fail as a typed result the
         * user can read rather than as an exception thrown halfway through
         * replacing their rota. The ceiling belongs at the doors, not here.
         */
        const val MAX_CYCLE_DAYS: Int = 40
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
