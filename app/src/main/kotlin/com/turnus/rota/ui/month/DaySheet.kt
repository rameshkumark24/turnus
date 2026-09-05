package com.turnus.rota.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.turnus.rota.data.ShiftStyle
import com.turnus.rota.ui.theme.TurnusTokens
import java.time.format.DateTimeFormatter

/**
 * The day editor: swaps, sickness, leave, overtime.
 *
 * Choosing a shift applies it immediately and closes — this is an app people
 * open for ten seconds, and a Save button on a single-choice screen is a step
 * that earns nothing. The safety net is the undo snackbar, not a confirmation
 * dialog, because the common case is a correct tap and the rare case is
 * recoverable.
 *
 * The pattern itself is never touched. An override is a separate row layered
 * over it, which is why a swapped day can be undone without disturbing the
 * fifty years of calendar either side of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    sheet: DaySheetState,
    styles: Map<String, ShiftStyle>,
    onChoose: (String?) -> Unit,
    onRestore: () -> Unit,
    onNoteChange: (String) -> Unit,
    onSaveNote: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheduledStyle = sheet.scheduled?.let { styles[it] }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = sheet.day.toLocalDate().format(SHEET_DATE),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    sheet.isOverridden && scheduledStyle != null ->
                        "Changed. Your rota says ${scheduledStyle.name}."
                    sheet.isOverridden ->
                        "Changed. Your rota says this is a day off."
                    else -> "Following your rota"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(18.dp))
            Text(
                "Working",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(TurnusTokens.CellGap)) {
                styles.values
                    .filter { it.isWorking }
                    .sortedBy { it.code }
                    .forEach { style ->
                        ShiftOption(
                            style = style,
                            selected = sheet.effective == style.id,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clickable { onChoose(style.id) },
                        )
                    }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { onChoose(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (sheet.effective == null) "Off (current)" else "Mark as a day off")
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = sheet.note,
                onValueChange = onNoteChange,
                label = { Text("Note") },
                placeholder = { Text("Swapped with Priya, overtime, sick...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onSaveNote, modifier = Modifier.fillMaxWidth()) {
                Text("Save note")
            }

            if (sheet.isOverridden) {
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                    // Names the shift rather than saying "reset", so the button
                    // says what will actually happen.
                    Text(
                        scheduledStyle?.let { "Put back to ${it.name}" }
                            ?: "Put back to a day off",
                    )
                }
            }
        }
    }
}

@Composable
private fun ShiftOption(
    style: ShiftStyle,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = Color(style.color)
    val foreground = if (background.luminance() > 0.5f) Color(0xFF14202A) else Color.White

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(TurnusTokens.CellCorner))
            .background(background)
            .semantics {
                contentDescription = if (selected) "${style.name}, current" else style.name
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = style.code,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = foreground,
            )
            Text(
                text = style.name,
                style = MaterialTheme.typography.labelSmall,
                color = foreground.copy(alpha = 0.85f),
            )
        }
    }
}

private val SHEET_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
