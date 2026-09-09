package com.turnus.rota.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Calls [onChange] when the device decides it is a different day.
 *
 * ### Why this has to exist
 *
 * Every screen that rings "today" reads the date when it builds its state, and
 * its state is rebuilt when the rota changes, when the visible month changes, or
 * when the screen comes back to the foreground. None of those is midnight. A
 * calendar left on screen at 23:59 therefore goes on ringing yesterday, naming
 * yesterday's shift, and counting "off from tomorrow" from the wrong day —
 * measured on a real handset, not assumed.
 *
 * That is not a cosmetic lag. The person most likely to have this app open at
 * midnight is someone on nights, and the answer they came for is which day is
 * today. A confidently wrong ring is the one failure this app is supposed not
 * to have.
 *
 * ### Why a receiver rather than a timer
 *
 * Android already announces this. `ACTION_DATE_CHANGED` is sent when the day
 * rolls over, so nothing here has to poll, wake up, or guess how long until
 * midnight — and a minute-ticker would spend a wakeup every minute of the day
 * to catch one moment of it.
 *
 * The clock and timezone actions are here for the same reason the date one is:
 * a phone that lands after a flight, or picks up the network's time for the
 * first time, changes what day it is without midnight having happened.
 *
 * ### Why it is registered from the composition
 *
 * It lives exactly as long as the screen that needs it, so there is no receiver
 * running while the app is in the background — where the foreground return
 * already refreshes everything — and nothing to unregister by hand.
 *
 * Callers are expected to re-read the date and store it, not to assume the day
 * moved: these actions also fire when the clock is nudged by a second, and a
 * caller that keeps the date in a `StateFlow` will find an unchanged day
 * conflated away for free.
 */
@Composable
internal fun OnDateChange(onChange: () -> Unit) {
    val context = LocalContext.current
    // So a recomposition that hands over a new lambda does not tear the
    // receiver down and build another one.
    val latest by rememberUpdatedState(onChange)

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = latest()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // NOT_EXPORTED: all three are broadcast by the system only, and a
        // receiver that accepts none of them from another app is the narrower
        // of the two choices API 34 forces you to make.
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
}
