package com.turnus.rota.share

import android.content.Intent
import com.turnus.rota.data.RotaRepository
import com.turnus.rota.engine.ShareLink
import com.turnus.rota.engine.ShareLinkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sending a rota to a workmate, and taking one from them.
 *
 * People on the same site work the same rotation, and the person who has
 * already typed it in is doing the other four a favour. This is that favour:
 * one code, pasted into any messaging app they already use.
 *
 * A code rather than a link, because a link needs a domain and a domain needs
 * an owner, and none of that should stand between two people comparing shifts.
 * The code carries nothing but the cycle, its start date and its name — no
 * identifier, nothing about who sent it, and nothing that leaves the phone
 * except through the app the user chose from the share sheet.
 */
object RotaCode {

    /** Thrown when there is no rota to share, so the caller can say why. */
    class NothingToShare : IllegalStateException("No rota has been set up yet")

    /**
     * The message that goes into the share sheet.
     *
     * It says where to paste it. A bare token arriving in a chat is a puzzle,
     * and the person receiving it may not have the app yet.
     */
    suspend fun message(repository: RotaRepository): String = withContext(Dispatchers.Default) {
        val pattern = repository.activePattern() ?: throw NothingToShare()
        val definitions = repository.shiftDefinitions()
        val token = ShareLink.encode(pattern, definitions)

        buildString {
            append("My rota — ").append(pattern.name)
            append(" (").append(pattern.cycleLength).append("-day cycle).\n\n")
            append(token)
            append("\n\nOpen Turnus, then Settings → Share and export → ")
            append("\"I have a code\", and paste that in.")
        }
    }

    fun shareIntent(message: String): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        },
        "Send my rota",
    )

    /**
     * Reads whatever the user pasted.
     *
     * Accepts a bare token, a token with surrounding chatter, or a URL — people
     * paste the whole message, and picking the code out of it is this app's job
     * rather than theirs.
     */
    fun read(pasted: String): ShareLinkResult {
        val trimmed = pasted.trim()
        if (trimmed.isEmpty()) return ShareLinkResult.Malformed

        // A URL first: its fragment is the token, and the rest is not.
        ShareLink.tokenFrom(trimmed)?.let { fromUrl ->
            val result = ShareLink.decode(fromUrl)
            if (result !is ShareLinkResult.Malformed) return result
        }

        val direct = ShareLink.decode(trimmed)
        if (direct !is ShareLinkResult.Malformed) return direct

        // Pasted inside a message: find the word that looks like a token. Split
        // on whitespace only, so a trailing full stop still fails cleanly
        // rather than being silently trimmed into a different code.
        return trimmed.split(Regex("\\s+"))
            .asSequence()
            .filter { it.contains('.') }
            .map(ShareLink::decode)
            .firstOrNull { it !is ShareLinkResult.Malformed }
            ?: ShareLinkResult.Malformed
    }
}
