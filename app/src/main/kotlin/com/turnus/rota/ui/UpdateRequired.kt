package com.turnus.rota.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties

/**
 * The one thing this app can say to a phone it has already shipped to.
 *
 * There is no server, no forced sign-out and no feature flag system. When a
 * released build turns out to be doing something wrong enough that not running
 * is better than running, the choices are: publish a fix and wait out review,
 * or stop the broken version. This is the second, and it is the only lever
 * there is.
 *
 * **Deliberately not dismissable.** Back and a tap outside both do nothing. A
 * block someone can wave away is not a block, and the situation it exists for
 * is one where continuing is the harm.
 *
 * It is also deliberately reassuring about the rota, because the alarming part
 * of a calendar refusing to open is not the app, it is the fear that the thing
 * inside it is gone. It is not: the database is untouched and the block is a
 * number in a JSON file.
 *
 * Triggered from [com.turnus.rota.ads.AdConfig]'s `min_version`, which defaults
 * to zero and fails open on every error path.
 */
@Composable
fun UpdateRequiredDialog() {
    val context = LocalContext.current
    AlertDialog(
        // Both no-ops, and both required: onDismissRequest fires on a back
        // press, and without the properties below Compose would also close
        // this on an outside tap.
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = { Text("Update Turnus") },
        text = {
            Text(
                "There is a problem with this version and we have fixed it in a " +
                    "newer one.\n\n" +
                    "Your rota is safe on this phone. Updating does not touch it — " +
                    "everything will be exactly where you left it.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = { openStoreListing(context) }) {
                Text("Update now")
            }
        },
    )
}

/**
 * Opens the Play listing, preferring the installed Play app.
 *
 * `market://` goes straight there; the https address is the fallback for a
 * device with no Play app, which on this app's audience is rare but real —
 * plenty of cheap handsets are sold with a vendor store instead.
 */
private fun openStoreListing(context: Context) {
    val attempts = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PLAY_PACKAGE")),
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$PLAY_PACKAGE"),
        ),
    )
    for (intent in attempts) {
        try {
            context.startActivity(intent)
            return
        } catch (missing: ActivityNotFoundException) {
            // Try the next one. If both fail the dialog simply stays up, which
            // is the correct outcome: the build is still the blocked one.
            continue
        }
    }
}

/**
 * The published application id, not `BuildConfig.APPLICATION_ID`.
 *
 * A debug build carries a `.debug` suffix so it can sit beside a Play install,
 * and that id has no Play listing — using it here would send anyone testing
 * this dialog to a 404.
 */
private const val PLAY_PACKAGE = "com.turnus.rota"
