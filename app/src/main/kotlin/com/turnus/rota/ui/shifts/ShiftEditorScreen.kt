package com.turnus.rota.ui.shifts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turnus.rota.ui.theme.TurnusTokens
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The palette offered for a shift. Enough to tell shifts apart, few enough to choose from. */
private val COLORS = listOf(
    0xFFE0A32E, 0xFF4C6E9C, 0xFF2E9B8F, 0xFFA85C8B,
    0xFFB4553C, 0xFF6B8E3D, 0xFF7A6BC4, 0xFF6B7A87,
).map { it.toInt() }

@Composable
fun ShiftEditorScreen(viewModel: ShiftEditorViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
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
            Text("Your shifts", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Reminders and the exported calendar use these times, so they are " +
                    "worth getting right.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            state.shifts.forEach { shift ->
                ShiftRow(shift = shift, onClick = { viewModel.edit(shift) })
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = viewModel::addNew, modifier = Modifier.fillMaxWidth()) {
                Text("Add a shift")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    state.draft?.let { draft ->
        DraftSheet(
            draft = draft,
            saving = state.saving,
            onChange = viewModel::updateDraft,
            onSave = viewModel::save,
            onDelete = {
                state.shifts.firstOrNull { it.id == draft.id }?.let(viewModel::delete)
            },
            onDismiss = viewModel::cancel,
        )
    }
}

@Composable
private fun ShiftRow(shift: EditableShift, onClick: () -> Unit) {
    val fill = Color(shift.color)
    val onFill = if (fill.luminance() > 0.5f) Color(0xFF14202A) else Color.White

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${shift.name}, ${describe(shift)}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(TurnusTokens.CellCorner))
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = shift.code,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = onFill,
            )
        }
        Spacer(Modifier.height(0.dp))
        Column(Modifier.padding(start = 12.dp)) {
            Text(shift.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = describe(shift),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftSheet(
    draft: ShiftDraft,
    saving: Boolean,
    onChange: ((ShiftDraft) -> ShiftDraft) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = if (draft.isNew) "New shift" else "Edit shift",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft.code,
                    onValueChange = { typed ->
                        // One or two characters: this is what fits in a calendar
                        // cell, and a longer code is silently clipped there.
                        onChange { it.copy(code = typed.take(2)) }
                    },
                    label = { Text("Letter") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { typed -> onChange { it.copy(name = typed.take(24)) } },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(2.4f),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("Colour", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                COLORS.forEach { option ->
                    val chosen = option == draft.color
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(option))
                            .then(
                                if (chosen) {
                                    Modifier
                                        .padding(3.dp)
                                        .border(
                                            width = 2.dp,
                                            color = if (Color(option).luminance() > 0.5f) {
                                                Color(0xFF14202A)
                                            } else {
                                                Color.White
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                        )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onChange { it.copy(color = option) } },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Has set hours", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Turn off if you record the shift but not when it runs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = draft.timed,
                    onCheckedChange = { on -> onChange { it.copy(timed = on) } },
                    modifier = Modifier.semantics { contentDescription = "Has set hours" },
                )
            }

            if (draft.timed) {
                Spacer(Modifier.height(14.dp))
                Stepper(
                    label = "Starts",
                    value = formatMinutes(draft.startMinute),
                    onDown = { onChange { it.copy(startMinute = shift(it.startMinute, -STEP)) } },
                    onUp = { onChange { it.copy(startMinute = shift(it.startMinute, STEP)) } },
                )
                Spacer(Modifier.height(10.dp))
                Stepper(
                    label = "Length",
                    value = formatLength(draft.durationMinute),
                    onDown = {
                        onChange { it.copy(durationMinute = (it.durationMinute - STEP).coerceAtLeast(STEP)) }
                    },
                    onUp = {
                        onChange { it.copy(durationMinute = (it.durationMinute + STEP).coerceAtMost(1440)) }
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Ends " + formatMinutes((draft.startMinute + draft.durationMinute) % 1440) +
                        if (draft.startMinute + draft.durationMinute >= 1440) " the next day" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Saving…" else "Save")
            }

            if (!draft.isNew) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete this shift")
                }
            }
        }
    }
}

/**
 * Steppers rather than a time picker dialog.
 *
 * A shift start is almost always a round time being nudged from a default, and
 * two taps beats opening a dialog, scrolling a clock face and confirming.
 */
@Composable
private fun Stepper(label: String, value: String, onDown: () -> Unit, onUp: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onDown,
            modifier = Modifier.semantics { contentDescription = "$label earlier" },
        ) { Text("−") }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        OutlinedButton(
            onClick = onUp,
            modifier = Modifier.semantics { contentDescription = "$label later" },
        ) { Text("+") }
    }
}

/** Wraps around the clock, so stepping back from 00:00 reaches 23:30. */
private fun shift(minute: Int, by: Int): Int = Math.floorMod(minute + by, 1440)

private const val STEP = 30

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatMinutes(minute: Int): String =
    LocalTime.MIDNIGHT.plusMinutes(minute.toLong()).format(TIME)

private fun formatLength(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "${hours}h" else "${hours}h ${rest}m"
}

private fun describe(shift: EditableShift): String = when {
    shift.startMinute != null && shift.durationMinute != null ->
        formatMinutes(shift.startMinute) + " – " +
            formatMinutes((shift.startMinute + shift.durationMinute) % 1440) +
            " · " + formatLength(shift.durationMinute)
    else -> "No set hours"
}
