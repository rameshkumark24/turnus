package com.turnus.rota.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turnus.rota.data.RotaRepository
import com.turnus.rota.data.ShiftStyle
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.AnchorSolver
import com.turnus.rota.engine.Pattern
import com.turnus.rota.engine.Preset
import com.turnus.rota.engine.Presets
import com.turnus.rota.engine.ShiftEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class SetupStep {
    Welcome,
    ChoosePattern,
    BuildCustom,
    ChooseShiftToday,
    ResolveAmbiguity,
    Confirm,
}

/**
 * One possible reading of "I'm on a Day shift today".
 *
 * When the chosen shift appears more than once in the cycle, today could sit at
 * any of those positions, and each implies a different anchor. [preview] is the
 * next seven days under that reading — showing them is the only disambiguation
 * question a user can actually answer, because it asks about their real week
 * rather than about cycle indices.
 */
data class AnchorCandidate(
    val slotIndex: Int,
    val anchor: DayNumber,
    val preview: List<String?>,
    /** Where today sits in its unbroken run, e.g. the 2nd of 4 days on. */
    val runPosition: AnchorSolver.RunPosition,
    val isWorking: Boolean,
) {
    /**
     * The label that actually distinguishes one candidate from another.
     *
     * Four cards all reading "starting today" force a user to compare strips of
     * coloured squares. "Your 2nd of 4 days on" is something they know about
     * their own week without looking at anything.
     */
    val label: String
        get() = buildString {
            append("Your ")
            append(ordinal(runPosition.position))
            append(" of ")
            append(runPosition.length)
            append(if (isWorking) " days on" else " days off")
        }
}

