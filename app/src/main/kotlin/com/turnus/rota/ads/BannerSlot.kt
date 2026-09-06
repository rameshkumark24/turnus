package com.turnus.rota.ads

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.turnus.rota.BuildConfig

/**
 * The anchored banner under the month grid.
 *
 * Its height is reserved whether or not an ad ever arrives, so the calendar
 * above it never reflows. A grid that jumps a centimetre when a banner fills is
 * how someone taps the wrong day.
 *
 * No interstitials and no app-open ads exist anywhere in this app, by decision.
 * This is a tool people open for ten seconds to check whether they are working
 * tomorrow; a full-screen ad in front of that answer would be the last time
 * they opened it.
 */
@Composable
fun BannerSlot(modifier: Modifier = Modifier) {
    val allowed by AdGate.bannersAllowed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current

    // The window's width, not the screen's. In split-screen or a freeform
    // window the two differ, and an anchored banner sized to the whole display
    // would be wider than the space it is anchored in.
    val density = LocalDensity.current
    val widthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }

    val adSize = remember(widthDp) {
        if (inPreview) null else adaptiveSize(context, widthDp.value.toInt())
    }
    val reserved = adSize?.height?.dp ?: FALLBACK_HEIGHT

    Box(
        modifier
            .fillMaxWidth()
            .height(reserved)
            // An empty or unfilled slot must not be a stop for a screen reader,
            // and an ad that does arrive carries its own semantics.
            .clearAndSetSemantics { },
    ) {
        if (!allowed || adSize == null || inPreview) return@Box

        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { viewContext ->
                AdView(viewContext).apply {
                    setAdSize(adSize)
                    adUnitId = BuildConfig.AD_BANNER_UNIT_ID
                    loadAd(AdRequest.Builder().build())
                }
            },
            // An AdView owns a WebView and its own refresh timers. Left
            // undestroyed it keeps running behind whatever replaces this
            // screen, which on a calendar people page through all day is a
            // leak that compounds.
            onRelease = AdView::destroy,
        )
    }
}

/**
 * An anchored adaptive banner: the height Google picks for this screen width,
 * which is what fills best and what the policy expects for a pinned slot.
 */
private fun adaptiveSize(context: Context, widthDp: Int): AdSize? = runCatching {
    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
}.onFailure { Log.w("BannerSlot", "could not size banner", it) }.getOrNull()

/** Roughly a banner's height, so the reservation is right even before sizing. */
private val FALLBACK_HEIGHT = 50.dp
