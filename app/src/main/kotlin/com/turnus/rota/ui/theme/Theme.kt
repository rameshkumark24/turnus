package com.turnus.rota.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Design tokens.
 *
 * Deliberately not Material's default purple. The palette is a cool, slightly
 * blue-biased neutral set with a single petrol accent — chosen so that the
 * user's own shift colours, which are the only saturated things on screen, are
 * never competing with the chrome for attention. A rota app is read at a
 * glance in bad light; the grid has to be the loudest thing on it.
 *
 * Dynamic colour is deliberately not used. Shift colours are user-assigned and
 * meaningful, so a wallpaper-derived scheme could put the chrome in direct
 * conflict with them.
 */
object TurnusTokens {
    // Neutrals, light
    val Ink = Color(0xFF14202A)
    val Ink2 = Color(0xFF485A67)
    val Ink3 = Color(0xFF7B8A95)
    val Rule = Color(0xFFDCE3E7)
    val RuleStrong = Color(0xFFC3CDD3)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFEFF3F5)
    val Ground = Color(0xFFF6F8F9)

    // Neutrals, dark
    val InkD = Color(0xFFE5ECF0)
    val Ink2D = Color(0xFFA1B0BA)
    val Ink3D = Color(0xFF73838D)
    val RuleD = Color(0xFF253138)
    val RuleStrongD = Color(0xFF35444D)
    val SurfaceD = Color(0xFF151E24)
    val SurfaceAltD = Color(0xFF1B262D)
    val GroundD = Color(0xFF0D1418)

    // Accent
    val Accent = Color(0xFF0C6B7C)
    val AccentSoft = Color(0xFFE0EFF1)
    val AccentD = Color(0xFF52BFD1)
    val AccentSoftD = Color(0xFF10313A)

    val Danger = Color(0xFFA03B2B)
    val DangerD = Color(0xFFE08A73)

    /** Grid metrics, kept here so the month and year views cannot drift apart. */
    val CellCorner = 7.dp
    val CellGap = 3.dp
    /** Cell width divided by height. Slightly taller than square, to fit a day number above a shift code. */
    val CellAspect = 0.82f
    val ScreenPadding = 14.dp
    /** One line of weekday initials above the grid. Fixed so the grid can budget for it. */
    val WeekdayRowHeight = 22.dp

    /**
     * Floor on a day cell's height, below which it stops being readable.
     *
     * A landscape phone has nowhere near the height for six weeks: shrinking to
     * fit produced 30dp cells whose day numbers were clipped to a single digit,
     * so "11" and "12" both read as "1". Better to keep the cells legible and
     * let the screen scroll.
     */
    val MinCellHeight = 46.dp

    /**
     * The "this is today" and "this is your current shift" ring.
     *
     * Inset rather than drawn on the edge, so the ring always has the cell's own
     * colour on both sides of it and never has to contrast with the page behind.
     */
    val TodayRingWidth = 2.dp
    val TodayRingInset = 2.dp

    /**
     * Ceiling on the user's font scale *inside the month grid only*.
     *
     * A calendar is a fixed geometry: seven columns and six rows have to be on
     * screen at once, so cell text cannot grow without either clipping or
     * pushing weeks off the bottom. At 2x it clipped every shift code in half.
     * Everything outside the grid — headers, the day editor, the whole setup
     * flow — still scales without limit, and each cell's spoken description
     * carries the full date and shift name, so nothing is only available to
     * people who can read 11sp text.
     */
    const val GridFontScaleCap = 1.3f
}

private val LightColors = lightColorScheme(
    primary = TurnusTokens.Accent,
    onPrimary = Color.White,
    primaryContainer = TurnusTokens.AccentSoft,
    onPrimaryContainer = TurnusTokens.Accent,
    // Secondary and tertiary are mapped onto the accent rather than left at
    // their defaults: any Material component that reaches for them would
    // otherwise paint itself baseline purple in the middle of this palette.
    secondary = TurnusTokens.Accent,
    onSecondary = Color.White,
    secondaryContainer = TurnusTokens.AccentSoft,
    onSecondaryContainer = TurnusTokens.Accent,
    tertiary = TurnusTokens.Ink2,
    onTertiary = Color.White,
    // The snackbar lives on the inverse roles. Leaving them unset is what put
    // a purple "Undo" on screen — the one control in the app that has to be
    // found and hit inside a few seconds.
    inverseSurface = TurnusTokens.Ink,
    inverseOnSurface = TurnusTokens.Ground,
    inversePrimary = TurnusTokens.AccentD,
    background = TurnusTokens.Ground,
    onBackground = TurnusTokens.Ink,
    surface = TurnusTokens.Surface,
    onSurface = TurnusTokens.Ink,
    surfaceVariant = TurnusTokens.SurfaceAlt,
    onSurfaceVariant = TurnusTokens.Ink2,
    outline = TurnusTokens.RuleStrong,
    outlineVariant = TurnusTokens.Rule,
    error = TurnusTokens.Danger,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = TurnusTokens.AccentD,
    onPrimary = Color(0xFF06323B),
    primaryContainer = TurnusTokens.AccentSoftD,
    onPrimaryContainer = TurnusTokens.AccentD,
    secondary = TurnusTokens.AccentD,
    onSecondary = Color(0xFF06323B),
    secondaryContainer = TurnusTokens.AccentSoftD,
    onSecondaryContainer = TurnusTokens.AccentD,
    tertiary = TurnusTokens.Ink2D,
    onTertiary = Color(0xFF06323B),
    inverseSurface = TurnusTokens.InkD,
    inverseOnSurface = TurnusTokens.Ink,
    inversePrimary = TurnusTokens.Accent,
    background = TurnusTokens.GroundD,
    onBackground = TurnusTokens.InkD,
    surface = TurnusTokens.SurfaceD,
    onSurface = TurnusTokens.InkD,
    surfaceVariant = TurnusTokens.SurfaceAltD,
    onSurfaceVariant = TurnusTokens.Ink2D,
    outline = TurnusTokens.RuleStrongD,
    outlineVariant = TurnusTokens.RuleD,
    error = TurnusTokens.DangerD,
    onError = Color(0xFF3A1109),
)

/**
 * Sizes are in `sp` throughout so they scale with the user's font setting.
 * Shift workers skew older than the average app audience and a rota is read in
 * a hurry, so Dynamic Type support is a feature here, not a checkbox.
 */
private val TurnusTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        titleMedium = titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyMedium = bodyMedium.copy(fontSize = 15.sp),
        labelLarge = labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    )
}

@Composable
fun TurnusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = androidx.compose.ui.platform.LocalView.current

    if (!view.isInEditMode) {
        val context = LocalContext.current
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = TurnusTypography,
        content = content,
    )
}
