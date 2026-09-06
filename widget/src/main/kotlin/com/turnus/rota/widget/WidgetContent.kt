package com.turnus.rota.widget

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.turnus.rota.engine.DayNumber
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Tapping anywhere opens the app.
 *
 * A widget with several tap targets on a home screen is a widget people trigger
 * by accident while swiping. One target, one destination.
 */
@Composable
internal fun Content(snapshot: Snapshot, palette: WidgetPalette) {
    val open = GlanceModifier.clickable(openApp())

    Box(
        GlanceModifier
            .fillMaxSize()
            .background(palette.background)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .then(open),
    ) {
        when (snapshot) {
            Snapshot.NoRota -> Text(
                text = "Tap to set up your rota",
                style = TextStyle(
                    color = palette.onSurfaceVariant,
                    fontSize = 14.sp,
                ),
            )
            is Snapshot.Rota -> RotaContent(snapshot, palette)
        }
    }
}

@Composable
private fun RotaContent(snapshot: Snapshot.Rota, palette: WidgetPalette) {
    Column(GlanceModifier.fillMaxSize()) {
        Text(
            text = snapshot.headline,
            style = TextStyle(
                color = palette.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        snapshot.detail?.let { detail ->
            Text(
                text = detail,
                style = TextStyle(
                    color = palette.onSurfaceVariant,
                    fontSize = 13.sp,
                ),
                maxLines = 1,
            )
        }

        Spacer(GlanceModifier.height(8.dp))

        // defaultWeight so the strip takes the height the widget actually has.
        // Sized to its content it left two thirds of a 4x2 widget empty, which
        // reads as a widget that failed to load rather than one that is done.
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            snapshot.days.forEach { (day, shiftTypeId) ->
                DayColumn(
                    day = day,
                    shiftTypeId = shiftTypeId,
                    snapshot = snapshot,
                    palette = palette,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: DayNumber,
    shiftTypeId: String?,
    snapshot: Snapshot.Rota,
    palette: WidgetPalette,
    modifier: GlanceModifier,
) {
    val style = shiftTypeId?.let { snapshot.styles[it] }
    val fill = style?.let { Color(it.color) }
    // The same luminance rule the calendar uses: shift colours are user-chosen,
    // so the only label colour guaranteed to be readable is one derived from
    // the fill rather than fixed.
    val label = when {
        fill == null -> palette.onSurfaceVariant
        fill.luminance() > 0.5f -> WidgetPalette.Companion.onLightShift
        else -> WidgetPalette.Companion.onDarkShift
    }

    Column(
        // fillMaxHeight so the block below has vertical space to expand into.
        // Without it the column wrapped its content and the weighted block
        // collapsed, leaving two thirds of the widget empty.
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Centred rather than top-aligned: the strip takes whatever height the
        // widget has, but a day block stretched to fill it reads as a bar
        // chart instead of a calendar.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.toLocalDate().dayOfWeek
                .getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
            style = TextStyle(color = palette.onSurfaceVariant, fontSize = 10.sp),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(2.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 1.dp)
                .then(
                    if (fill != null) {
                        GlanceModifier.background(ColorProvider(fill)).cornerRadius(5.dp)
                    } else {
                        GlanceModifier
                            .background(palette.offDay)
                            .cornerRadius(5.dp)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = style?.code ?: "",
                style = TextStyle(color = label, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

/**
 * Launches the app's own launcher entry rather than naming MainActivity.
 *
 * The widget module does not depend on `:app`, and hard-coding the class name
 * of something in another module is a rename away from a widget that does
 * nothing when tapped.
 */
@Composable
private fun openApp() = androidx.glance.appwidget.action.actionStartActivity(
    androidx.glance.LocalContext.current.packageManager
        .getLaunchIntentForPackage(androidx.glance.LocalContext.current.packageName)
        ?: Intent(),
)
