package com.turnus.rota.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turnus.rota.data.RotaRepository
import com.turnus.rota.data.ShiftStyle
import com.turnus.rota.engine.ReminderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: ReminderSettings = ReminderSettings(),
    val workingShifts: List<ShiftStyle> = emptyList(),
    val loading: Boolean = true,
)

class SettingsViewModel(
    private val repository: RotaRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        repository.observeReminderSettings(),
        repository.observeShiftStyles().map { styles -> styles.values.filter(ShiftStyle::isWorking) },
    ) { settings, shifts ->
        SettingsUiState(settings = settings, workingShifts = shifts, loading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Every change writes immediately and then reschedules.
     *
     * No Save button: a settings screen where the choice does not take effect
     * until you find and press something is a screen people leave without
     * saving. The reschedule is passed in as a callback rather than done here
     * because it needs a Context, which a ViewModel must not hold.
     */
    fun setEnabled(enabled: Boolean, onChanged: () -> Unit) =
        update(onChanged) { it.copy(enabled = enabled) }

    fun setLeadMinutes(minutes: Int, onChanged: () -> Unit) =
        update(onChanged) { it.copy(leadMinutes = minutes.coerceIn(0, ReminderSettings.MAX_LEAD_MINUTES)) }

    fun setShiftMuted(shiftTypeId: String, muted: Boolean, onChanged: () -> Unit) =
        update(onChanged) { current ->
            current.copy(
                mutedShiftTypeIds = if (muted) {
                    current.mutedShiftTypeIds + shiftTypeId
                } else {
                    current.mutedShiftTypeIds - shiftTypeId
                },
            )
        }

    private fun update(onChanged: () -> Unit, change: (ReminderSettings) -> ReminderSettings) {
        val current = state.value.settings
        viewModelScope.launch {
            try {
                repository.saveReminderSettings(change(current))
                onChanged()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _error.value = failure.message ?: "Could not save that setting"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
