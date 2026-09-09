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
     * rather than theirs. It also accepts a token a messaging app or an email
     * client broke across lines: [ShareLink.decode] removes whitespace and the
     * invisible characters that wrapping leaves behind, so each candidate below
     * is tried whole rather than in pieces.
     *
     * The candidates are ordered widest-net-last, and a real rota beats a
     * version complaint: an early candidate that happens to look like a code
     * from the future must not hide a good code further down the message.
     */
    fun read(pasted: String): ShareLinkResult {
        val trimmed = pasted.trim()
        if (trimmed.isEmpty()) return ShareLinkResult.Malformed

        val candidates = sequence {
            // A URL first: its fragment is the token, and the rest is not.
            ShareLink.tokenFrom(trimmed)?.let { yield(it) }

            // The whole paste — the token on its own, however it was wrapped.
            yield(trimmed)

            // Pasted inside a message. A blank line is the one separator that
            // survives every app that touches the message, so each paragraph is
            // tried entire: that is what rejoins a token an email client hard
            // wrapped, without swallowing the sentence after it.
            yieldAll(trimmed.split(PARAGRAPH))

            // Chatter on a single line: the word that looks like a token.
            // Split on whitespace only, so a trailing full stop still fails
            // cleanly rather than being trimmed into a different code.
            yieldAll(trimmed.split(WHITESPACE).filter { it.contains('.') })
        }

        // A candidate that decoded but cannot be used is worth keeping: it is
        // the difference between telling someone their code is broken and
        // telling them what is actually wrong with it. Only [Malformed] — which
        // means "this was not a code at all" — is discarded on the way past.
        var problem: ShareLinkResult? = null
        for (candidate in candidates) {
            when (val result = ShareLink.decode(candidate)) {
                is ShareLinkResult.Success -> return result
                ShareLinkResult.Malformed -> Unit
                else -> problem = problem ?: result
            }
        }
        return problem ?: ShareLinkResult.Malformed
    }

    /** A blank line, however the sender's platform spells its line endings. */
    private val PARAGRAPH = Regex("\\n[ \\t\\r]*\\n")

    private val WHITESPACE = Regex("\\s+")
}
