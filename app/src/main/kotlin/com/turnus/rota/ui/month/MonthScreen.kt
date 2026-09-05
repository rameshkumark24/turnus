package com.turnus.rota.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turnus.rota.data.ShiftStyle
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.ResolvedDay
import com.turnus.rota.ui.theme.TurnusTokens
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

@Composable
fun MonthScreen(viewModel: MonthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Undo is the safety net for the day editor: an accidental tap rewrites a
    // shift silently, and the user may not notice until that day arrives.
    LaunchedEffect(undo) {
        val action = undo ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = action.message,
            actionLabel = "Undo",
            // Long, not Short. Short is four seconds to notice a silent edit,
            // decide it was wrong, and get a thumb to the bottom of a 6.7"
            // screen. Testing on a real phone, the window closed before the
            // action could be hit twice running.
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.performUndo()
        } else {
            viewModel.clearUndo()
        }
    }

    // Repository failures are reported rather than swallowed or fatal.
    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Long)
        viewModel.clearError()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Scaffold's inset padding already covers the status bar under
                // edge-to-edge; adding statusBarsPadding on top double-counted it
                // and left a visible gap above the month title.
                .padding(padding)
                .padding(horizontal = TurnusTokens.ScreenPadding),
        ) {
            MonthHeader(
                title = state.yearMonth.format(MONTH_TITLE),
                onPrevious = viewModel::showPreviousMonth,
                onNext = viewModel::showNextMonth,
                onToday = viewModel::showToday,
            )

            TodaySummary(state)

            Spacer(Modifier.height(10.dp))

            // Given all the remaining space rather than its natural size, so it
            // can discover how much height it actually has to fit six weeks in.
            MonthCalendar(
                state = state,
                onDayClick = viewModel::openDay,
                modifier = Modifier.weight(1f),
            )

            // The anchored banner slot lands here. Its height is reserved from
            // the start so the grid never jumps when an ad fills or fails.
            Spacer(Modifier.height(8.dp))
        }
    }

    sheet?.let { open ->
        DaySheet(
            sheet = open,
            styles = state.styles,
            onChoose = viewModel::applyOverride,
            onRestore = viewModel::restoreScheduled,
            onNoteChange = viewModel::updateNote,
            onSaveNote = viewModel::saveNote,
            onDismiss = viewModel::closeSheet,
        )
    }
}

@Composable
private fun MonthHeader(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onToday) { Text("Today") }
        StepButton(label = "‹", description = "Previous month", onClick = onPrevious)
        StepButton(label = "›", description = "Next month", onClick = onNext)
    }
}

/**
 * Arrows as text rather than vector icons: it avoids pulling in the whole
 * material-icons artifact for two glyphs, and the label is set as a content
 * description so screen readers announce the action, not the character.
 */
