package com.turnus.rota.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.action.actionStartActivity
import com.turnus.rota.data.RotaData
import com.turnus.rota.data.ShiftStyle
import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.Outlook
import com.turnus.rota.engine.ShiftEngine
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * The rota on the home screen.
 *
 * The point of a widget here is that the most common use of this app —
 * "am I in tomorrow?" — should not require opening it at all. So the content is
 * the two facts the month grid takes a scroll and a count to give: what today
 * is, and when that changes.
 *
 * Everything is recomputed from the pattern on each update. Nothing about the
 * widget is stored, which is what stops it becoming a second, staler copy of
 * the calendar.
 */
class RotaWidget : GlanceAppWidget() {

    /** Responsive, so the same widget works at 2x1 and at 4x2 without two layouts. */
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = load(context)
        provideContent {
            Content(snapshot, WidgetPalette.of(context))
        }
    }

    private suspend fun load(context: Context): Snapshot {
        val repository = RotaData.repository(context)
        val today = DayNumber.today()
        // One consistent read, the same as the reminder receiver takes: a
        // widget that named today's shift from one rota and tomorrow's from
        // another would be wrong in a way nobody could explain.
        val inputs = repository.reminderInputs(today, days = HORIZON) ?: return Snapshot.NoRota
        val styles = repository.shiftStyles().associateBy(ShiftStyle::id)

        val days = (0 until DAYS_SHOWN).map { offset ->
            val day = today + offset.toLong()
            day to ShiftEngine.resolve(inputs.pattern, inputs.overrides, day)
        }

        val resolved = ShiftEngine.resolveRange(
            inputs.pattern,
            inputs.overrides,
            today,
            today + HORIZON.toLong(),
        )
        val outlook = Outlook.summarise(inputs.pattern, inputs.overrides, today, HORIZON)

        return Snapshot.Rota(
            days = days,
            styles = styles,
            today = today,
            headline = headlineFor(outlook, styles, days.first().second),
            detail = detailFor(outlook, styles),
            hasBreak = resolved.any { !it.isWorking },
        )
    }

    private companion object {
        /** Enough to answer "when does this change" without loading a year. */
        const val HORIZON = 60
        const val DAYS_SHOWN = 7
    }
}

internal sealed interface Snapshot {
    data object NoRota : Snapshot
    data class Rota(
        val days: List<Pair<DayNumber, String?>>,
        val styles: Map<String, ShiftStyle>,
        val today: DayNumber,
        val headline: String,
        val detail: String?,
        val hasBreak: Boolean,
    ) : Snapshot
}

private fun headlineFor(
    outlook: Outlook.Summary,
    styles: Map<String, ShiftStyle>,
    todayShift: String?,
): String = when {
    todayShift == null -> "Off today"
    else -> styles[todayShift]?.name ?: "Working today"
}

private fun detailFor(outlook: Outlook.Summary, styles: Map<String, ShiftStyle>): String? {
    val next = outlook.next ?: return null
    val away = outlook.daysUntilNext ?: return null
    val whenPhrase = when {
        away == 1 -> "tomorrow"
        away <= 6 -> next.start.toLocalDate().format(WEEKDAY)
        else -> next.start.toLocalDate().format(SHORT_DATE)
    }
    return if (outlook.current.isWorking) {
        "Off from $whenPhrase"
    } else {
        val name = next.shiftTypeId?.takeUnless { next.mixed }?.let { styles[it]?.name }
        if (name != null) "Back in $whenPhrase · $name" else "Back in $whenPhrase"
    }
}

private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE")
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
