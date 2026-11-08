package com.turnus.rota.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import com.turnus.rota.share.PlayListing

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
            // If nothing can open the listing the dialog simply stays up, which
            // is the correct outcome: the build is still the blocked one.
            TextButton(onClick = { PlayListing.open(context) }) {
                Text("Update now")
            }
        },
    )
}
