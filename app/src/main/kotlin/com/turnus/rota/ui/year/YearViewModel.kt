package com.turnus.rota.ui.year

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.turnus.rota.data.RotaRepository
import com.turnus.rota.data.ShiftStyle
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.Outlook
import com.turnus.rota.engine.ResolvedDay
import com.turnus.rota.ui.month.localeFirstDayOfWeek
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
import java.util.Locale

/**
 * A whole year of shifts.
 *
 * [days] is a flat list starting at 1 January, indexed by offset rather than
 * held in a map. A year is 365 entries looked up 365 times on every frame of a
 * scroll; arithmetic on a list is free where rebuilding and hashing a map is
 * not.
 */
data class YearUiState(
    val year: Int,
    val firstDayOfWeek: DayOfWeek,
    val locale: Locale,
    val start: DayNumber,
    val days: List<ResolvedDay>,
    val styles: Map<String, ShiftStyle>,
    val today: DayNumber,
    val loading: Boolean = false,
) {
    fun dayAt(day: DayNumber): ResolvedDay? =
        days.getOrNull((day.value - start.value).toInt())

    val workingDays: Int get() = days.count(ResolvedDay::isWorking)

    /**
     * Computed here rather than in the flow so it costs nothing until something
     * reads it, and is recomputed only when [days] itself changes.
     */
    val longestBreak: Outlook.Stretch? by lazy { Outlook.longestBreak(days) }

    companion object {
        fun empty(): YearUiState {
            val locale = Locale.getDefault()
            val today = DayNumber.today()
            return YearUiState(
                year = today.toLocalDate().year,
                firstDayOfWeek = localeFirstDayOfWeek(locale),
                locale = locale,
                start = DayNumber.from(LocalDate.of(today.toLocalDate().year, 1, 1)),
                days = emptyList(),
                styles = emptyMap(),
                today = today,
                loading = true,
            )
        }
    }
}

class YearViewModel(
    private val repository: RotaRepository,
) : ViewModel() {

    private val visibleYear = MutableStateFlow(DayNumber.today().toLocalDate().year)
    private val locale = MutableStateFlow(Locale.getDefault())

    fun setLocale(value: Locale) {
        locale.value = value
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<YearUiState> = combine(visibleYear, locale, ::Pair)
        .flatMapLatest { (year, currentLocale) ->
            val start = DayNumber.from(LocalDate.of(year, 1, 1))
            val end = DayNumber.from(LocalDate.of(year, 12, 31))
            combine(
                repository.observeCalendar(start, end),
                repository.observeShiftStyles(),
            ) { days, styles ->
                YearUiState(
                    year = year,
                    firstDayOfWeek = localeFirstDayOfWeek(currentLocale),
                    locale = currentLocale,
                    start = start,
                    days = days,
                    styles = styles,
                    today = DayNumber.today(),
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = YearUiState.empty(),
        )

    fun showPreviousYear() {
        visibleYear.value -= 1
    }

    fun showNextYear() {
        visibleYear.value += 1
    }

    fun showThisYear() {
        visibleYear.value = DayNumber.today().toLocalDate().year
    }

    /** The month to open when one is tapped. */
    fun monthAt(month: Int): YearMonth = YearMonth.of(visibleYear.value, month)
}
