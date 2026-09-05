package com.turnus.rota.engine

/**
 * "Am I in today, and when does that change?"
 *
 * The second question every shift worker asks after "what am I on today", and
 * the one a month grid answers badly: finding the end of your run means
 * counting coloured squares, and finding the next one after a long break means
 * counting across a month boundary.
 *
 * This works in *stretches* — unbroken spans of days that are all working or
 * all off — rather than in the pattern's cycle. That distinction matters
 * because overrides break periodicity: someone who picks up an overtime shift
 * in the middle of their four off has a two-day break, not a four-day one, and
 * a cycle-based answer would tell them the comforting lie. So every day in the
 * window is resolved through [ShiftEngine], overrides included.
 */
object Outlook {

    /**
     * An unbroken run of days that are either all working or all off.
     *
     * [shiftTypeId] is the shift on [start], not a property of the whole run:
     * a "four on" can be two days then two nights. [mixed] says when that is
     * the case, so a caller never labels such a run with a single shift name.
     */
    data class Stretch(
        val start: DayNumber,
        val length: Int,
        val shiftTypeId: String?,
        val isWorking: Boolean,
        val mixed: Boolean,
        /**
         * False when the run was still going at the edge of the search window.
         * [length] is then only a lower bound and must not be shown as a count.
         */
        val complete: Boolean,
    ) {
        val endInclusive: DayNumber get() = start + (length - 1).toLong()
    }

    /**
     * @property position 1-based place of the reference day within [current],
     *   or `null` when the start of the run lay beyond the search window.
     * @property next the run that follows [current], or `null` when [current]
     *   never ended inside the window — a rota with no days off at all, or one
     *   with no working days.
     */
    data class Summary(
        val day: DayNumber,
        val current: Stretch,
        val position: Int?,
        val next: Stretch?,
    ) {
        /** Days from the reference day to the start of [next]. */
        val daysUntilNext: Int?
            get() = next?.let { it.start.daysSince(day).toInt() }
    }

    /**
     * Resolves the run containing [day] and the one after it.
     *
     * [horizonDays] bounds the scan in both directions. Without it a rota of
     * all working days — legal, and reachable by overriding every day of a
     * short cycle — would search forever for a day off.
     */
    fun summarise(
        pattern: Pattern,
        overrides: Overrides,
        day: DayNumber,
        horizonDays: Int = 366,
    ): Summary {
        require(horizonDays > 0) { "horizonDays must be positive, was $horizonDays" }

        val working = isWorking(pattern, overrides, day)

        var start = day
        var startFound = false
        for (step in 1..horizonDays) {
            val candidate = day - step.toLong()
            if (isWorking(pattern, overrides, candidate) != working) {
                startFound = true
                break
            }
            start = candidate
        }

        val forward = stretchFrom(pattern, overrides, start, horizonDays)
        val current = forward.copy(complete = startFound && forward.complete)
        val next = if (forward.complete) {
            stretchFrom(pattern, overrides, forward.endInclusive + 1, horizonDays)
        } else {
            null
        }

        return Summary(
            day = day,
            current = current,
            position = if (startFound) (day.daysSince(start) + 1).toInt() else null,
            next = next,
        )
    }

    /**
     * The longest unbroken run of days off inside an already-resolved range.
     *
     * This is the question a year view exists to answer — "when could I take a
     * holiday?" — and it is worth computing rather than leaving someone to
     * count squares, because the best break in a rota is usually the one that
     * straddles a month boundary and is therefore the hardest to spot.
     *
     * Takes resolved days rather than a pattern so the caller can hand over
     * exactly the window it is showing, overrides already applied. A run
     * touching either end of [days] is reported with `complete = false`: it may
     * continue outside the window, so its length is only a lower bound.
     */
    fun longestBreak(days: List<ResolvedDay>): Stretch? {
        var best: Stretch? = null
        var runStart = -1

        fun close(endExclusive: Int) {
            if (runStart < 0) return
            val length = endExclusive - runStart
            if (best == null || length > best!!.length) {
                best = Stretch(
                    start = days[runStart].day,
                    length = length,
                    shiftTypeId = null,
                    isWorking = false,
                    mixed = false,
                    complete = runStart > 0 && endExclusive < days.size,
                )
            }
            runStart = -1
        }

        days.forEachIndexed { index, day ->
            if (day.isWorking) close(index) else if (runStart < 0) runStart = index
        }
        close(days.size)

        return best
    }

    private fun isWorking(pattern: Pattern, overrides: Overrides, day: DayNumber): Boolean =
        ShiftEngine.resolve(pattern, overrides, day) != null

    /** The run beginning at [from], scanning forward only. */
    private fun stretchFrom(
        pattern: Pattern,
        overrides: Overrides,
        from: DayNumber,
        horizonDays: Int,
    ): Stretch {
        val first = ShiftEngine.resolve(pattern, overrides, from)
        val working = first != null
        var length = 1
        var mixed = false
        var complete = false

        for (step in 1..horizonDays) {
            val shift = ShiftEngine.resolve(pattern, overrides, from + step.toLong())
            if ((shift != null) != working) {
                complete = true
                break
            }
            if (shift != first) mixed = true
            length++
        }

        return Stretch(
            start = from,
            length = length,
            shiftTypeId = first,
            isWorking = working,
            mixed = mixed,
            complete = complete,
        )
    }
}
