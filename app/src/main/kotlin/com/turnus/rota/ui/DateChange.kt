package com.turnus.rota.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect

/**
 * Calls [onChange] whenever the day the device thinks it is may have moved:
 * once as the screen starts, and again on every date, clock or timezone change
 * while it is on screen.
 *
 * ### Why this has to exist
 *
 * Every screen that rings "today" reads the date when it builds its state, and
 * its state is rebuilt when the rota changes, when the visible month changes, or
 * when the flow behind it is resubscribed. None of those is midnight. A calendar
 * left on screen at 23:59 therefore goes on ringing yesterday, naming
 * yesterday's shift, and counting "off from tomorrow" from the wrong day —
 * measured on a real handset, not assumed.
 *
 * That is not a cosmetic lag. The person most likely to have this app open at
 * midnight is someone on nights, and the answer they came for is which day is
 * today. A confidently wrong ring is the one failure this app is supposed not
 * to have.
 *
 * ### Why it fires at start, and not only on a broadcast
 *
 * A receiver only hears what is broadcast while it is listening, and this one
 * is deliberately not listening while its screen is away — so the interesting
 * case is the day changing during exactly that gap. Someone on the calendar at
 * 23:55 who opens settings, comes back at 00:05 and finds yesterday still
 * ringed has been failed by a fix that only listened.
 *
 * Firing on start closes that, and replaces something subtler that was lost when
 * the date became a `StateFlow`: the cold flow it replaced re-read the clock
 * every time `WhileSubscribed` restarted it, so a return to the foreground was
 * self-correcting. A `StateFlow` replays its cached value instead. Reading the
 * clock here restores that, and does not depend on a broadcast having reached a
 * process the system may have cached.
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
 * ### Why it is tied to the lifecycle, not the composition
 *
 * A composition outlives `onStop` — it is torn down at `onDestroy` — so a
 * `DisposableEffect` here would leave the receiver registered for the whole
 * time the app sat in the background, which is precisely when it is not needed.
 * Starting and stopping with the lifecycle keeps it to the moments the screen is
 * actually showing a date, and hands back the start-time read above for free.
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

    LifecycleStartEffect(context) {
        // Before registering, not after: whatever happened while this screen
        // was away was not heard by anybody.
        latest()

        val receiver = object : BroadcastReceiver() {
            // Deliberately not named `context`. The broadcast context is a
            // different, nullable object from the one this effect registered
            // against, and letting it shadow the outer one would quietly hand
            // the wrong receiver to whoever edits this next.
            override fun onReceive(broadcastContext: Context?, intent: Intent?) = latest()
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
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
}
