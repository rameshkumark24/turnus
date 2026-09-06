package com.turnus.rota.ads

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.turnus.rota.BuildConfig

private const val TAG = "NativeSlot"

/**
 * The native slot at the foot of the year view.
 *
 * The year view is the one screen people linger on — they open it to find their
 * next long break and then scroll it — which is why the second ad belongs here
 * and not in front of the month grid they check for ten seconds.
 *
 * Unlike the banner, this reserves no space. The banner sits *under* the month
 * grid, where a late fill would shift the day someone is reaching for; this
 * sits at the bottom of a scrolling column, below everything, so appearing late
 * moves nothing. Reserving here would only mean a blank rectangle for every
 * user who declines consent, is served nothing, or has the slot switched off.
 *
 * It is drawn from plain views rather than an XML layout so it can take its
 * colours and shapes from the app's own theme and stop looking like an advert
 * bolted onto someone else's design — while still being unmistakably labelled
 * as one, which the "Ad" badge is there to do and which policy requires.
 */
@Composable
fun NativeSlot(modifier: Modifier = Modifier) {
    val allowed by AdGate.nativeAllowed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current

    var ad by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(allowed, inPreview) {
        if (!allowed || inPreview) return@DisposableEffect onDispose { }

        // A load in flight outlives the screen that started it. Without this
        // flag, an ad arriving after the user has left is assigned to a
        // composition that no longer exists and is never destroyed — a leaked
        // ad object per visit to a screen people open repeatedly.
        var gone = false

        AdLoader.Builder(context, BuildConfig.AD_NATIVE_UNIT_ID)
            .forNativeAd { loaded ->
                if (gone) {
                    loaded.destroy()
                } else {
                    ad?.destroy()
                    ad = loaded
                }
            }
            .withAdListener(
                object : AdListener() {
                    // Not an error worth surfacing: no fill is the normal state
                    // of a new app with no ad history, and there is nothing the
                    // user could do about it.
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.i(TAG, "no native ad: ${error.code} ${error.message}")
                    }
                },
            )
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build(),
            )
            .build()
            .loadAd(AdRequest.Builder().build())

        onDispose {
            gone = true
            ad?.destroy()
            ad = null
        }
    }

    val loaded = ad ?: return

    val colors = MaterialTheme.colorScheme
    val palette = remember(colors) {
        NativeAdCard.Palette(
            surface = colors.surface.toArgb(),
            onSurface = colors.onSurface.toArgb(),
            onSurfaceVariant = colors.onSurfaceVariant.toArgb(),
            accent = colors.primary.toArgb(),
            onAccent = colors.onPrimary.toArgb(),
            outline = colors.outlineVariant.toArgb(),
        )
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { NativeAdCard(it) },
        update = { it.show(loaded, palette) },
    )
}

/**
 * A native ad drawn to look like the rest of the app.
 *
 * Every asset shown has to be registered on the [NativeAdView] — the SDK
 * reports clicks and impressions through those registrations, so an unregistered
 * headline is not a styling mistake but an ad that pays nothing and, if it is
 * the clickable part, a policy violation.
 */
private class NativeAdCard(context: Context) : FrameLayout(context) {

    /**
     * Held rather than inherited: NativeAdView is final, so the only way to
     * give it a custom layout is to build one inside it. Every asset view must
     * be a descendant of this, or its registration means nothing.
     */
    private val adView = NativeAdView(context)

    data class Palette(
        val surface: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val accent: Int,
        val onAccent: Int,
        val outline: Int,
    )

    private val badge = TextView(context).apply {
        text = "Ad"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(6), dp(1), dp(6), dp(1))
    }

    private val icon = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private val headline = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 2
    }

    private val body = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        maxLines = 2
    }

    private val cta = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(14), dp(8), dp(14), dp(8))
    }

    init {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }

        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(badge)
            addView(
                icon,
                LinearLayout.LayoutParams(dp(28), dp(28)).apply { leftMargin = dp(8) },
            )
            addView(
                headline,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { leftMargin = dp(8) },
            )
        }

        root.addView(top, matchWidth())
        root.addView(body, matchWidth().apply { topMargin = dp(6) })
        root.addView(
            cta,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(10) },
        )

        adView.addView(root)
        addView(adView)

        // The registrations the SDK needs. Done once, in the constructor: the
        // views never change identity, only their content.
        adView.headlineView = headline
        adView.bodyView = body
        adView.callToActionView = cta
        adView.iconView = icon
    }

    fun show(ad: NativeAd, palette: Palette) {
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(palette.surface)
            setStroke(dp(1), palette.outline)
        }

        badge.setTextColor(palette.onAccent)
        badge.background = GradientDrawable().apply {
            cornerRadius = dp(4).toFloat()
            setColor(palette.accent)
        }

        headline.setTextColor(palette.onSurface)
        headline.text = ad.headline

        body.setTextColor(palette.onSurfaceVariant)
        // Gone, not blank: an empty line of text still takes a line of height,
        // and the card would sit lopsided for every ad without a body.
        body.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
        body.text = ad.body

        cta.setTextColor(palette.onAccent)
        cta.background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(palette.accent)
        }
        cta.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
        cta.text = ad.callToAction

        val iconDrawable = ad.icon?.drawable
        icon.visibility = if (iconDrawable == null) View.GONE else View.VISIBLE
        icon.setImageDrawable(iconDrawable)

        // Last, and always: this is what turns the registered views into a
        // reported impression.
        adView.setNativeAd(ad)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