@Composable
private fun StepButton(label: String, description: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        // sizeIn, not size: at a 2x font scale the glyph is larger than a fixed
        // 44dp box and the arrows were sliced down to bare diagonal strokes.
        // 44dp stays the floor, for the touch target.
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodaySummary(state: MonthUiState) {
    val cell = state.todayCell
    val label = when {
        state.loading -> "Loading your rota"
        cell == null -> "Today is not in this month"
        cell.shiftTypeId == null -> "Today — off"
        else -> "Today — " + (state.styles[cell.shiftTypeId]?.name ?: "Working")
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/**
 * The month grid, sized to fit rather than sized from the width.
 *
 * Six rows at a fixed aspect ratio are only guaranteed to fit when the screen
 * is portrait-shaped. In landscape the same arithmetic made the grid two and a
 * half screens tall, and because nothing here scrolls the weeks simply drew on
 * top of one another. So the cell is measured against both axes and the whole
 * block is centred when height is the binding constraint.
 *
 * The weekday header is inside this box, not above it, because it has to line
 * up with columns whose width is decided here.
 */
@Composable
private fun MonthCalendar(
    state: MonthUiState,
    onDayClick: (DayNumber) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val gridDensity = remember(density) {
        // Only the font scale is capped. Keeping the same `density` means every
        // dp in here still measures exactly what it does everywhere else.
        Density(density.density, density.fontScale.coerceAtMost(TurnusTokens.GridFontScaleCap))
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val gap = TurnusTokens.CellGap
        val rows = MonthViewModel.WEEKS

        val widthBoundCell = (maxWidth - gap * (COLUMNS - 1)) / COLUMNS
        val naturalHeight = widthBoundCell / TurnusTokens.CellAspect
        val heightBudget = maxHeight - TurnusTokens.WeekdayRowHeight - gap * rows

        val heightBound = naturalHeight * rows > heightBudget
        val cellHeight = if (heightBound) heightBudget / rows else naturalHeight
        val cellWidth = if (heightBound) cellHeight * TurnusTokens.CellAspect else widthBoundCell

        CompositionLocalProvider(LocalDensity provides gridDensity) {
            Column(
                modifier = Modifier.width(cellWidth * COLUMNS + gap * (COLUMNS - 1)),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                WeekdayHeader(state)
                repeat(rows) { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        repeat(COLUMNS) { column ->
                            val index = week * COLUMNS + column
                            DayCell(
                                cell = state.days.getOrNull(index),
                                state = state,
                                onClick = onDayClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(cellHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader(state: MonthUiState) {
    val locale = Locale.getDefault()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // A known height, so the grid above can budget for it honestly.
            .height(TurnusTokens.WeekdayRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(COLUMNS) { index ->
            val day = state.firstDayOfWeek.plus(index.toLong())
            Text(
                text = day.getDisplayName(JavaTextStyle.SHORT, locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    // The row is decoration; the cells carry the real labels.
                    .clearAndSetSemantics { },
            )
        }
    }
}

@Composable
private fun DayCell(
    cell: ResolvedDay?,
    state: MonthUiState,
    onClick: (DayNumber) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cell == null) {
        Box(modifier)
        return
    }

    val date = cell.day.toLocalDate()
    val inMonth = date.month == state.yearMonth.month && date.year == state.yearMonth.year
    val isToday = cell.day == state.today
    val style: ShiftStyle? = cell.shiftTypeId?.let { state.styles[it] }

    val background = when {
        style != null -> Color(style.color)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    // User-assigned shift colours are arbitrary, so the label colour is derived
    // from the background's luminance rather than fixed. Otherwise a pale
    // shift colour renders white-on-white and the day is simply unreadable.
    val foreground = when {
        style != null -> if (background.luminance() > 0.5f) Color(0xFF14202A) else Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val description = buildString {
        append(date.format(CELL_ANNOUNCE))
        append(", ")
        append(style?.name ?: "off")
        // The dot means the SHIFT changed: note-only rows are filtered out
        // before the engine sees them, so they never reach a ResolvedDay.
        if (cell.isOverridden) append(", changed")
        if (isToday) append(", today")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(TurnusTokens.CellCorner))
            .background(background.copy(alpha = if (inMonth) 1f else 0.32f))
            .then(
                if (isToday) {
                    // Derived from the cell's own luminance, like the label, not
                    // painted in the accent. The accent ring was petrol on a
                    // dark blue night shift in light mode — all but invisible,
                    // on the one cell the whole screen is organised around. A
                    // shift colour is user-chosen, so the only colour certain to
                    // contrast with it is one computed from it.
                    //
                    // Inset, so the ring has cell colour on both sides. Drawn on
                    // the outer edge it contrasted with the cell but vanished
                    // into the page behind it, which in light mode left today
                    // looking like a slightly smaller square.
                    Modifier
                        .padding(TurnusTokens.TodayRingInset)
                        .border(
                            width = TurnusTokens.TodayRingWidth,
                            color = foreground.copy(alpha = if (inMonth) 1f else 0.6f),
                            shape = RoundedCornerShape(TurnusTokens.CellCorner),
                        )
                } else {
                    Modifier
                }
            )
            .clickable { onClick(cell.day) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = foreground.copy(alpha = if (inMonth) 1f else 0.6f),
            )
            if (style != null) {
                Text(
                    text = style.code,
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground.copy(alpha = if (inMonth) 0.85f else 0.5f),
                )
            }
        }

        if (cell.isOverridden) {
            // A changed day has to be distinguishable without relying on colour.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(foreground.copy(alpha = 0.75f)),
            )
        }
    }
}

private const val COLUMNS = 7

private val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy")
private val CELL_ANNOUNCE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
