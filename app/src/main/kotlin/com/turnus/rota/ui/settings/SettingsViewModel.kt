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
import com.turnus.rota.ui.TodayClock
import com.turnus.rota.data.ShiftStyle
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.Pattern
import com.turnus.rota.engine.ReminderSettings
import com.turnus.rota.engine.ShareLinkResult
import com.turnus.rota.engine.ShiftEngine
import com.turnus.rota.share.RotaBackupFile
import com.turnus.rota.share.RotaCode
import com.turnus.rota.share.RotaExport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: ReminderSettings = ReminderSettings(),
    val workingShifts: List<ShiftStyle> = emptyList(),
    val loading: Boolean = true,
    val patternName: String = "",
    val cycleLength: Int = 0,
    /**
     * What today resolves to right now.
     *
     * Shown beside the nudge buttons so moving the rota can be checked on the
     * spot. "Move it a day and then go and look at the calendar" is a change
     * nobody can verify, which is how a rota ends up two days out.
     */
    val todayLabel: String? = null,
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
    val pendingImport: PendingImport? = null,
    /** True while the paste-a-code sheet is open. */
    val entering: Boolean = false,
    /** True while the "delete everything" confirmation is on screen. */
    val confirmingDelete: Boolean = false,
    /**
     * True while the rota an import replaced can still be put back.
     *
     * Shown as a button as well as offered in the snackbar. A snackbar lasts a
     * few seconds; someone who tries a workmate's code, locks their phone and
     * realises over tea that it was the wrong shift pattern deserves the way
     * back to still be there.
     */
    val canUndoImport: Boolean = false,
)

/** What an Undo on a notice would put back. */
enum class UndoKind { LastRestore, LastImport }

/**
 * Something that went right.
 *
 * [undo] rather than the screen matching on the wording: whether a message
 * carries an Undo, and what that Undo means, is a property of what just
 * happened. Deciding it by comparing strings makes rephrasing a confirmation
 * into a way to lose the only route back.
 */
data class Notice(val text: String, val undo: UndoKind? = null)

/**
 * A rota that arrived as a code, decoded and waiting to be accepted.
 *
 * [preview] is the next fortnight as the sender's cycle would fall here,
 * resolved through the engine rather than recomputed — so what the user agrees
 * to is what their calendar will show.
 */
data class PendingImport(
    val name: String,
    val cycleLength: Int,
    val preview: List<String?>,
    val newShiftCodes: List<String>,
    internal val decoded: ShareLinkResult.Success,
)

