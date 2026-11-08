package com.turnus.rota.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The app's own Play listing: the address to send people to, and opening it.
 *
 * The id is written out rather than read from `BuildConfig.APPLICATION_ID`. A
 * debug build carries a `.debug` suffix so it can sit beside a Play install,
 * and that id has no listing — a link built from it would send a workmate to a
 * 404.
 *
 * Links that leave the app carry a campaign tag in Play's `referrer` parameter.
 * Play Console reads it back as the source of an install, which is the only way
 * to learn whether a shared rota brought anyone in: the app has no analytics
 * and is not getting any (CLAUDE.md rule 9). The tag names the button the link
 * came from, never the person who pressed it.
 */
object PlayListing {

    const val PACKAGE = "com.turnus.rota"

    /** Which button a link left the app from, as Play Console will report it. */
    enum class Source(val campaign: String) {
        /** The install line under a shared rota code. */
        RotaCode("rota-code"),

        /** "Tell someone" in Settings: the app on its own, no rota attached. */
        TellSomeone("tell-someone"),
    }

    /**
     * The https address, so it opens on any phone, in any chat app, and on an
     * iPhone that cannot install it but can at least see what it was sent.
     * Same tag scheme as `docs/INSTAGRAM.md` §10.2, so every channel reads the
     * same way in Play Console.
     */
    fun url(source: Source): String =
        "https://play.google.com/store/apps/details?id=$PACKAGE" +
            "&referrer=utm_source%3Dturnus-app%26utm_medium%3Dshare%26utm_campaign%3D${source.campaign}"

    /** The share sheet for "Tell someone": the app, with nothing of the user's in it. */
    fun tellSomeoneIntent(): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Turnus is a shift rota calendar for Android. Set up your pattern once " +
                    "and see every shift for years ahead, with a reminder before each one. " +
                    "Every feature is free.\n\n" + url(Source.TellSomeone),
            )
        },
        "Tell someone about Turnus",
    )

    /**
     * Opens the listing, preferring the installed Play app.
     *
     * `market://` goes straight there; the https address is the fallback for a
     * device with no Play app, which on this app's audience is rare but real —
     * plenty of cheap handsets are sold with a vendor store instead.
     *
     * @return false if nothing on the phone could open either, so the caller
     *   can say so rather than leave a button that silently does nothing.
     */
    fun open(context: Context): Boolean {
        val attempts = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE")),
        )
        for (intent in attempts) {
            try {
                context.startActivity(intent)
                return true
            } catch (missing: ActivityNotFoundException) {
                continue
            }
        }
        return false
    }
}
