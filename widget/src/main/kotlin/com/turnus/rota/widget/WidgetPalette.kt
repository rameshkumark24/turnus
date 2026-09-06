package com.turnus.rota.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider

/**
 * The widget's chrome colours.
 *
 * Deliberately fixed rather than `GlanceTheme.colors`, which is wallpaper-
 * derived on Android 12+. That is the same decision the app's theme makes and
 * for the same reason: shift colours are user-assigned and carry meaning, and a
 * dynamic scheme puts the chrome in direct conflict with them. On a green
 * wallpaper the widget came out green behind yellow and blue shift blocks.
 *
 * Dark mode is resolved once, when the content is built, rather than through a
 * day/night colour provider. The widget already has a Context at that point and
 * the answer cannot change without the system rebuilding the widget anyway, so
 * a plain boolean is the whole of what this needs.
 *
 * These values are duplicated from `TurnusTokens` in `:app`. They cannot be
 * imported — `:widget` must not depend on `:app`, since `:app` depends on it —
 * and a handful of hex constants did not justify a fourth module. If the
 * palette moves, it moves in both places.
 */
internal class WidgetPalette(private val dark: Boolean) {

    val background = provide(light = 0xFFFFFFFF, night = 0xFF151E24)
    val onSurface = provide(light = 0xFF14202A, night = 0xFFE5ECF0)
    val onSurfaceVariant = provide(light = 0xFF485A67, night = 0xFFA1B0BA)
    val offDay = provide(light = 0xFFEFF3F5, night = 0xFF1B262D)

    private fun provide(light: Long, night: Long) =
        ColorProvider(Color(if (dark) night else light))

    companion object {
        /** Labels on a user-chosen shift colour, chosen by that colour's luminance. */
        val onLightShift = ColorProvider(Color(0xFF14202A))
        val onDarkShift = ColorProvider(Color.White)

        fun of(context: Context): WidgetPalette = WidgetPalette(
            dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES,
        )
    }
}
