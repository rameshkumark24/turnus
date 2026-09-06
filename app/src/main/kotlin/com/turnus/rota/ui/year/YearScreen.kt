package com.turnus.rota.ui.year

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.turnus.rota.ads.NativeSlot
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.ui.theme.TurnusTokens
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle

/**
 * The whole year at a glance.
 *
 * The reason this screen exists is one question: "when can I take a holiday?"
 * A month grid answers it badly, because the useful runs of days off are the
 * ones that straddle a month boundary and a month view cannot show both sides.
 *
 * Days are drawn as colour blocks with no numbers. At the size twelve months
 * need, a numeral is either unreadable or crowds out the colour, and the colour
 * is the thing being scanned — a long break reads as a gap in the pattern. The
 * date itself is one tap away in the month view.
 */
@Composable
fun YearScreen(
    viewModel: YearViewModel,
    onOpenMonth: (YearMonth) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Same reason as the month grid: the ViewModel outlives the recreation a
    // language change causes, so the locale has to be pushed in. It decides
    // both the weekday the columns start on and the month abbreviations.
    val locale = LocalLocale.current.platformLocale
    LaunchedEffect(locale) { viewModel.setLocale(locale) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = TurnusTokens.ScreenPadding),
        ) {
            YearHeader(
                year = state.year,
                onPrevious = viewModel::showPreviousYear,
                onNext = viewModel::showNextYear,
                onThisYear = viewModel::showThisYear,
                onBack = onBack,
            )

            BoxWithConstraints(Modifier.weight(1f)) {
                // Columns from the width available rather than a fixed three.
                // Three is right on a portrait phone, but on a landscape screen
                // it blows each day block up to thumbnail size and leaves two
                // months on screen — the opposite of what a year view is for.
                val columns = (maxWidth / TARGET_MONTH_WIDTH).toInt().coerceIn(2, 6)

                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    (1..12).chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { month ->
                                MiniMonth(
                                    month = YearMonth.of(state.year, month),
                                    state = state,
                                    onClick = { onOpenMonth(viewModel.monthAt(month)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // Keeps a short final row the same cell size as the rest.
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    YearSummary(state)

                    // No reserved height, unlike the banner under the month
                    // grid. That one sits above nothing, so a late fill would
                    // shift the day a finger is already moving towards; this
                    // one is the last thing in a scrolling column, so it can
                    // appear late and move nothing. Reserving would only leave
                    // a blank rectangle for everyone who declines consent or is
                    // served nothing.
                    Spacer(Modifier.height(8.dp))
                    NativeSlot()
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * The two facts the grid cannot state, only imply.
 *
 * Counting shifts across twelve months is not something anyone should do by
 * eye, and the longest break is the whole reason to open this screen — it is
 * also the hardest run to spot, because the good ones straddle a month
 * boundary and are drawn as two separate shapes.
 */
@Composable
private fun YearSummary(state: YearUiState) {
    if (state.days.isEmpty()) return
    val best = state.longestBreak

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .semantics(mergeDescendants = true) { },
    ) {
        Text(
            text = "${state.workingDays} working days in ${state.year}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Hours on their own line rather than appended to the days: this is a
        // different question — days off versus pay — and running them together
        // makes one long line that reads as neither.
        val hours = state.hours
        if (hours.minutes > 0) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append(hours.wholeHours)
                    append(if (hours.wholeHours == 1) " hour" else " hours")
                    // Only when it matters. A year is dominated by whole hours
                    // and the odd half-shift does not deserve equal billing.
                    if (hours.minutesPastTheHour != 0) {
                        append(" ").append(hours.minutesPastTheHour).append(" min")
                    }
                    if (!hours.isComplete) append(", not counting shifts with no times")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (best != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append("Longest break: ")
                    append(best.length)
                    append(if (best.length == 1) " day from " else " days from ")
                    append(best.start.toLocalDate().format(BREAK_DATE))
                    // A run touching the edge of the year may well continue
                    // into the next one, so its length is a floor, not a fact.
                    if (!best.complete) append(", carrying over the year end")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun YearHeader(
    year: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onThisYear: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onThisYear) { Text("This year") }
        YearStepButton("‹", "Previous year", onPrevious)
        YearStepButton("›", "Next year", onNext)
        YearStepButton("▦", "Back to month", onBack)
    }
}

@Composable
private fun YearStepButton(label: String, description: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        // sizeIn rather than a fixed size, so a large font scale grows the
        // glyph instead of slicing it.
        modifier = Modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MiniMonth(
    month: YearMonth,
    state: YearUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val capped = remember(density) {
        // Same cap as the month grid, for the same reason: twelve months have
        // to fit at once, so the label cannot grow without limit.
        Density(density.density, density.fontScale.coerceAtMost(TurnusTokens.GridFontScaleCap))
    }

    val label = month.month.getDisplayName(JavaTextStyle.SHORT, state.locale)
    val workingDays = remember(month, state.days, state.start) { countWorking(month, state) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(2.dp)
            // One announcement for the whole month. Forty-two unlabelled colour
            // blocks would otherwise be forty-two stops for a screen reader,
            // none of which says anything.
            .semantics(mergeDescendants = true) {
                contentDescription = "$label, $workingDays working days"
            },
    ) {
        CompositionLocalProvider(LocalDensity provides capped) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(bottom = 3.dp)
                    .clearAndSetSemantics { },
            )
            MonthBlocks(month, state)
        }
    }
}

@Composable
private fun MonthBlocks(month: YearMonth, state: YearUiState) {
    val firstOfMonth: LocalDate = month.atDay(1)
    // Math.floorMod, not %: CLAUDE.md rule 2. A locale starting the week on
    // Saturday makes this difference negative.
    val offset = Math.floorMod(firstOfMonth.dayOfWeek.value - state.firstDayOfWeek.value, 7)
    val length = month.lengthOfMonth()
    val rows = (offset + length + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                repeat(7) { column ->
                    val dayOfMonth = row * 7 + column - offset + 1
                    DayBlock(
                        day = if (dayOfMonth in 1..length) {
                            DayNumber.from(firstOfMonth.withDayOfMonth(dayOfMonth))
                        } else {
                            null
                        },
                        state = state,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayBlock(day: DayNumber?, state: YearUiState, modifier: Modifier) {
    if (day == null) {
        // Days belonging to the neighbouring month are left empty rather than
        // dimmed, so each month reads as its own shape.
        Box(modifier)
        return
    }

    val cell = state.dayAt(day)
    val style = cell?.shiftTypeId?.let { state.styles[it] }
    val background = when {
        style != null -> Color(style.color)
        // Off days are the quietest thing on screen on purpose: a holiday is
        // found by looking for the gaps, so the gaps have to read as gaps.
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }

    Box(
        modifier
            .clip(RoundedCornerShape(2.dp))
            .background(background)
            .then(
                if (day == state.today) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.onBackground,
                        shape = RoundedCornerShape(2.dp),
                    )
                } else {
                    Modifier
                }
            ),
    )
}

private fun countWorking(month: YearMonth, state: YearUiState): Int =
    (1..month.lengthOfMonth()).count { dayOfMonth ->
        state.dayAt(DayNumber.from(month.atDay(dayOfMonth)))?.isWorking == true
    }

private val BREAK_DATE: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")

/** Roughly the narrowest a month can be and still read as a month. */
private val TARGET_MONTH_WIDTH = 120.dp
private val GAP = 1.5.dp
