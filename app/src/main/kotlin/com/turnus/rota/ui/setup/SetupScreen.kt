package com.turnus.rota.ui.setup

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turnus.rota.data.ShiftStyle
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.ui.theme.TurnusTokens
import java.time.format.DateTimeFormatter

/**
 * The setup wizard.
 *
 * Held as internal state rather than a navigation graph on purpose: a wizard
 * needs custom back semantics and must be *replaced* on completion, not popped,
 * so that back from the calendar can never re-enter onboarding. Modelling that
 * as a linear state machine is simpler than fighting a back stack into the same
 * shape.
 */
@Composable
fun SetupScreen(viewModel: SetupViewModel, onComplete: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(enabled = state.step != SetupStep.Welcome) { viewModel.back() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Box(Modifier.weight(1f)) {
            when (state.step) {
                SetupStep.Welcome -> WelcomeStep(onBegin = viewModel::begin)
                SetupStep.ChoosePattern -> ChoosePatternStep(state, viewModel)
                SetupStep.BuildCustom -> BuildCustomStep(state, viewModel)
                SetupStep.ChooseShiftToday -> ChooseShiftTodayStep(state, viewModel)
                SetupStep.ResolveAmbiguity -> ResolveAmbiguityStep(state, viewModel)
                SetupStep.Confirm -> ConfirmStep(state, viewModel, onComplete)
            }
        }

        state.error?.let { message ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::dismissError) { Text("OK") }
            }
        }
    }
}

// ------------------------------------------------------------------- welcome

@Composable
private fun WelcomeStep(onBegin: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Turnus", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Text(
            "Set your rotation once. See your whole year, get reminded before " +
                "every shift, and share it with whoever needs to know.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onBegin, modifier = Modifier.fillMaxWidth()) {
            Text("Set up my rota")
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Everything stays on this phone. No account, no sign-up.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ----------------------------------------------------------- choose pattern

@Composable
private fun ChoosePatternStep(state: SetupUiState, viewModel: SetupViewModel) {
    StepColumn(
        title = "Which rota do you work?",
        subtitle = "Pick the closest match. You can change any day afterwards.",
    ) {
        state.presets.forEach { preset ->
            Card(
                onClick = { viewModel.choosePreset(preset) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(preset.displayName, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${preset.cycleLength}-day cycle · " +
                            "${preset.slots.count { it != null }} working days",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(9.dp))
                    SlotStrip(preset.slots, state.styleById)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = viewModel::startCustom, modifier = Modifier.fillMaxWidth()) {
            Text("Mine is different — build it")
        }
    }
}

// ------------------------------------------------------------ custom builder

@Composable
private fun BuildCustomStep(state: SetupUiState, viewModel: SetupViewModel) {
    StepColumn(
        title = "Build your cycle",
        subtitle = "Tap a day to change what you work. The pattern repeats from the start.",
    ) {
        Text(
            "${state.cycleLength}-day cycle · ${state.workingDays} working days",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        // Wraps naturally at seven per row, so a fortnightly cycle reads as two
        // weeks rather than one long line.
        state.slots.chunked(7).forEachIndexed { rowIndex, row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = TurnusTokens.CellGap),
                horizontalArrangement = Arrangement.spacedBy(TurnusTokens.CellGap),
            ) {
                row.forEachIndexed { columnIndex, slot ->
                    val index = rowIndex * 7 + columnIndex
                    SlotChip(
                        slot = slot,
                        styles = state.styleById,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clickable { viewModel.cycleSlot(index) },
                    )
                }
                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::removeSlot, modifier = Modifier.weight(1f)) {
                Text("Shorter")
            }
            OutlinedButton(onClick = viewModel::addSlot, modifier = Modifier.weight(1f)) {
                Text("Longer")
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = viewModel::confirmCustom, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}

// ------------------------------------------------------- which shift today

@Composable
private fun ChooseShiftTodayStep(state: SetupUiState, viewModel: SetupViewModel) {
    StepColumn(
        title = "What are you on today?",
        subtitle = "This is all we need to line the rota up with your calendar.",
    ) {
        state.styles.forEach { style ->
            // Only offer shifts the chosen cycle actually contains, so the user
            // is never presented with an answer that cannot be right.
            if (state.slots.contains(style.id)) {
                Button(
                    onClick = { viewModel.chooseTodaysShift(style.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text("${style.name} shift")
                }
            }
        }
        if (state.slots.contains(null)) {
            OutlinedButton(
                onClick = viewModel::chooseOffToday,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Text("I'm off today")
            }
        }
    }
}

// -------------------------------------------------------------- ambiguity

@Composable
private fun ResolveAmbiguityStep(state: SetupUiState, viewModel: SetupViewModel) {
    StepColumn(
        title = "Which week is yours?",
        subtitle = "That shift happens more than once in your cycle. Pick the row " +
            "that matches the next seven days.",
    ) {
        state.candidates.forEach { candidate ->
            Card(
                onClick = { viewModel.resolveAmbiguity(candidate) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Starting today",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    SlotStrip(candidate.preview, state.styleById)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- confirm

@Composable
private fun ConfirmStep(state: SetupUiState, viewModel: SetupViewModel, onComplete: () -> Unit) {
    val anchor = state.anchor
    StepColumn(
        title = "Does this look right?",
        subtitle = "The next two weeks, starting today. If it is out by a day you " +
            "can nudge it later without redoing any of this.",
    ) {
        SlotStrip(state.confirmationPreview().take(7), state.styleById)
        Spacer(Modifier.height(TurnusTokens.CellGap))
        SlotStrip(state.confirmationPreview().drop(7), state.styleById)

        Spacer(Modifier.height(16.dp))
        Text(
            buildString {
                append(state.patternName)
                append(" · ")
                append(state.cycleLength)
                append("-day cycle")
                if (anchor != null) {
                    append(" · starts ")
                    append(anchor.toLocalDate().format(ANCHOR_FORMAT))
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.finish(onComplete) },
            enabled = !state.saving && anchor != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.saving) "Saving..." else "That's my rota")
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { viewModel.back() }, modifier = Modifier.fillMaxWidth()) {
            Text("Not quite — go back")
        }
    }
}

// ----------------------------------------------------------------- pieces

@Composable
private fun StepColumn(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        content()
        Spacer(Modifier.height(24.dp))
    }
}

/** A row of days, used for previews. Read-only. */
@Composable
private fun SlotStrip(slots: List<String?>, styles: Map<String, ShiftStyle>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TurnusTokens.CellGap),
    ) {
        slots.forEach { slot ->
            SlotChip(slot, styles, Modifier.weight(1f).height(34.dp))
        }
        repeat((7 - slots.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun SlotChip(slot: String?, styles: Map<String, ShiftStyle>, modifier: Modifier = Modifier) {
    val style = slot?.let { styles[it] }
    val background = if (style != null) {
        Color(style.color)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    // Shift colours are user-assigned, so the label colour is derived rather
    // than fixed — a pale colour would otherwise be unreadable.
    val foreground = if (style != null) {
        if (background.luminance() > 0.5f) Color(0xFF14202A) else Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(TurnusTokens.CellCorner))
            .background(background)
            .semantics { contentDescription = style?.name ?: "Off" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = style?.code ?: "–",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            textAlign = TextAlign.Center,
        )
    }
}

private val ANCHOR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
