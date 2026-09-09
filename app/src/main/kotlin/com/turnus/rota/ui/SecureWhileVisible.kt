package com.turnus.rota.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Keeps the day sheet out of screenshots and the recents thumbnail.
 *
 * ### What this is actually protecting
 *
 * A day's note is where people write "hospital", "funeral", "occupational
 * health". Everywhere else in the app that data is guarded by the type system —
 * the calendar export and the share code are given a structure that has no note
 * field in it, so they cannot leak one. The screen is the one place a note is
 * on display, and the recents switcher photographs whatever is on display.
 *
 * The person that matters to is not a remote attacker. It is someone who picks
 * up an unlocked phone that is not theirs — a partner, a relative, a colleague —
 * and taps the square button. For an app whose users work nights and whose notes
 * record their health, that is the likeliest way any of this ever gets read by
 * the wrong person.
 *
 * ### Why it is scoped to the sheet and not set on the Activity
 *
 * `FLAG_SECURE` is a property of the window, so setting it once would block
 * every screenshot in the app — including the one users actually want, of the
 * month grid, to send to somebody who needs to know when they are working.
 * Sharing the rota is a feature; sharing the notes is not. Raising the flag only
 * while the sheet is open draws the line in the right place, and costs the user
 * nothing they were trying to do.
 *
 * Cleared in `onDispose`, so closing the sheet restores normal behaviour.
 *
 * The context is unwrapped rather than cast. `LocalContext` is the Activity
 * today, but it is only ever *a* Context: anything that wraps the tree in a
 * `ContextWrapper` — a theme overlay, a locale wrapper, a preview host — turns a
 * direct cast into a silent null, and a silent null here means the protection
 * quietly stops existing while the code still looks correct. Walking the wrapper
 * chain costs three lines and removes the failure mode instead of documenting it.
 */
@Composable
internal fun SecureWhileVisible() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
