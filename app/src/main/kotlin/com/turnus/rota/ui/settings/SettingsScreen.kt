package com.turnus.rota.ui.settings

import android.Manifest
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.turnus.rota.data.ShiftStyle
import com.turnus.rota.ui.theme.TurnusTokens

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
    onRemindersChanged: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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
        if (!granted) viewModel.setEnabled(false, onRemindersChanged)
    }

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
        viewModel.clearError()
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
                            viewModel.setEnabled(wanted, onRemindersChanged)
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
                        onSelect = { viewModel.setLeadMinutes(it, onRemindersChanged) },
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
                                    viewModel.setShiftMuted(shift.id, !on, onRemindersChanged)
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

            Spacer(Modifier.height(24.dp))
        }
    }
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
