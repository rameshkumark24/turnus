package com.turnus.rota.ui.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turnus.rota.data.RotaRepository
import com.turnus.rota.data.ShiftStyle
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.Outlook
import com.turnus.rota.engine.ResolvedDay
import com.turnus.rota.engine.ShiftEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The open day editor.
 *
 * [scheduled] is what the pattern says; [effective] is what the calendar
 * currently shows. They differ exactly when the day has been overridden, and
 * keeping both is what lets the sheet offer "restore to Day shift" naming the
 * real shift rather than a vague "reset".
 */
data class DaySheetState(
    val day: DayNumber,
    val scheduled: String?,
    val effective: String?,
    val isOverridden: Boolean,
    val hasNote: Boolean,
    val note: String,
)

/** Enough to put a day back exactly as it was, note included. */
data class UndoAction(
    val day: DayNumber,
    val hadOverride: Boolean,
    val previousShiftTypeId: String?,
    val previousOverridesShift: Boolean,
    val previousNote: String?,
    val message: String,
)

data class MonthUiState(
    val yearMonth: YearMonth,
    val firstDayOfWeek: DayOfWeek,
    /** Carried in state so the weekday labels and the column order always agree. */
    val locale: Locale,
    val days: List<ResolvedDay>,
    val styles: Map<String, ShiftStyle>,
    val today: DayNumber,
    val loading: Boolean = false,
) {
    /** The resolved cell for today, when today falls inside the visible grid. */
    val todayCell: ResolvedDay? get() = days.firstOrNull { it.day == today }

    companion object {
        fun empty(): MonthUiState {
            val now = YearMonth.now()
            val locale = Locale.getDefault()
            return MonthUiState(
                yearMonth = now,
                firstDayOfWeek = localeFirstDayOfWeek(locale),
                locale = locale,
                days = emptyList(),
                styles = emptyMap(),
                today = DayNumber.today(),
                loading = true,
            )
        }
    }
}

/**
 * First day of the week for the device's locale — Monday across most of Europe,
 * Sunday in the US, Saturday in much of the Middle East.
 *
 * This is a correctness concern, not a cosmetic one. A grid that starts on the
 * wrong day puts every shift in the wrong column, and a shift worker reading it
 * at 5am will not notice before they act on it.
 */
internal fun localeFirstDayOfWeek(locale: Locale): DayOfWeek =
    WeekFields.of(locale).firstDayOfWeek

