package com.turnus.rota.ui.settings

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turnus.rota.ads.AdGate
import com.turnus.rota.data.BackupSummary
import com.turnus.rota.data.ShiftStyle
import com.turnus.rota.share.RotaBackupFile
import com.turnus.rota.ui.theme.TurnusTokens
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The lead times worth offering. More than this is a picker nobody wants. */
private val LEAD_CHOICES = listOf(
    0 to "At the start",
    15 to "15 min",
    30 to "30 min",
    60 to "1 hour",
    120 to "2 hours",
    12 * 60 to "12 hours",
    24 * 60 to "1 day",
)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onRotaChanged: () -> Unit,
    onEditShifts: () -> Unit,
    onChangeRota: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var exporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    var notificationsAllowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var exactAllowed by remember { mutableStateOf(exactAlarmsAllowed(context)) }

    // Held in state and refreshed on resume, not read inline during
    // composition. Both of these change *outside* the app — in the system
    // permission dialog, or in Settings after tapping one of the buttons below
    // — and neither change invalidates a composition on its own. Read inline,
    // the screen kept insisting notifications were blocked seconds after the
    // user had allowed them, on the very screen offering to fix it.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsAllowed = notificationsAllowed(context)
                exactAllowed = exactAlarmsAllowed(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionAsked = true
        // The launcher's own result, rather than re-querying: it is the
        // authoritative answer and needs no cache to have caught up yet.
        notificationsAllowed = granted
        // Turning the switch on without the permission would be a lie: the
        // alarms fire and post nothing. The setting follows what can happen.
        if (!granted) viewModel.setEnabled(false, onRotaChanged)
    }

    val backup by viewModel.backup.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    // CreateDocument with a name, OpenDocument without a filter. The picker is
    // the whole storage story here: it grants this one file and nothing else,
    // so the app needs no storage permission at all.
    val saveBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(RotaBackupFile.MIME),
    ) { uri -> if (uri != null) viewModel.saveBackup(context, uri) }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.openBackup(context, uri) }

    LaunchedEffect(Unit) { viewModel.checkUndoAvailable(context) }

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        viewModel.clearError()
    }

    LaunchedEffect(notice) {
        val current = notice ?: return@LaunchedEffect
        val undo = current.undo
        val result = snackbarHostState.showSnackbar(
            message = current.text,
            // Offered where the user is already looking rather than filed under
            // a menu: these are the two changes in the app that replace a rota
            // wholesale, and both can be the wrong one.
            actionLabel = if (undo != null) "Undo" else null,
            duration = if (undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed && undo != null) {
            viewModel.undo(undo, context, onRotaChanged)
        }
        viewModel.clearNotice()
    }

    backup.pending?.let { pending ->
        RestoreConfirmation(
            summary = pending.summary,
            busy = backup.busy,
            onConfirm = { viewModel.confirmRestore(context, onRotaChanged) },
            onDismiss = viewModel::cancelRestore,
        )
    }

    if (backup.confirmingDelete) {
        AlertDialog(
            onDismissRequest = { if (!backup.busy) viewModel.cancelDeleteEverything() },
            title = { Text("Delete everything?") },
            text = {
                Text(
                    // Itemised, because "are you sure?" is not information. The
                    // backup line is the one that matters: this is the only
                    // action in the app with no undo, and the user may have a
                    // file that makes it survivable.
                    "Your rota, your shifts, every day you have changed and every " +
                        "note will be removed from this phone, along with the copy " +
                        "kept for undoing a restore.\n\n" +
                        "This cannot be undone. If you have saved a backup file, " +
                        "you can still restore from that.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteEverything(context, onRotaChanged) },
                    enabled = !backup.busy,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(if (backup.busy) "Deleting…" else "Delete everything")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::cancelDeleteEverything,
                    enabled = !backup.busy,
                ) {
                    Text("Keep my rota")
                }
            },
        )
    }

    if (backup.entering) {
        CodeEntry(
            busy = backup.busy,
            onSubmit = viewModel::readCode,
            onDismiss = viewModel::cancelEnteringCode,
        )
    }

    backup.pendingImport?.let { pending ->
        ImportConfirmation(
            pending = pending,
            busy = backup.busy,
            onConfirm = { viewModel.confirmImport(onRotaChanged) },
            onDismiss = viewModel::cancelImport,
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = TurnusTokens.ScreenPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹  Back") }
            }
            Spacer(Modifier.height(4.dp))
            Text("Reminders", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(14.dp))

            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Remind me before every shift", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Off days and muted shifts are never announced.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.settings.enabled,
                        onCheckedChange = { wanted ->
                            if (wanted && !notificationsAllowed) {
                                // The one ask Android allows, spent at the
                                // moment the user has said they want this —
                                // never on launch, when it means nothing yet.
                                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.setEnabled(wanted, onRotaChanged)
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Shift reminders"
                        },
                    )
                }
            }

            if (state.settings.enabled) {
                Spacer(Modifier.height(10.dp))
                Card {
                    Text("How much warning", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    ChoiceRow(
                        choices = LEAD_CHOICES,
                        selected = state.settings.leadMinutes,
                        onSelect = { viewModel.setLeadMinutes(it, onRotaChanged) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Shifts with no set time are announced the evening before.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.workingShifts.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Card {
                        Text("Which shifts", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        state.workingShifts.forEach { shift ->
                            ShiftToggle(
                                shift = shift,
                                on = shift.id !in state.settings.mutedShiftTypeIds,
                                onChange = { on ->
                                    viewModel.setShiftMuted(shift.id, !on, onRotaChanged)
                                },
                            )
                        }
                    }
                }

                if (!notificationsAllowed && permissionAsked) {
                    Spacer(Modifier.height(10.dp))
                    Warning(
                        title = "Notifications are blocked",
                        body = "Turnus can set the reminders but Android will not show them. " +
                            "Allow notifications to get them.",
                        action = "Open notification settings",
                        onAction = { context.openNotificationSettings() },
                    )
                }

                if (!exactAllowed) {
                    Spacer(Modifier.height(10.dp))
                    // "Up to an hour", not "a few minutes". On a real device the
                    // system gives an inexact alarm a one-hour window, so
                    // someone who asked for an hour's notice can be told as
                    // their shift starts. Understating that would be the kind
                    // of reassurance that makes a person miss work.
                    Warning(
                        title = "Reminders may arrive late",
                        body = "Android is batching this app's alarms to save battery, so a " +
                            "reminder can arrive up to an hour after the time you asked for. " +
                            "Allowing exact alarms gets them on time.",
                        action = "Allow exact alarms",
                        onAction = { context.openExactAlarmSettings() },
                    )
                }

                Spacer(Modifier.height(10.dp))
                // Not a warning, because it is not a fault — but on several
                // manufacturers it is the single reason reminders never arrive,
                // and no amount of correct code on this side fixes it.
                Warning(
                    title = "Reminders not arriving at all?",
                    body = "Some phones — Xiaomi, Samsung, Oppo, Vivo, OnePlus and others — " +
                        "stop background apps to save battery, which cancels reminders. " +
                        "Allow Turnus to run in the background and turn off battery " +
                        "optimisation for it.",
                    action = "Open app settings",
                    onAction = { context.openAppSettings() },
                )
            }

            Spacer(Modifier.height(18.dp))
            Text("Your shifts", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Card {
                Text("Names, colours and hours", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Reminders and the exported calendar use these times. If your " +
                        "shift starts at a different hour, change it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onEditShifts) { Text("Edit shifts") }
            }

            Spacer(Modifier.height(18.dp))
            Text("Your rota", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Card {
                Text(
                    state.patternName.ifBlank { "Your rota" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        if (state.cycleLength > 0) append(state.cycleLength).append("-day cycle")
                        state.todayLabel?.let {
                            if (isNotEmpty()) append(" · ")
                            append("today is ").append(it)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                Text("Out by a day?", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Move the whole rota without touching anything you have " +
                        "already changed. Watch the line above as you tap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !backup.busy && state.cycleLength > 0,
                        onClick = { viewModel.nudge(-1, onRotaChanged) },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Move my rota back one day" },
                    ) {
                        Text("‹  A day back")
                    }
                    OutlinedButton(
                        enabled = !backup.busy && state.cycleLength > 0,
                        onClick = { viewModel.nudge(1, onRotaChanged) },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Move my rota forward one day" },
                    ) {
                        Text("A day on  ›")
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Changed jobs or teams?", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Pick a different rota or build a new one. Your shifts, your " +
                        "changed days and your notes all stay.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onChangeRota) { Text("Change my rota") }
            }

            Spacer(Modifier.height(18.dp))
            Text("Share and export", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Card {
                Text("Send your rota to a calendar", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "A calendar file covering the next year. Open it in Google " +
                        "Calendar, or send it to whoever needs to know when you work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    enabled = !exporting,
                    onClick = {
                        exporting = true
                        scope.launch {
                            // Failures are shown, not swallowed: an export that
                            // silently does nothing looks like a broken button.
                            runCatching { viewModel.exportIcs(context) }
                                .onSuccess { context.startActivity(it) }
                                .onFailure {
                                    snackbarHostState.showSnackbar(
                                        it.message ?: "Could not export your rota",
                                    )
                                }
                            exporting = false
                        }
                    },
                ) {
                    Text(if (exporting) "Preparing…" else "Export to calendar")
                }
            }

            Spacer(Modifier.height(10.dp))
            Card {
                Text("Send your rota to a workmate", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Everyone on your shift works the same rotation. Send them a " +
                        "code and they can have it in seconds instead of typing " +
                        "it in themselves.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !backup.busy,
                        onClick = {
                            scope.launch {
                                runCatching { viewModel.shareIntent() }
                                    .onSuccess { context.startActivity(it) }
                                    .onFailure {
                                        snackbarHostState.showSnackbar(
                                            it.message ?: "Could not share your rota",
                                        )
                                    }
                            }
                        },
                    ) {
                        Text("Send my rota")
                    }
                    OutlinedButton(
                        enabled = !backup.busy,
                        onClick = viewModel::startEnteringCode,
                    ) {
                        Text("I have a code")
                    }
                }
                if (backup.canUndoImport) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        enabled = !backup.busy,
                        onClick = { viewModel.undo(UndoKind.LastImport, context, onRotaChanged) },
                    ) {
                        Text("Go back to my old rota")
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Backup", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Card {
                Text("Save a copy of your rota", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Turnus keeps your rota on this phone and nowhere else, so a " +
                        "new phone starts empty. Save a file now and you can put " +
                        "everything back later — your pattern, your shifts, every " +
                        "day you have changed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !backup.busy,
                        onClick = { saveBackup.launch(RotaBackupFile.suggestedName()) },
                    ) {
                        Text(if (backup.busy) "Working…" else "Save a backup")
                    }
                    OutlinedButton(
                        enabled = !backup.busy,
                        // Unfiltered: providers disagree about what a .json file
                        // is called, and a filter that hides the user's own
                        // backup is worse than one that shows too much.
                        onClick = { openBackup.launch(arrayOf("*/*")) },
                    ) {
                        Text("Restore")
                    }
                }
                if (backup.canUndo) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Your rota from just before the last restore is still saved " +
                            "on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        enabled = !backup.busy,
                        onClick = { viewModel.undo(UndoKind.LastRestore, context, onRotaChanged) },
                    ) {
                        Text("Undo the last restore")
                    }
                }
            }

            // Outside the reminders block: consent has nothing to do with
            // whether the user wants reminders, and burying the only way to
            // withdraw it behind an unrelated switch would not be offering it.
            val activity = remember(context) { context.findActivity() }
            if (activity != null && remember(activity) { AdGate.privacyOptionsRequired(activity) }) {
                Spacer(Modifier.height(18.dp))
                Text("Privacy", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(10.dp))
                Card {
                    Text("Ad privacy choices", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Turnus is free because of the adverts on the calendar and " +
                            "the year view. You can change what you agreed to at any time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { AdGate.showPrivacyOptions(activity) }) {
                        Text("Change my choices")
                    }
                }
            }

            // Last on the screen, and the only destructive thing on it. Placed
            // below the privacy section rather than beside the backup buttons,
            // so a thumb reaching for "Save a backup" cannot land on it.
            Spacer(Modifier.height(18.dp))
            Text("Start again", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Card {
                Text("Delete everything on this phone", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Removes your rota, your shifts, every day you have changed " +
                        "and your notes. Use it if you are handing this phone on, " +
                        "or starting somewhere new.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    enabled = !backup.busy,
                    onClick = viewModel::askToDeleteEverything,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete everything")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * The last thing between a picked file and the user's whole rota.
 *
 * It describes the *contents* of the backup rather than asking "are you sure?".
 * A file picker shows names, and names are the least reliable thing about a
 * file — someone with four backups needs to see which rota is in this one, not
 * be asked to confirm a decision they have no information about.
 */
@Composable
private fun RestoreConfirmation(
    summary: BackupSummary,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        // Not dismissable mid-write: the database is being replaced.
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Restore this backup?") },
        text = {
            Column {
                Text(
                    buildString {
                        append(summary.patternName ?: "A rota")
                        if (summary.cycleLength > 0) {
                            append(" — ").append(summary.cycleLength).append("-day cycle")
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(summary.shiftTypeCount).append(" shift")
                        if (summary.shiftTypeCount != 1) append("s")
                        append(", ").append(summary.changedDayCount).append(" changed day")
                        if (summary.changedDayCount != 1) append("s")
                        savedOn(summary.createdAtMillis)?.let { append("\nSaved ").append(it) }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "This replaces the rota on this phone. You can undo it straight " +
                        "afterwards if it is the wrong file.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(if (busy) "Restoring…" else "Replace my rota")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}

/**
 * Paste what a workmate sent.
 *
 * A text field rather than a clipboard read: reading the clipboard without
 * being asked shows a system warning on Android 12 and above, and an app that
 * looks through your clipboard is exactly what a rota planner should not be.
 * The field accepts the whole message, not just the code — nobody selects
 * precisely 60 characters out of a chat on a phone.
 */
@Composable
private fun CodeEntry(
    busy: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pasted by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Paste the code") },
        text = {
            Column {
                Text(
                    "Paste the whole message they sent — Turnus will find the code in it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    label = { Text("Rota code") },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(pasted) },
                enabled = !busy && pasted.isNotBlank(),
            ) {
                Text("Look at it")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/**
 * What a shared rota would do to this phone's calendar, before it does it.
 *
 * The fortnight strip is the whole point. A cycle name and a length mean
 * nothing to someone checking whether their mate sent the right rota; the days
 * they are about to be given, starting today, mean everything.
 */
@Composable
private fun ImportConfirmation(
    pending: PendingImport,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Use this rota?") },
        text = {
            Column {
                Text(
                    buildString {
                        append(pending.name.ifBlank { "A rota" })
                        if (pending.cycleLength > 0) {
                            append(" — ").append(pending.cycleLength).append("-day cycle")
                        }
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text("The next two weeks", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    pending.preview.joinToString(" ") { it ?: "·" },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (pending.newShiftCodes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        buildString {
                            append("This adds ")
                            append(pending.newShiftCodes.joinToString())
                            append(" to your shifts. They have no times yet — set them ")
                            append("in Your shifts, or reminders will not know when to fire.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your own shift times, your changed days and your notes all stay " +
                        "as they are.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(if (busy) "Setting up…" else "Use this rota")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

/**
 * When the backup was written, in the reader's own locale and zone.
 *
 * This is an audit timestamp — epoch milliseconds — not a rota date, so
 * converting it through a zone is exactly right here and would be exactly
 * wrong two files away.
 */
private fun savedOn(millis: Long): String? {
    if (millis <= 0L) return null
    return runCatching {
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }.getOrNull()
}

/**
 * The Activity behind a Compose context.
 *
 * The consent form is a dialog and cannot be shown from an application context,
 * so this has to resolve to the real thing rather than assume it.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun Warning(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun ChoiceRow(
    choices: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    // A wrapping row of chips rather than a dropdown: every option is visible,
    // which matters when the difference between them is whether someone wakes
    // up in time.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (minutes, label) ->
                    val chosen = minutes == selected
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (chosen) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (chosen) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            )
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 12.dp),
                    )
                }
                // Keeps the last row's chips the same width as the others'.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ShiftToggle(shift: ShiftStyle, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(shift.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = on,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = "Remind me about ${shift.name}" },
        )
    }
}

// ------------------------------------------------------------------- platform

private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun exactAlarmsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Falls back to the app's own settings page: not every OEM ships the
    // notification screen this intent expects, and a crash here would be a
    // dead end on the screen meant to fix a dead end.
    runCatching { startActivity(intent) }.onFailure { openAppSettings() }
}

private fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return openAppSettings()
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }.onFailure { openAppSettings() }
}
