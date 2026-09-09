package com.turnus.rota.share

import com.turnus.rota.engine.DayNumber
import com.turnus.rota.engine.Pattern
import com.turnus.rota.engine.Presets
import com.turnus.rota.engine.ShareLink
import com.turnus.rota.engine.ShareLinkResult
import com.turnus.rota.engine.ShiftDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * What survives the trip between two phones.
 *
 * These are JVM tests, not instrumented ones: [RotaCode.read] is pure Kotlin
 * over a string, and the string is the whole problem. Nothing here touches
 * Android, so the cases below run in milliseconds and can afford to be many —
 * which matters, because every one of them is a real thing a messaging app,
 * an email client or a clipboard actually does to a pasted code.
 *
 * This is the flow that gets this app its next user. A workmate who pastes a
 * code and is told it is not a rota code does not try twice.
 */
class RotaCodeTest {

    // ------------------------------------------------------- the plain cases

    @Test
    fun `reads a bare token`() {
        assertRota(RotaCode.read(token()))
    }

    @Test
    fun `reads the message the app itself sends`() {
        assertRota(RotaCode.read(shareMessage()))
    }

    @Test
    fun `reads a token with chatter around it on one line`() {
        assertRota(RotaCode.read("here you go mate ${token()} see you monday"))
    }

    @Test
    fun `reads a url`() {
        val url = ShareLink.url(pattern(), definitions(), base = "https://example.test/r/")
        assertRota(RotaCode.read(url))
    }

    // ------------------------------------------------------ what apps do to it

    /** The reported failure: a client hard-wrapped the token onto three lines. */
    @Test
    fun `reads a token broken across lines`() {
        assertRota(RotaCode.read(token().chunked(20).joinToString("\n")))
    }

    /**
     * The same wrap, but inside the app's own message.
     *
     * This is the case the whitespace stripping alone does not solve: strip
     * every space from the whole message and the sentence after the token runs
     * into it. A blank line is the one separator that survives every app that
     * touches the text, so the paragraph holding the token is tried entire.
     */
    @Test
    fun `reads a wrapped token inside the full message`() {
        val wrapped = shareMessage().replace(token(), token().chunked(20).joinToString("\n"))
        assertRota(RotaCode.read(wrapped))
    }

    /**
     * Zero-width space, soft hyphen, byte-order mark, left-to-right mark.
     *
     * None of these are typed by anybody. They are left behind by text
     * renderers at wrap points, by rich-text editors, and by the clipboard on
     * its way through a browser — invisible on screen, and fatal to Base64.
     */
    @Test
    fun `reads a token carrying invisible characters`() {
        val mangled = token()
            .replaceRange(6, 6, "\u200b")
            .replaceRange(20, 20, "\u00ad")
            .let { "\ufeff" + it + "\u200e" }
        assertRota(RotaCode.read(mangled))
    }

    @Test
    fun `reads a token with windows line endings and trailing spaces`() {
        assertRota(RotaCode.read("  \r\n" + token().chunked(16).joinToString("  \r\n") + "\r\n  "))
    }

    // ------------------------------------------------------------ refusals

    /**
     * Prose must not be reported as a code from the future.
     *
     * Whitespace is stripped before the version is read, so without a check on
     * the shape of a version any sentence containing a full stop looks like
     * one — and the user is sent to the Play Store to fix a message they
     * pasted by mistake.
     */
    @Test
    fun `ordinary text is not a rota code`() {
        listOf(
            "",
            "   ",
            "hi mate. see you at 6",
            "My rota - 4 on, 4 off (8-day cycle).",
            "https://example.test/r/",
        ).forEach {
            assertEquals(ShareLinkResult.Malformed, RotaCode.read(it), "for '$it'")
        }
    }

    @Test
    fun `a code from a newer version says so`() {
        val result = RotaCode.read("Here is mine\n\nv9.YW55dGhpbmc\n\npaste that in")
        assertEquals(ShareLinkResult.UnsupportedVersion("v9"), result)
    }

    /** A real code anywhere in the paste beats a lookalike earlier in it. */
    @Test
    fun `a real rota wins over a version complaint`() {
        assertRota(RotaCode.read("v9.YW55dGhpbmc\n\n${token()}"))
    }

    /**
     * An over-long cycle survives the candidate ladder as itself.
     *
     * The ladder used to keep only a version complaint and throw every other
     * non-success away as [ShareLinkResult.Malformed], which would have turned
     * "this rota is too long" back into "this is not a rota code".
     */
    @Test
    fun `a cycle too long to use is reported as such, not as broken`() {
        val long = Pattern(
            id = "p",
            name = "long",
            anchor = DayNumber.of(2026, 9, 4),
            slots = List(Pattern.MAX_CYCLE_DAYS + 5) { "a" },
        )
        val token = ShareLink.encode(
            long,
            mapOf("a" to ShiftDefinition(id = "a", code = "a", name = "A")),
        )

        val result = RotaCode.read("here you go\n\n$token\n\npaste that in")
        val tooLong = assertIs<ShareLinkResult.CycleTooLong>(result)
        assertEquals(Pattern.MAX_CYCLE_DAYS + 5, tooLong.days)
    }

    /** A usable rota anywhere in the paste still wins over one that is not. */
    @Test
    fun `a usable rota beats an over-long one earlier in the message`() {
        val long = Pattern(
            id = "p",
            name = "long",
            anchor = DayNumber.of(2026, 9, 4),
            slots = List(Pattern.MAX_CYCLE_DAYS + 5) { "a" },
        )
        val longToken = ShareLink.encode(
            long,
            mapOf("a" to ShiftDefinition(id = "a", code = "a", name = "A")),
        )
        assertRota(RotaCode.read("$longToken\n\n${token()}"))
    }

    // ------------------------------------------------------------------ helpers

    private fun assertRota(result: ShareLinkResult) {
        val success = assertIs<ShareLinkResult.Success>(result, "expected a rota, got $result")
        assertEquals(pattern().anchor, success.anchor)
        assertEquals(pattern().name, success.name)
        assertEquals(pattern().slots, success.codes)
    }

    private fun pattern() = Pattern(
        id = "p1",
        name = "4 on, 4 off",
        anchor = DayNumber.of(2026, 9, 4),
        slots = Presets.FOUR_ON_FOUR_OFF.slots,
    )

    private fun definitions(): Map<String, ShiftDefinition> =
        pattern().slots.filterNotNull().distinct().associateWith { id ->
            ShiftDefinition(id = id, code = id, name = id)
        }

    private fun token() = ShareLink.encode(pattern(), definitions())

    /**
     * A copy of what [RotaCode.message] builds, without a repository.
     *
     * Deliberately a copy: if the real message changes shape, this test keeps
     * proving the old shape still imports — which is the thing that matters,
     * because codes sent last month are still in people's chat histories.
     */
    private fun shareMessage() = buildString {
        append("My rota — 4 on, 4 off (8-day cycle).\n\n")
        append(token())
        append("\n\nOpen Turnus, then Settings → Share and export → ")
        append("\"I have a code\", and paste that in.")
    }
}
