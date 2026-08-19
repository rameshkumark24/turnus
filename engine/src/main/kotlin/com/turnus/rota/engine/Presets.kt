package com.turnus.rota.engine

/**
 * Canonical shift-type ids used by the built-in presets.
 *
 * The app maps these to user-editable `shift_type` rows at setup time, so the
 * user can rename "Day" to "Earlies" or recolour it without the presets caring.
 */
object ShiftCode {
    const val DAY = "day"
    const val NIGHT = "night"
    const val EARLY = "early"
    const val LATE = "late"
}

/** A named rotation offered during setup. */
data class Preset(
    val key: String,
    val displayName: String,
    val slots: List<String?>,
) {
    val cycleLength: Int get() = slots.size

    fun toPattern(id: String, anchor: DayNumber): Pattern =
        Pattern(id = id, name = displayName, anchor = anchor, slots = slots)
}

/**
 * The rotations people actually search for by name.
 *
 * Regional variants of most of these exist — "Continental" in particular means
 * different things in different industries. These are the widely recognised
 * forms; anything else is served by the custom pattern builder, which is why
 * the preset list does not need to be exhaustive.
 */
object Presets {

    private val D = ShiftCode.DAY
    private val N = ShiftCode.NIGHT
    private val E = ShiftCode.EARLY
    private val L = ShiftCode.LATE
    private val OFF: String? = null

    /** 4 on, 4 off. 8-day cycle, 12-hour shifts. */
    val FOUR_ON_FOUR_OFF = Preset(
        key = "4on4off",
        displayName = "4 on, 4 off",
        slots = listOf(D, D, D, D, OFF, OFF, OFF, OFF),
    )

    /** 4 days, 4 off, 4 nights, 4 off. 16-day cycle. */
    val FOUR_ON_FOUR_OFF_DAYS_NIGHTS = Preset(
        key = "4on4off_dn",
        displayName = "4 days, 4 off, 4 nights, 4 off",
        slots = listOf(
            D, D, D, D, OFF, OFF, OFF, OFF,
            N, N, N, N, OFF, OFF, OFF, OFF,
        ),
    )

    /**
     * DuPont. 28-day cycle, 12-hour shifts, averaging 42 hours per week:
     * 4 nights, 3 off, 3 days, 1 off, 3 nights, 3 off, 4 days, 7 off.
     */
    val DUPONT = Preset(
        key = "dupont",
        displayName = "DuPont",
        slots = listOf(
            N, N, N, N, OFF, OFF, OFF,
            D, D, D, OFF, N, N, N,
            OFF, OFF, OFF, D, D, D, D,
            OFF, OFF, OFF, OFF, OFF, OFF, OFF,
        ),
    )

    /** Pitman, also called 2-2-3. 14-day cycle, 12-hour shifts, 42 hours a week. */
    val PITMAN = Preset(
        key = "pitman",
        displayName = "Pitman (2-2-3)",
        slots = listOf(
            D, D, OFF, OFF, D, D, D,
            OFF, OFF, D, D, OFF, OFF, OFF,
        ),
    )

    /** Panama: the 2-2-3 shape run for two weeks of days, then two of nights. */
    val PANAMA = Preset(
        key = "panama",
        displayName = "Panama",
        slots = listOf(
            D, D, OFF, OFF, D, D, D,
            OFF, OFF, D, D, OFF, OFF, OFF,
            N, N, OFF, OFF, N, N, N,
            OFF, OFF, N, N, OFF, OFF, OFF,
        ),
    )

    /** Continental: 2 earlies, 2 lates, 2 nights, 2 off. 8-day cycle, 8-hour shifts. */
    val CONTINENTAL = Preset(
        key = "continental",
        displayName = "Continental",
        slots = listOf(E, E, L, L, N, N, OFF, OFF),
    )

    /** Plain 5 on, 2 off. The fallback for anyone whose rota is nearly regular. */
    val FIVE_ON_TWO_OFF = Preset(
        key = "5on2off",
        displayName = "5 on, 2 off",
        slots = listOf(D, D, D, D, D, OFF, OFF),
    )

    /** Presentation order for the setup picker: commonest first. */
    val ALL: List<Preset> = listOf(
        FOUR_ON_FOUR_OFF,
        FOUR_ON_FOUR_OFF_DAYS_NIGHTS,
        PITMAN,
        CONTINENTAL,
        DUPONT,
        PANAMA,
        FIVE_ON_TWO_OFF,
    )

    fun byKey(key: String): Preset? = ALL.firstOrNull { it.key == key }
}
