package com.turnus.rota.ui.shifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turnus.rota.data.RotaRepository
import com.turnus.rota.data.ShiftStyle
import com.turnus.rota.data.ShiftTypeInUseException
import com.turnus.rota.engine.ShiftDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A shift as the editor works on it: presentation plus the clock times. */
data class EditableShift(
    val id: String,
    val code: String,
    val name: String,
    val color: Int,
    val startMinute: Int?,
    val durationMinute: Int?,
    val isWorking: Boolean,
) {
    val isTimed: Boolean get() = startMinute != null && durationMinute != null
}

/** The row being edited, or a blank one being added. */
data class ShiftDraft(
    val id: String?,
    val code: String,
    val name: String,
    val color: Int,
    val startMinute: Int,
    val durationMinute: Int,
    val timed: Boolean,
) {
    val isNew: Boolean get() = id == null
}

data class ShiftEditorUiState(
    val shifts: List<EditableShift> = emptyList(),
    val draft: ShiftDraft? = null,
    val error: String? = null,
    val saving: Boolean = false,
)

/**
 * Editing the shift types themselves — their names, colours and, above all,
 * their times.
 *
 * The times are the reason this screen has to exist. Reminders, the exported
 * calendar and the day sheet all read them, and the seeded defaults are
 * guesses: a user whose day shift starts at 06:00 rather than 07:00 would
 * otherwise get every reminder an hour late with no way to correct it.
 */
class ShiftEditorViewModel(
    private val repository: RotaRepository,
) : ViewModel() {

    private val draft = MutableStateFlow<ShiftDraft?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val saving = MutableStateFlow(false)

    val state: StateFlow<ShiftEditorUiState> = combine(
        repository.observeShiftTypes(),
        repository.observeShiftStyles(),
        draft,
        message,
        saving,
    ) { definitions, styles, currentDraft, error, isSaving ->
        ShiftEditorUiState(
            shifts = definitions.mapNotNull { definition ->
                val style = styles[definition.id] ?: return@mapNotNull null
                EditableShift(
                    id = definition.id,
                    code = definition.code,
                    name = definition.name,
                    color = style.color,
                    startMinute = definition.startMinute,
                    durationMinute = definition.durationMinute,
                    isWorking = style.isWorking,
                )
            },
            draft = currentDraft,
            error = error,
            saving = isSaving,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShiftEditorUiState(),
    )

    fun edit(shift: EditableShift) {
        draft.value = ShiftDraft(
            id = shift.id,
            code = shift.code,
            name = shift.name,
            color = shift.color,
            // Sensible starting points for a shift that had no times, rather
            // than zeros the user has to clear before they can type.
            startMinute = shift.startMinute ?: DEFAULT_START,
            durationMinute = shift.durationMinute ?: DEFAULT_DURATION,
            timed = shift.isTimed,
        )
    }

    fun addNew() {
        draft.value = ShiftDraft(
            id = null,
            code = "",
            name = "",
            color = NEW_COLOR,
            startMinute = DEFAULT_START,
            durationMinute = DEFAULT_DURATION,
            timed = true,
        )
    }

    fun updateDraft(change: (ShiftDraft) -> ShiftDraft) {
        draft.update { it?.let(change) }
    }

    fun cancel() {
        draft.value = null
    }

    fun save() {
        val current = draft.value ?: return
        val code = current.code.trim()
        val name = current.name.trim()
        if (code.isBlank() || name.isBlank()) {
            message.value = "A shift needs a letter and a name"
            return
        }

        saving.value = true
        launchGuarded {
            // A shift with no times is a valid thing — some rotas record only
            // which shift you are on — so both fields go null together, which
            // is the pairing ShiftDefinition enforces.
            val start = current.startMinute.takeIf { current.timed }
            val duration = current.durationMinute.takeIf { current.timed }

            if (current.isNew) {
                repository.createShiftType(
                    code = code,
                    name = name,
                    color = current.color,
                    startMinute = start,
                    durationMinute = duration,
                )
            } else {
                repository.updateShiftType(
                    id = current.id!!,
                    code = code,
                    name = name,
                    color = current.color,
                    startMinute = start,
                    durationMinute = duration,
                )
            }
            draft.value = null
        }
    }

    fun delete(shift: EditableShift) {
        launchGuarded {
            repository.deleteShiftType(shift.id)
            draft.value = null
        }
    }

    fun clearError() {
        message.value = null
    }

    /**
     * ShiftTypeInUseException carries exactly what still references the shift,
     * so the message can say which pattern or how many days to fix rather than
     * just refusing.
     */
    private fun launchGuarded(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (inUse: ShiftTypeInUseException) {
                message.value = buildString {
                    append("Still in use")
                    if (inUse.usedByPatterns.isNotEmpty()) {
                        append(" by ").append(inUse.usedByPatterns.joinToString())
                    }
                    if (inUse.usedByOverrideCount > 0) {
                        if (inUse.usedByPatterns.isNotEmpty()) append(" and")
                        append(" on ").append(inUse.usedByOverrideCount).append(" changed day")
                        if (inUse.usedByOverrideCount != 1) append("s")
                    }
                }
            } catch (failure: Exception) {
                message.value = failure.message ?: "Could not save that shift"
            } finally {
                saving.value = false
            }
        }
    }

    private companion object {
        const val DEFAULT_START = 9 * 60
        const val DEFAULT_DURATION = 8 * 60
        /** A neutral slate, so a new shift is visible but not pretending to be one of the presets. */
        const val NEW_COLOR = 0xFF6B7A87.toInt()
    }
}
