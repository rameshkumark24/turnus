package com.turnus.rota.ui

import com.turnus.rota.engine.DayNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which day it is, held once for the whole app.
 *
 * ### Why one, and not one per screen
 *
 * Every screen that rings a day used to keep its own copy and refresh it from
 * its own receiver. That worked only while every screen was listening: a screen
 * that had been away had a stale copy, and two screens alive at once could
 * disagree about what day it was inside a single session. Holding it once makes
 * that unrepresentable rather than merely unlikely.
 *
 * ### Why it is a value and not a function
 *
 * `DayNumber.today()` reads the system clock, which is a fine thing for a
 * function to do and an impossible thing to test around. Nothing that consumes
 * this can be written a test for while the answer is whatever the machine
 * running the test happens to think. Taking [read] as a parameter costs one
 * default argument in production and is the whole reason this has tests.
 *
 * ### What it is not
 *
 * It is not a scheduler and it does not tick. Nothing here knows when midnight
 * is; something outside has to say so, and [refresh] is how. See `OnDateChange`,
 * which does the saying.
 *
 * An unchanged day is conflated by the `StateFlow`, so calling [refresh] more
 * often than the day actually changes is free — which matters, because the
 * broadcasts that drive it also fire when the clock is nudged by a second.
 */
class TodayClock(private val read: () -> DayNumber = { DayNumber.today() }) {

    private val _today = MutableStateFlow(read())

    /** The current civil day. Emits only when the day genuinely changes. */
    val today: StateFlow<DayNumber> = _today.asStateFlow()

    /** Re-read the clock. Cheap, and a no-op when the day has not moved. */
    fun refresh() {
        _today.value = read()
    }
}