class SettingsViewModel(
    private val repository: RotaRepository,
    clock: TodayClock,
) : ViewModel() {

    /**
     * The shared day, and this one is not merely a caption.
     *
     * It used to be read once when the ViewModel was built, on the grounds that
     * a settings screen left open across midnight would show "yesterday's shift
     * until it is reopened, which is a wrong caption rather than a wrong rota".
     * That was wrong about the consequence. This label sits directly above the
     * nudge buttons, and their own copy tells the user to *watch the line above
     * as you tap*. A stale label there does not mislead someone about a caption;
     * it invites them to shift a correct rota by a day to fix a misalignment
     * that is not real — which is the exact complaint this app exists to answer.
     */
    private val today = clock.today

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SettingsUiState> = today.flatMapLatest { currentDay ->
        combine(
            repository.observeReminderSettings(),
            repository.observeShiftStyles(),
            repository.observeActivePattern(),
            // A one-day window. Recomputed on every write, so the nudge buttons
            // below show their own effect without the user leaving the screen.
            repository.observeCalendar(currentDay, currentDay),
        ) { settings, styles, pattern, days ->
            val resolved = days.firstOrNull()
            SettingsUiState(
                settings = settings,
                workingShifts = styles.values.filter(ShiftStyle::isWorking),
                loading = false,
                patternName = pattern?.name.orEmpty(),
                cycleLength = pattern?.slots?.size ?: 0,
                todayLabel = when {
                    pattern == null -> null
                    resolved?.shiftTypeId == null -> "Off"
                    else -> styles[resolved.shiftTypeId]?.name ?: "Off"
                },
            )
        }
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
     * The rota an import replaced, kept only for as long as this screen lives.
     *
     * Deliberately not persisted, unlike the pre-restore file: an import can be
     * reversed by pasting the old code again or by the rota editor, and a
     * second on-disk snapshot with its own staleness rules would be more to go
     * wrong than it is worth.
     */
    private var patternBeforeImport: Pattern? = null

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

    // ------------------------------------------------------------------ share

    /**
     * Builds the message for the share sheet.
     *
     * Returns rather than launches, for the same reason [exportIcs] does: a
     * ViewModel holding a Context outlives the screen it came from.
     */
    suspend fun shareIntent(): Intent = RotaCode.shareIntent(RotaCode.message(repository))

    fun startEnteringCode() = _backup.update { it.copy(entering = true) }

    fun cancelEnteringCode() = _backup.update { it.copy(entering = false) }

    /**
     * Decodes what was pasted and holds it for confirmation.
     *
     * Nothing is written yet. Accepting a code moves the whole calendar, so it
     * gets the same treatment as a restore: show what it is, then ask.
     */
    fun readCode(pasted: String) {
        busy {
            when (val result = RotaCode.read(pasted)) {
                is ShareLinkResult.Success -> {
                    val known = repository.shiftStyles().map { it.code.lowercase() }.toSet()
                    // The engine's own arithmetic, not a copy of it: the
                    // preview has to agree with the calendar exactly, and
                    // floorMod on a day delta is the one thing in this app most
                    // likely to be got wrong twice.
                    val preview = Pattern(
                        id = "preview",
                        name = result.name,
                        anchor = result.anchor,
                        slots = result.codes,
                    ).let { pattern ->
                        val today = DayNumber.today()
                        (0 until PREVIEW_DAYS).map { ShiftEngine.scheduled(pattern, today + it.toLong()) }
                    }
                    _backup.update {
                        it.copy(
                            entering = false,
                            pendingImport = PendingImport(
                                name = result.name,
                                cycleLength = result.codes.size,
                                preview = preview,
                                newShiftCodes = result.shiftCodes.filterNot { code ->
                                    code.lowercase() in known
                                },
                                decoded = result,
                            ),
                        )
                    }
                }

                ShareLinkResult.Malformed ->
                    _error.value = "That is not a Turnus rota code"

                is ShareLinkResult.UnsupportedVersion ->
                    _error.value = "That code was made by a newer version of Turnus. Update the app first."

                // Names the real limit rather than calling their code broken.
                is ShareLinkResult.CycleTooLong ->
                    _error.value = "That rota repeats every ${result.days} days. " +
                        "Turnus handles cycles up to ${result.maximum} days."
            }
        }
    }

    fun cancelImport() = _backup.update { it.copy(pendingImport = null) }

    fun confirmImport(onRotaChanged: () -> Unit) {
        val pending = _backup.value.pendingImport ?: return
        busy {
            // Kept so the notice can offer a way back. Adopting someone else's
            // rota is a bigger change than it looks from the button, and the
            // person who tapped it may only find out tomorrow morning.
            patternBeforeImport = repository.activePattern()

            val outcome = repository.importSharedPattern(
                name = pending.decoded.name,
                anchor = pending.decoded.anchor,
                codes = pending.decoded.codes,
            )
            _backup.update { it.copy(pendingImport = null, canUndoImport = patternBeforeImport != null) }
            _notice.value = Notice(
                text = if (outcome.createdShiftCodes.isEmpty()) {
                    "Rota updated"
                } else {
                    "Rota updated — added " + outcome.createdShiftCodes.joinToString()
                },
                undo = UndoKind.LastImport,
            )
            onRotaChanged()
        }
    }

    // ------------------------------------------------------------------- rota

    /**
     * Moves the whole rota by a day.
     *
     * This is the fix for the commonest complaint a rota app gets — "it is out
     * by a day" — and the reason no generated shift is ever stored. It is one
     * field, applied instantly, reversible by pressing the other button, and it
     * leaves every changed day and note exactly where the user put it.
     */
    fun nudge(days: Long, onRotaChanged: () -> Unit) {
        busy {
            repository.shiftActivePatternBy(days)
            _notice.value = Notice(if (days < 0) "Moved back a day" else "Moved forward a day")
            onRotaChanged()
        }
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

                // Says what to do, not just what went wrong. The commonest
                // cause by far is a backup sitting in a cloud folder that has
                // not been downloaded to the phone yet, and the fix is
                // something the user can carry out in the other app in a few
                // seconds — but only if someone tells them that is the problem.
                BackupResult.Unreadable ->
                    _error.value = "That file could not be opened. If it is kept in Google Drive " +
                        "or another cloud folder, open it there once so it downloads to this " +
                        "phone, then pick it again."

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

    fun askToDeleteEverything() {
        _backup.update { it.copy(confirmingDelete = true) }
    }

    fun cancelDeleteEverything() {
        _backup.update { it.copy(confirmingDelete = false) }
    }

    /**
     * Wipes the rota and the undo snapshot together.
     *
     * Both, or neither. The snapshot is a full copy of the rota including its
     * notes, so deleting the database alone would leave the app holding exactly
     * the data the user just asked it to forget.
     */
    fun deleteEverything(context: Context, onRotaChanged: () -> Unit) {
        busy {
            repository.deleteEverything()
            RotaBackupFile.clearUndoSnapshot(context)
            _backup.update { it.copy(confirmingDelete = false, canUndo = false) }
            // Alarms were scheduled against a rota that no longer exists.
            onRotaChanged()
        }
    }

    fun confirmRestore(context: Context, onRotaChanged: () -> Unit) {
        val pending = _backup.value.pending ?: return
        busy {
            // Saved before the delete, not after: the point of it is to be the
            // rota that is about to stop existing.
            RotaBackupFile.keepUndoSnapshot(context, repository)
            repository.restore(pending.snapshot)
            _backup.update { it.copy(pending = null, canUndo = true) }
            _notice.value = Notice("Rota restored", undo = UndoKind.LastRestore)
            // Alarms were scheduled against the rota that just went away.
            onRotaChanged()
        }
    }

    /**
     * Puts back whatever the last change replaced.
     *
     * Two different reversals behind one word, because to the user they are the
     * same thing: the last big change, undone.
     */
    fun undo(kind: UndoKind, context: Context, onRotaChanged: () -> Unit) {
        busy {
            when (kind) {
                UndoKind.LastRestore -> {
                    val previous = RotaBackupFile.undoSnapshot(context)
                    if (previous == null) {
                        _error.value = "There is nothing to undo"
                        return@busy
                    }
                    repository.restore(previous)
                    _backup.update { it.copy(canUndo = false) }
                }

                UndoKind.LastImport -> {
                    val previous = patternBeforeImport
                    if (previous == null) {
                        _error.value = "There is nothing to undo"
                        return@busy
                    }
                    // The shifts an import created are left alone. Deleting them
                    // would fail anyway once a day has been changed to one, and
                    // an unused shift in the editor is a smaller problem than a
                    // half-finished undo.
                    repository.saveActivePattern(previous)
                    patternBeforeImport = null
                    _backup.update { it.copy(canUndoImport = false) }
                }
            }
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

    private companion object {
        /** A fortnight: long enough to recognise a rota, short enough to read. */
        const val PREVIEW_DAYS = 14
    }
}