private fun ordinal(value: Int): String {
    val suffix = when {
        value % 100 in 11..13 -> "th"
        value % 10 == 1 -> "st"
        value % 10 == 2 -> "nd"
        value % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$value$suffix"
}

data class SetupUiState(
    val step: SetupStep = SetupStep.Welcome,
    val styles: List<ShiftStyle> = emptyList(),
    val presets: List<Preset> = Presets.ALL,
    val patternName: String = "",
    val slots: List<String?> = emptyList(),
    val candidates: List<AnchorCandidate> = emptyList(),
    val anchor: DayNumber? = null,
    val saving: Boolean = false,
    val cameFromBuilder: Boolean = false,
    /**
     * Set when an existing rota is being changed rather than a first one set up.
     *
     * The id is kept and reused on save, which is the whole reason this field
     * holds an id rather than a boolean: the changed days the user has recorded
     * — sickness, swaps, booked leave — hang off the pattern id. Saving under a
     * fresh id would leave every one of them attached to a rota that is no
     * longer active, and they would simply disappear from the calendar.
     */
    val editingPatternId: String? = null,
    /** True for the frame or two between opening the editor and reading the rota. */
    val preparing: Boolean = false,
    val error: String? = null,
) {
    val isEditing: Boolean get() = editingPatternId != null
    val cycleLength: Int get() = slots.size
    val workingDays: Int get() = slots.count { it != null }
    // A val, not a computed get(): a fresh map on every read makes every
    // SlotStrip see an unequal parameter, so Compose can skip nothing.
    val styleById: Map<String, ShiftStyle> = styles.associateBy { it.id }

    /**
     * Fourteen days from the chosen reading, resolved through the real engine
     * rather than recomputed here — so what the user confirms is exactly what
     * the calendar will show them, not an approximation of it.
     */
    fun confirmationPreview(): List<String?> {
        val a = anchor ?: return emptyList()
        if (slots.isEmpty()) return emptyList()
        val pattern = Pattern(id = "preview", name = patternName, anchor = a, slots = slots)
        val today = DayNumber.today()
        return (0 until 14).map { ShiftEngine.scheduled(pattern, today + it.toLong()) }
    }
}

class SetupViewModel(
    private val repository: RotaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedDefaultsIfEmpty()
            val styles = repository.shiftStyles()
            _state.update { it.copy(styles = styles.filter(ShiftStyle::isWorking)) }
        }
    }

    // ------------------------------------------------------------- navigation

    fun begin() = _state.update { it.copy(step = SetupStep.ChoosePattern) }

    /**
     * Re-enters the wizard to change a rota that already exists.
     *
     * People change teams, sites and employers, and a rota planner that can
     * only be told once is one they uninstall when that happens.
     *
     * Starts at the pattern list rather than the welcome screen — they have met
     * the app — and carries the current cycle in, so "build my own" opens on
     * what they work now instead of a blank grid they have to retype from
     * memory.
     *
     * Does nothing if an edit is already in progress. The screen calls this on
     * entry, and entry happens again on every rotation and font-size change; a
     * version that re-primed would throw away a half-built cycle each time.
     */
    fun editActive() {
        if (_state.value.isEditing) return
        _state.update { it.copy(preparing = true) }
        viewModelScope.launch {
            val pattern = repository.activePattern()
            _state.update { current ->
                if (pattern == null) {
                    current.copy(preparing = false)
                } else {
                    current.copy(
                        step = SetupStep.ChoosePattern,
                        editingPatternId = pattern.id,
                        patternName = pattern.name,
                        slots = pattern.slots,
                        anchor = null,
                        candidates = emptyList(),
                        cameFromBuilder = false,
                        preparing = false,
                        error = null,
                    )
                }
            }
        }
    }

    /**
     * Leaves edit mode, saved or abandoned.
     *
     * The wizard is one long-lived ViewModel, so without this an abandoned edit
     * would still be sitting on the confirm step the next time the screen was
     * opened — offering to save a rota the user had already walked away from.
     */
    fun stopEditing() = _state.update {
        SetupUiState(styles = it.styles, presets = it.presets)
    }

    /**
     * Back within the wizard. Returns false when there is nowhere left to go,
     * so the caller can let the system handle it.
     */
    fun back(): Boolean {
        val current = _state.value
        val previous = when (current.step) {
            SetupStep.Welcome -> return false
            // There is no welcome screen to go back to when the rota already
            // exists; the caller closes the screen instead.
            SetupStep.ChoosePattern -> if (current.isEditing) return false else SetupStep.Welcome
            SetupStep.BuildCustom -> SetupStep.ChoosePattern
            // Back into the builder when the cycle came from there. Routing to
            // the preset list instead stranded a hand-built cycle: the only way
            // back was the button that resets it, so minutes of tapping
            // vanished with no warning.
            SetupStep.ChooseShiftToday ->
                if (current.cameFromBuilder) SetupStep.BuildCustom else SetupStep.ChoosePattern
            SetupStep.ResolveAmbiguity -> SetupStep.ChooseShiftToday
            SetupStep.Confirm -> SetupStep.ChooseShiftToday
        }
        _state.update { it.copy(step = previous, error = null) }
        return true
    }

    // --------------------------------------------------------------- pattern

    fun choosePreset(preset: Preset) = _state.update {
        it.copy(
            step = SetupStep.ChooseShiftToday,
            patternName = preset.displayName,
            slots = preset.slots,
            cameFromBuilder = false,
        )
    }

    fun startCustom() = _state.update {
        it.copy(
            step = SetupStep.BuildCustom,
            cameFromBuilder = true,
            patternName = it.patternName.ifBlank { "My rota" },
            // Only seed an empty builder. Overwriting a cycle the user already
            // built is destructive, and this is the route back into the builder.
            slots = it.slots.ifEmpty {
                List(8) { index -> if (index < 4) it.styles.firstOrNull()?.id else null }
            },
        )
    }

    /** Cycles one slot through the shift types and then off, in order. */
    fun cycleSlot(index: Int) = _state.update { current ->
        val order = current.styles.map { it.id }
        // Guard the write as well as the read: an index can be stale by the
        // time it arrives if the cycle was shortened in between.
        if (order.isEmpty() || index !in current.slots.indices) return@update current
        val next = when (val slot = current.slots[index]) {
            null -> order.first()
            else -> {
                val at = order.indexOf(slot)
                if (at < 0 || at == order.lastIndex) null else order[at + 1]
            }
        }
        current.copy(slots = current.slots.toMutableList().also { it[index] = next })
    }

    fun addSlot() = _state.update {
        if (it.slots.size >= MAX_CYCLE) it else it.copy(slots = it.slots + null)
    }

    fun removeSlot() = _state.update {
        if (it.slots.size <= 1) it else it.copy(slots = it.slots.dropLast(1))
    }

    fun confirmCustom() = _state.update {
        if (it.workingDays == 0) {
            it.copy(error = "Add at least one working day before continuing")
        } else {
            it.copy(step = SetupStep.ChooseShiftToday, error = null)
        }
    }

    // ---------------------------------------------------------------- anchor

    /**
     * The central question of setup: "which shift are you on today?"
     *
     * Asking it this way means the user never has to know what an anchor date
     * is, or count cycle positions. They answer about today, which they know.
     *
     * The arithmetic is [AnchorSolver]'s, in `:engine`, where it is property
     * tested — a mistake here would misalign every calendar built through setup
     * and nobody would notice until they missed a shift.
     */
    fun chooseTodaysShift(shiftTypeId: String) = resolveToday(shiftTypeId)

    /** "I'm off today" — the same question, for the null slot. */
    fun chooseOffToday() = resolveToday(null)

    private fun resolveToday(shiftTypeId: String?) = _state.update { current ->
        if (current.slots.isEmpty()) return@update current

        val candidates = AnchorSolver
            .candidates(current.slots, shiftTypeId, DayNumber.today())
            .map { candidate ->
                AnchorCandidate(
                    slotIndex = candidate.slotIndex,
                    anchor = candidate.anchor,
                    preview = AnchorSolver.previewFrom(current.slots, candidate.slotIndex),
                    runPosition = AnchorSolver.runPosition(current.slots, candidate.slotIndex),
                    isWorking = shiftTypeId != null,
                )
            }

        when (candidates.size) {
            0 -> current.copy(
                error = if (shiftTypeId == null) {
                    "This rota has no days off"
                } else {
                    "That shift is not part of this rota"
                },
            )
            1 -> current.copy(step = SetupStep.Confirm, anchor = candidates.first().anchor, error = null)
            else -> current.copy(step = SetupStep.ResolveAmbiguity, candidates = candidates, error = null)
        }
    }

    fun resolveAmbiguity(candidate: AnchorCandidate) = _state.update {
        it.copy(step = SetupStep.Confirm, anchor = candidate.anchor, error = null)
    }

    /**
     * The escape hatch for anyone who would rather say "my cycle started on
     * this date" than answer about today.
     */
    fun chooseAnchorDate(day: DayNumber) = _state.update {
        it.copy(step = SetupStep.Confirm, anchor = day, error = null)
    }

    // ------------------------------------------------------------------ save

    fun finish(onDone: () -> Unit) {
        val current = _state.value
        val anchor = current.anchor ?: return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            // Not runCatching: it catches CancellationException too, which would
            // report a cancelled scope as a failed save and touch state on a
            // scope that is already going away.
            try {
                repository.saveActivePattern(
                    Pattern(
                        // Reused when editing. See SetupUiState.editingPatternId:
                        // a new id would orphan every changed day.
                        id = current.editingPatternId ?: UUID.randomUUID().toString(),
                        name = current.patternName.ifBlank { "My rota" },
                        anchor = anchor,
                        slots = current.slots,
                    ),
                )
                onDone()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _state.update {
                    it.copy(saving = false, error = failure.message ?: "Could not save your rota")
                }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private companion object {
        const val MAX_CYCLE = 40
    }
}
