package com.turnus.rota.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turnus.rota.data.BackupRejectedException
import com.turnus.rota.data.BackupResult
import com.turnus.rota.data.BackupSnapshot
import com.turnus.rota.data.BackupSummary
import com.turnus.rota.data.RotaRepository
import com.turnus.rota.data.ShiftStyle
import com.turnus.rota.engine.ReminderSettings
import com.turnus.rota.share.RotaBackupFile
import com.turnus.rota.share.RotaExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: ReminderSettings = ReminderSettings(),
    val workingShifts: List<ShiftStyle> = emptyList(),
    val loading: Boolean = true,
)

/**
 * A backup that has been read and understood, waiting for the user to say yes.
 *
 * The snapshot is held so the confirmation acts on the bytes that were shown
 * to the user, not on whatever the file contains by the time they tap: a file
 * in a synced folder can change between the picker and the dialog.
 */
data class PendingRestore(
    val summary: BackupSummary,
    internal val snapshot: BackupSnapshot,
)

data class BackupUiState(
    val busy: Boolean = false,
    val pending: PendingRestore? = null,
    val canUndo: Boolean = false,
)

/**
 * Something that went right.
 *
 * [undoable] rather than the screen matching on the wording: whether a message
 * carries an Undo is a property of what just happened, and deciding it by
 * comparing strings makes rephrasing a confirmation into a way to lose the
 * only route back.
 */
data class Notice(val text: String, val undoable: Boolean = false)

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

    /** Confirmations, kept apart from [error] so the two read differently. */
    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice.asStateFlow()

    private val _backup = MutableStateFlow(BackupUiState())
    val backup: StateFlow<BackupUiState> = _backup.asStateFlow()

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

    /**
     * Builds the calendar file and returns the share intent.
     *
     * Returns rather than launches: starting an Activity needs a Context, and a
     * ViewModel that holds one outlives the screen it came from.
     */
    suspend fun exportIcs(context: Context): Intent {
        val uri = RotaExport.writeIcs(context, repository)
        val name = repository.activePattern()?.name ?: "My rota"
        return RotaExport.shareIntent(uri, name)
    }

    // ----------------------------------------------------------------- backup

    /** True once a restore has happened in this install and can still be undone. */
    fun checkUndoAvailable(context: Context) {
        viewModelScope.launch {
            val available = runCatching { RotaBackupFile.undoSnapshot(context) != null }
                .getOrDefault(false)
            _backup.update { it.copy(canUndo = available) }
        }
    }

    fun saveBackup(context: Context, target: Uri) {
        busy {
            RotaBackupFile.write(context, repository, target)
            _notice.value = Notice("Backup saved")
        }
    }

    /**
     * Reads a picked file and holds it for confirmation rather than applying it.
     *
     * Nothing is written here. Restore destroys what the user already has, and
     * the only thing a file picker tells them about a file is its name — so the
     * decision belongs after they have seen what is inside it.
     */
    fun openBackup(context: Context, source: Uri) {
        busy {
            when (val result = RotaBackupFile.read(context, source)) {
                is BackupResult.Success ->
                    _backup.update {
                        it.copy(pending = PendingRestore(result.snapshot.summary(), result.snapshot))
                    }

                BackupResult.NotABackup ->
                    _error.value = "That file is not a Turnus backup"

                is BackupResult.TooNew ->
                    _error.value = "That backup was made by a newer version of Turnus. Update the app first."

                is BackupResult.Damaged ->
                    _error.value = "That backup cannot be read — ${result.reason}"
            }
        }
    }

    fun cancelRestore() {
        _backup.update { it.copy(pending = null) }
    }

    fun confirmRestore(context: Context, onRotaChanged: () -> Unit) {
        val pending = _backup.value.pending ?: return
        busy {
            // Saved before the delete, not after: the point of it is to be the
            // rota that is about to stop existing.
            RotaBackupFile.keepUndoSnapshot(context, repository)
            repository.restore(pending.snapshot)
            _backup.update { it.copy(pending = null, canUndo = true) }
            _notice.value = Notice("Rota restored", undoable = true)
            // Alarms were scheduled against the rota that just went away.
            onRotaChanged()
        }
    }

    fun undoRestore(context: Context, onRotaChanged: () -> Unit) {
        busy {
            val previous = RotaBackupFile.undoSnapshot(context)
            if (previous == null) {
                _error.value = "There is nothing to undo"
                return@busy
            }
            repository.restore(previous)
            _backup.update { it.copy(canUndo = false) }
            _notice.value = Notice("Put back the way it was")
            onRotaChanged()
        }
    }

    /**
     * One place where a backup operation's failure becomes a sentence.
     *
     * [BackupRejectedException] carries a reason written for a person, so it is
     * shown as-is; anything else gets a message that at least says which
     * operation failed rather than surfacing an IOException's file path.
     */
    private fun busy(block: suspend () -> Unit) {
        if (_backup.value.busy) return
        _backup.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (rejected: BackupRejectedException) {
                _error.value = "That backup cannot be used — ${rejected.reason}"
            } catch (failure: Exception) {
                _error.value = failure.message ?: "That did not work"
            } finally {
                _backup.update { it.copy(busy = false) }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearNotice() {
        _notice.value = null
    }
}
