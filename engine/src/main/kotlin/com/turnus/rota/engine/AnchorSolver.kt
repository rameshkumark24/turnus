package com.turnus.rota.engine

/**
 * Works out where today sits in a cycle, from what the user says they are
 * working today.
 *
 * This is the arithmetic behind the one question setup actually asks. Users do
 * not know what an anchor date is and should never be asked to count cycle
 * positions; they know what shift they are on today. Turning that answer into
 * an anchor is this file's whole job.
 *
 * It lives in `:engine` rather than in the setup screen because it is rota
 * logic, not presentation — which also means it gets the same property tests as
 * everything else here, and the screen above it cannot get the sums wrong.
 */
object AnchorSolver {

    /**
     * One possible reading of the user's answer.
     *
     * @property slotIndex the cycle position today would occupy
     * @property anchor the resulting anchor date — the day slot 0 falls on
     */
    data class Candidate(
        val slotIndex: Int,
        val anchor: DayNumber,
    )

    /**
     * Every position in [slots] that matches [todaysShift], with the anchor each
     * one implies. A null [todaysShift] means "I'm off today".
     *
     * More than one candidate is the normal case, not an edge case: a 4-on-4-off
     * rota has four positions that are all "a Day shift" and four that are all
     * "off". The caller has to disambiguate, and the honest way is to show the
     * user the week each reading produces — see [previewFrom].
     *
     * Returns an empty list when the shift does not occur in the cycle at all,
     * which is a caller error worth surfacing rather than silently anchoring.
     */
    fun candidates(
        slots: List<String?>,
        todaysShift: String?,
        today: DayNumber,
    ): List<Candidate> {
        require(slots.isNotEmpty()) { "a cycle needs at least one slot" }
        return slots.indices
            .filter { slots[it] == todaysShift }
            .map { index -> Candidate(slotIndex = index, anchor = today - index.toLong()) }
    }

    /**
     * The next [days] days as they would look under this reading, starting today.
     *
     * This is the disambiguation question. "Which of these is your week?" is
     * answerable by anyone; "which cycle index are you on?" is not.
     */
    fun previewFrom(slots: List<String?>, slotIndex: Int, days: Int = 7): List<String?> {
        require(slots.isNotEmpty()) { "a cycle needs at least one slot" }
        require(days > 0) { "preview needs at least one day" }
        return (0 until days).map { offset ->
            slots[Math.floorMod(slotIndex + offset, slots.size)]
        }
    }

    /** Builds the pattern a chosen [candidate] implies. */
    fun patternFor(
        candidate: Candidate,
        slots: List<String?>,
        id: String,
        name: String,
    ): Pattern = Pattern(id = id, name = name, anchor = candidate.anchor, slots = slots)
}