class MonthViewModel(
    private val repository: RotaRepository,
) : ViewModel() {

    private val visibleMonth = MutableStateFlow(YearMonth.now())

    /**
     * The device locale, pushed in from the composition rather than read here.
     *
     * A ViewModel outlives the Activity that a locale change recreates, so
     * `Locale.getDefault()` captured once would keep the old answer for the rest
     * of the session. That is not a cosmetic staleness: the locale decides which
     * day the week starts on, and a grid that starts on the wrong day puts every
     * shift in the wrong column.
     */
    private val locale = MutableStateFlow(Locale.getDefault())

    fun setLocale(value: Locale) {
        locale.value = value
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<MonthUiState> = combine(visibleMonth, locale, ::Pair)
        .flatMapLatest { (month, currentLocale) ->
            val firstDay = localeFirstDayOfWeek(currentLocale)
            val (start, end) = gridRange(month, firstDay)
            // Combined inside flatMapLatest, not alongside it, so the label and
            // the cells can never belong to different months mid-swipe.
            combine(
                repository.observeCalendar(start, end),
                repository.observeShiftStyles(),
            ) { days, styles ->
                MonthUiState(
                    yearMonth = month,
                    firstDayOfWeek = firstDay,
                    locale = currentLocale,
                    days = days,
                    styles = styles,
                    today = DayNumber.today(),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MonthUiState.empty(),
        )

    /**
     * The run today sits in and the one after it, for the card under the grid.
     *
     * Kept separate from [state] rather than folded into it because it does not
     * depend on the visible month: paging to December must not change the answer
     * to "when am I next off".
     *
     * `today` is read inside the flow, not captured once. Under
     * `WhileSubscribed` the upstream is cancelled when the screen goes away and
     * restarted when it comes back, so the date refreshes on return to the
     * foreground — which is when someone who left the app open overnight looks
     * at it again.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val outlook: StateFlow<Outlook.Summary?> = flow { emit(DayNumber.today()) }
        .flatMapLatest { repository.observeOutlook(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun showPreviousMonth() {
        visibleMonth.value = visibleMonth.value.minusMonths(1)
    }

    fun showNextMonth() {
        visibleMonth.value = visibleMonth.value.plusMonths(1)
    }

    fun showToday() {
        visibleMonth.value = YearMonth.now()
    }

    /** Used when a month is chosen from the year view. */
    fun showMonth(month: YearMonth) {
        visibleMonth.value = month
    }

    // ------------------------------------------------------------- day editor

    private val _sheet = MutableStateFlow<DaySheetState?>(null)
    val sheet: StateFlow<DaySheetState?> = _sheet.asStateFlow()

    private val _undo = MutableStateFlow<UndoAction?>(null)
    val undo: StateFlow<UndoAction?> = _undo.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun openDay(day: DayNumber) {
        launchGuarded("Could not open that day") {
            val pattern = repository.activePattern() ?: return@launchGuarded
            val stored = repository.overrideFor(day)
            val scheduled = ShiftEngine.scheduled(pattern, day)
            _sheet.value = DaySheetState(
                day = day,
                scheduled = scheduled,
                // A note-only row leaves the shift following the pattern, so
                // the effective shift is the scheduled one.
                effective = if (stored?.overridesShift == true) stored.shiftTypeId else scheduled,
                isOverridden = stored?.overridesShift == true,
                hasNote = !stored?.note.isNullOrBlank(),
                note = stored?.note.orEmpty(),
            )
        }
    }

    /**
     * Runs repository work with the failure paths closed off.
     *
     * Room can raise a constraint violation from the day_override to shift_type
     * foreign key, and the repository throws when a shift was deleted between
     * the sheet opening and the tap. Uncaught inside a bare launch, either one
     * takes the process down; the user loses their place for something that
     * should have been a line of text.
     *
     * CancellationException is rethrown rather than reported — a cancelled
     * scope is not a failure, and swallowing it breaks structured concurrency.
     */
    private fun launchGuarded(message: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _error.value = failure.message ?: message
            }
        }
    }

    fun closeSheet() {
        _sheet.value = null
    }

    fun updateNote(text: String) {
        _sheet.update { it?.copy(note = text) }
    }

    /** Records a swap, sickness or overtime. A null [shiftTypeId] means off. */
    fun applyOverride(shiftTypeId: String?) = mutateDay("Day changed") { day, note ->
        repository.setOverride(day, shiftTypeId, note.ifBlank { null })
    }

    /** Drops the exception so the day follows the pattern again. */
    fun restoreScheduled() = mutateDay("Restored to pattern") { day, _ ->
        repository.clearOverride(day)
    }

    /** Attaches a note without pinning the day's shift. */
    fun saveNote() = mutateDay("Note saved") { day, note ->
        repository.setNote(day, note)
    }

    /**
     * Every edit captures what was there first, so it can be put back exactly —
     * including the note. A mis-tap here silently rewrites someone's rota, and
     * they may not notice until the day arrives.
     *
     * The captured sheet is passed into the action rather than re-read inside
     * it. Re-reading raced the sheet's own dismissal: a swipe-away during the
     * in-flight database read left the action looking at a null sheet and
     * writing the day as explicitly off.
     */
    private fun mutateDay(message: String, action: suspend (DayNumber, String) -> Unit) {
        val current = _sheet.value ?: return
        _sheet.value = null
        launchGuarded("Could not save that change") {
            val before = repository.overrideFor(current.day)
            action(current.day, current.note)
            _undo.value = UndoAction(
                day = current.day,
                hadOverride = before != null,
                previousShiftTypeId = before?.shiftTypeId,
                previousOverridesShift = before?.overridesShift ?: false,
                previousNote = before?.note,
                message = message,
            )
        }
    }

    fun performUndo() {
        val action = _undo.value ?: return
        _undo.value = null
        launchGuarded("Could not undo that") {
            when {
                action.previousOverridesShift ->
                    repository.setOverride(action.day, action.previousShiftTypeId, action.previousNote)
                action.hadOverride ->
                    // The row existed but only carried a note.
                    repository.setNote(action.day, action.previousNote)
                else ->
                    repository.clearOverride(action.day)
            }
        }
    }

    fun clearUndo() {
        _undo.value = null
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        /** Rows always rendered, so the grid never changes height between months. */
        const val WEEKS = 6
        const val CELLS = WEEKS * 7

        /**
         * The visible grid always spans a fixed six weeks, which usually reaches
         * into the neighbouring months. Those leading and trailing days are real
         * shifts and are resolved like any other — a rota does not stop at a
         * month boundary, and someone checking whether they work on the 1st
         * needs to see the 31st too.
         */
        fun gridRange(month: YearMonth, firstDayOfWeek: DayOfWeek): Pair<DayNumber, DayNumber> {
            val firstOfMonth: LocalDate = month.atDay(1)
            // Math.floorMod, not %: CLAUDE.md rule 2. The old `+ 7` pre-bias
            // happened to be safe, but it is exactly the fragile idiom the
            // rule exists to ban.
            val offset = Math.floorMod(firstOfMonth.dayOfWeek.value - firstDayOfWeek.value, 7)
            val start = firstOfMonth.minusDays(offset.toLong())
            return DayNumber.from(start) to DayNumber.from(start.plusDays((CELLS - 1).toLong()))
        }
    }
}
