package com.turnus.rota.engine

import java.time.LocalDate

/**
 * A civil date, expressed as a count of days since 1970-01-01 in the proleptic
 * ISO calendar.
 *
 * This is **not** an instant in time. It carries no time zone, no clock and no
 * DST. A rota is a calendar concept: "the fourth day of my cycle" means the same
 * thing in Auckland and in Kolkata, and it does not become 23 or 25 hours long
 * twice a year. Computing cycle position from epoch milliseconds reintroduces
 * all of that, which is why this type exists.
 *
 * Audit timestamps elsewhere in the app (`created_at`, `updated_at`) are epoch
 * **milliseconds** — a different unit entirely. This is a value class so the
 * compiler rejects the mix-up rather than letting it become a wrong shift.
 */
@JvmInline
value class DayNumber(val value: Long) : Comparable<DayNumber> {

    operator fun plus(days: Long): DayNumber = DayNumber(value + days)

    operator fun minus(days: Long): DayNumber = DayNumber(value - days)

    /** Signed day count from [other] to this. Negative when this is earlier. */
    fun daysSince(other: DayNumber): Long = value - other.value

    fun toLocalDate(): LocalDate = LocalDate.ofEpochDay(value)

    override fun compareTo(other: DayNumber): Int = value.compareTo(other.value)

    override fun toString(): String = toLocalDate().toString()

    companion object {

        /** @throws java.time.DateTimeException if the date does not exist. */
        fun of(year: Int, month: Int, day: Int): DayNumber =
            DayNumber(LocalDate.of(year, month, day).toEpochDay())

        fun from(date: LocalDate): DayNumber = DayNumber(date.toEpochDay())

        /**
         * Today in [zone]. This is the only place a time zone legitimately
         * enters rota logic — turning "now" into "which civil day is it here".
         * Once you have a [DayNumber], the zone is gone and must stay gone.
         */
        fun today(zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): DayNumber =
            from(LocalDate.now(zone))
    }
}
