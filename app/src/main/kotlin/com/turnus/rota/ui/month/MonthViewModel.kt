package com.turnus.rota.ui.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turnus.rota.data.RotaRepository
import com.turnus.rota.data.ShiftStyle
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.ResolvedDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.util.Locale

data class MonthUiState(
    val yearMonth: YearMonth,
    val firstDayOfWeek: DayOfWeek,
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
            return MonthUiState(
                yearMonth = now,
                firstDayOfWeek = localeFirstDayOfWeek(),
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
internal fun localeFirstDayOfWeek(): DayOfWeek =
    WeekFields.of(Locale.getDefault()).firstDayOfWeek

class MonthViewModel(
    private val repository: RotaRepository,
) : ViewModel() {

    private val visibleMonth = MutableStateFlow(YearMonth.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<MonthUiState> = visibleMonth
        .flatMapLatest { month ->
            val firstDay = localeFirstDayOfWeek()
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

    fun showPreviousMonth() {
        visibleMonth.value = visibleMonth.value.minusMonths(1)
    }

    fun showNextMonth() {
        visibleMonth.value = visibleMonth.value.plusMonths(1)
    }

    fun showToday() {
        visibleMonth.value = YearMonth.now()
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
            val offset = ((firstOfMonth.dayOfWeek.value - firstDayOfWeek.value) + 7) % 7
            val start = firstOfMonth.minusDays(offset.toLong())
            return DayNumber.from(start) to DayNumber.from(start.plusDays((CELLS - 1).toLong()))
        }
    }
}
