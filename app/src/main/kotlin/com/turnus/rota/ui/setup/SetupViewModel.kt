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
)

data class SetupUiState(
    val step: SetupStep = SetupStep.Welcome,
    val styles: List<ShiftStyle> = emptyList(),
    val presets: List<Preset> = Presets.ALL,
    val patternName: String = "",
    val slots: List<String?> = emptyList(),
    val candidates: List<AnchorCandidate> = emptyList(),
    val anchor: DayNumber? = null,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val cycleLength: Int get() = slots.size
    val workingDays: Int get() = slots.count { it != null }
    val styleById: Map<String, ShiftStyle> get() = styles.associateBy { it.id }

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
     * Back within the wizard. Returns false when there is nowhere left to go,
     * so the caller can let the system handle it.
     */
    fun back(): Boolean {
        val current = _state.value
        val previous = when (current.step) {
            SetupStep.Welcome -> return false
            SetupStep.ChoosePattern -> SetupStep.Welcome
            SetupStep.BuildCustom -> SetupStep.ChoosePattern
            SetupStep.ChooseShiftToday -> SetupStep.ChoosePattern
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
        )
    }

    fun startCustom() = _state.update {
        it.copy(
            step = SetupStep.BuildCustom,
            patternName = "My rota",
            // A sensible seed the user edits, rather than an empty screen with
            // no clue what a "slot" is meant to be.
            slots = List(8) { index -> if (index < 4) it.styles.firstOrNull()?.id else null },
        )
    }

    /** Cycles one slot through the shift types and then off, in order. */
    fun cycleSlot(index: Int) = _state.update { current ->
        val order = current.styles.map { it.id }
        if (order.isEmpty()) return@update current
        val slot = current.slots.getOrNull(index)
        val next = when (slot) {
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
            runCatching {
                repository.saveActivePattern(
                    Pattern(
                        id = UUID.randomUUID().toString(),
                        name = current.patternName.ifBlank { "My rota" },
                        anchor = anchor,
                        slots = current.slots,
                    ),
                )
            }.onSuccess {
                onDone()
            }.onFailure { failure ->
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
