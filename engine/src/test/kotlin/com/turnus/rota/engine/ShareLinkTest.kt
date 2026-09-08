package com.turnus.rota.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareLinkTest {

    // ---------------------------------------------------------------- round trip

    /** The property that matters: whatever goes in comes back out unchanged. */
    @Test
    fun `round trips any pattern`() {
        val rnd = Random(SEED)
        repeat(5_000) {
            val original = randomPattern(rnd)
            val decoded = ShareLink.decode(ShareLink.encode(original, defsFor(original)))

            val success = assertIs<ShareLinkResult.Success>(decoded, "failed for $original")
            assertEquals(original.anchor, success.anchor)
            assertEquals(original.name, success.name)
            assertEquals(original.slots, success.codes)
        }
    }

    @Test
    fun `round trips through a full url`() {
        val original = fourOnFourOff()
        val url = ShareLink.url(original, defsFor(original), base = "https://example.test/r/")
        val token = ShareLink.tokenFrom(url)

        assertTrue(url.contains('#'), "the token must live in the fragment")
        val success = assertIs<ShareLinkResult.Success>(ShareLink.decode(token!!))
        assertEquals(original.slots, success.codes)
    }

    /**
     * The fragment is the whole privacy argument: nothing after `#` is sent in
     * an HTTP request, so a shared rota cannot reach the host.
     */
    @Test
    fun `puts the token after the hash and nothing before it`() {
        val url = fourOnFourOff().let { ShareLink.url(it, defsFor(it), base = "https://example.test/r/") }
        assertEquals("https://example.test/r/", url.substringBefore('#'))
        assertTrue(url.substringAfter('#').startsWith("v1."))
    }

    @Test
    fun `assigns the caller's id rather than inventing one`() {
        val success = assertIs<ShareLinkResult.Success>(
            ShareLink.decode(fourOnFourOff().let { ShareLink.encode(it, defsFor(it)) }),
        )
        assertEquals("local-id", success.toPattern("local-id") { it }.id)
    }

    // ---------------------------------------------------------------- awkward input

    @Test
    fun `a name containing the field separator survives`() {
        val original = Pattern("p", "Days; nights; and ;;; more", DayNumber.of(2026, 5, 4), listOf("a", null))
        val success = assertIs<ShareLinkResult.Success>(ShareLink.decode(ShareLink.encode(original, defsFor(original))))
        assertEquals(original.name, success.name)
    }

    @Test
    fun `a unicode name survives`() {
        val original = Pattern("p", "Spätschicht — 夜勤 — Ærøskøbing", DayNumber.of(2026, 5, 4), listOf("a"))
        val success = assertIs<ShareLinkResult.Success>(ShareLink.decode(ShareLink.encode(original, defsFor(original))))
        assertEquals(original.name, success.name)
    }

    @Test
    fun `an all off pattern survives`() {
        val original = Pattern("p", "Career break", DayNumber.of(2026, 5, 4), listOf(null, null, null))
        val success = assertIs<ShareLinkResult.Success>(ShareLink.decode(ShareLink.encode(original, defsFor(original))))
        assertEquals(listOf(null, null, null), success.codes)
    }

    @Test
    fun `an anchor far before the epoch survives`() {
        val original = Pattern("p", "old", DayNumber(-30_000), Presets.DUPONT.slots)
        val success = assertIs<ShareLinkResult.Success>(ShareLink.decode(ShareLink.encode(original, defsFor(original))))
        assertEquals(DayNumber(-30_000), success.anchor)
    }

    @Test
    fun `tolerates a leading hash and surrounding whitespace`() {
        val token = fourOnFourOff().let { ShareLink.encode(it, defsFor(it)) }
        assertIs<ShareLinkResult.Success>(ShareLink.decode("  #$token  "))
    }

    // ---------------------------------------------------------------- failure modes

    /** A newer app's link must say "update", not "broken". They are different fixes. */
    @Test
    fun `reports an unsupported version distinctly from a broken link`() {
        val result = ShareLink.decode("v9.YWJjZGVm")
        val unsupported = assertIs<ShareLinkResult.UnsupportedVersion>(result)
        assertEquals("v9", unsupported.version)
    }

    @Test
    fun `reports malformed input without throwing`() {
        val bad = listOf(
            "",
            "   ",
            "v1",
            "v1.",
            ".abc",
            "notatoken",
            "v1.!!!not base64!!!",
            "v1." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("too;few".toByteArray()),
            // slot index points past the end of the code list
            "v1." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("100;day;05;name".toByteArray()),
            // anchor is not a number
            "v1." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("notanumber;day;0;name".toByteArray()),
            // empty slot string
            "v1." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("100;day;;name".toByteArray()),
        )
        bad.forEach { token ->
            assertIs<ShareLinkResult.Malformed>(
                ShareLink.decode(token),
                "expected Malformed for \"$token\"",
            )
        }
    }

    @Test
    fun `tokenFrom returns null when there is no fragment`() {
        assertNull(ShareLink.tokenFrom("https://turnus.app/r/"))
    }

    @Test
    fun `rejects more distinct shift types than the format can index`() {
        val tooMany = Pattern("p", "p", DayNumber(0), (0..36).map { "shift$it" })
        assertFailsWith<IllegalArgumentException> { ShareLink.encode(tooMany, defsFor(tooMany)) }
    }

    @Test
    fun `rejects a shift letter containing a separator`() {
        assertFailsWith<IllegalArgumentException> {
            Pattern("p", "p", DayNumber(0), listOf("a,b")).let { ShareLink.encode(it, defsFor(it)) }
        }
        assertFailsWith<IllegalArgumentException> {
            Pattern("p", "p", DayNumber(0), listOf("a;b")).let { ShareLink.encode(it, defsFor(it)) }
        }
    }

    // ---------------------------------------------------------------- shape

    @Test
    fun `stays short enough to share in a message`() {
        val pattern = Pattern("p", "DuPont", DayNumber.of(2026, 9, 4), Presets.DUPONT.slots)
        val token = ShareLink.encode(pattern, defsFor(pattern))
        assertTrue(token.length < 120, "token was ${token.length} chars: $token")
    }

    @Test
    fun `is url safe`() {
        val rnd = Random(SEED)
        repeat(500) {
            val token = randomPattern(rnd).let { ShareLink.encode(it, defsFor(it)) }
            assertTrue(
                token.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' },
                "token is not url safe: $token",
            )
        }
    }

    @Test
    fun `encoding is deterministic`() {
        val pattern = fourOnFourOff()
        assertEquals(ShareLink.encode(pattern, defsFor(pattern)), ShareLink.encode(pattern, defsFor(pattern)))
    }

    // ------------------------------------------------------- letters, not ids

    /**
     * The property the whole format exists for.
     *
     * Two installs generate different ids for the same shift, so a code built
     * from ids would decode into shifts the receiver does not have. What
     * travels is the letter on the calendar, which both people can see.
     */
    @Test
    fun `travels as letters so a different install can read it`() {
        val sender = Pattern(
            id = "sender-pattern",
            name = "Four on four off",
            anchor = DayNumber.of(2026, 9, 4),
            slots = listOf("uuid-aaa", "uuid-aaa", "uuid-bbb", null),
        )
        val senderShifts = mapOf(
            "uuid-aaa" to ShiftDefinition("uuid-aaa", "D", "Day", 7 * 60, 12 * 60),
            "uuid-bbb" to ShiftDefinition("uuid-bbb", "N", "Night", 19 * 60, 12 * 60),
        )

        val success = assertIs<ShareLinkResult.Success>(
            ShareLink.decode(ShareLink.encode(sender, senderShifts)),
        )

        assertEquals(listOf("D", "D", "N", null), success.codes)
        assertEquals(listOf("D", "N"), success.shiftCodes)

        // The receiver has the same two letters under entirely different ids,
        // and their own hours. Both are theirs to keep.
        val receiverIds = mapOf("D" to "local-1", "N" to "local-2")
        val imported = success.toPattern("receiver-pattern") { receiverIds.getValue(it) }

        assertEquals(listOf("local-1", "local-1", "local-2", null), imported.slots)
        assertEquals(sender.anchor, imported.anchor)
        assertEquals("Four on four off", imported.name)
    }

    @Test
    fun `refuses to encode a slot whose shift is unknown`() {
        val pattern = Pattern("p", "p", DayNumber(0), listOf("known", "missing"))
        assertFailsWith<IllegalArgumentException> {
            ShareLink.encode(pattern, mapOf("known" to ShiftDefinition("known", "D", "Day")))
        }
    }

    /**
     * Two shifts can share a letter only if the sender typed the same letter
     * twice, which the shift editor refuses. If it ever happened, the cycle
     * must still decode to something coherent rather than to nonsense.
     */
    @Test
    fun `collapses two ids that share a letter`() {
        val pattern = Pattern("p", "p", DayNumber(0), listOf("a", "b"))
        val definitions = mapOf(
            "a" to ShiftDefinition("a", "D", "Day"),
            "b" to ShiftDefinition("b", "D", "Days"),
        )
        val success = assertIs<ShareLinkResult.Success>(
            ShareLink.decode(ShareLink.encode(pattern, definitions)),
        )
        assertEquals(listOf("D", "D"), success.codes)
    }

    // ---------------------------------------------------------------- helpers

    // ------------------------------------------------- what a paste does to it

    /**
     * The property behind the whole fix: a token is what it is, whatever a
     * messaging app did to its layout on the way.
     *
     * Invisible characters are injected at random positions in a real token —
     * including position zero and the very end — and the decoded rota must be
     * indistinguishable from the one that was encoded. Anything less than a
     * property test here misses the one case that matters, which is a break
     * landing exactly on the version dot.
     */
    @Test
    fun `survives anything invisible inserted anywhere in the token`() {
        val rnd = Random(SEED)
        repeat(5_000) {
            val original = randomPattern(rnd)
            val token = ShareLink.encode(original, defsFor(original))

            val mangled = buildString {
                append(token)
                repeat(rnd.nextInt(1, 6)) {
                    val where = rnd.nextInt(0, length + 1)
                    insert(where, INVISIBLE[rnd.nextInt(INVISIBLE.size)])
                }
            }

            val decoded = ShareLink.decode(mangled)
            val success = assertIs<ShareLinkResult.Success>(
                decoded,
                "failed for $original mangled as ${mangled.map { it.code }}",
            )
            assertEquals(original.anchor, success.anchor)
            assertEquals(original.name, success.name)
            assertEquals(original.slots, success.codes)
        }
    }

    /** The reported case: a client hard-wrapped the token onto two lines. */
    @Test
    fun `decodes a token broken across lines`() {
        val original = fourOnFourOff()
        val token = ShareLink.encode(original, defsFor(original))
        val wrapped = token.chunked(24).joinToString("\r\n")

        val success = assertIs<ShareLinkResult.Success>(ShareLink.decode(wrapped))
        assertEquals(original.slots, success.codes)
        assertEquals(original.anchor, success.anchor)
    }

    /**
     * A shift letter may legitimately be a space, and the name may contain
     * anything at all. Stripping happens to the *token*, which is Base64 —
     * never to the payload, which is what those live in.
     */
    @Test
    fun `stripping does not reach inside the payload`() {
        val pattern = Pattern(
            id = "p",
            name = "  Nights\u00a0and\ndays  ",
            anchor = DayNumber.of(2026, 3, 29),
            slots = listOf("a", null, "a"),
        )
        val definitions = mapOf("a" to ShiftDefinition(id = "a", code = " ", name = "Space"))

        val success = assertIs<ShareLinkResult.Success>(
            ShareLink.decode(ShareLink.encode(pattern, definitions)),
        )
        assertEquals("  Nights\u00a0and\ndays  ", success.name)
        assertEquals(listOf(" ", null, " "), success.codes)
    }

    /**
     * Prose is a broken code, not a code from the future.
     *
     * With whitespace now stripped before the version is read, an ordinary
     * sentence containing a full stop would otherwise be reported as
     * "made by a newer version of Turnus" — sending the user to the Play Store
     * to fix a message they pasted by accident.
     */
    @Test
    fun `prose is malformed rather than a version complaint`() {
        listOf(
            "Hi mate. Here is my rota",
            "See you at 6. Cheers",
            "turnus.rota",
            "My rota - 4 on, 4 off (8-day cycle).",
        ).forEach {
            assertEquals(ShareLinkResult.Malformed, ShareLink.decode(it), "for '$it'")
        }
    }

    /** But a real version from the future still says so. */
    @Test
    fun `a genuine newer version is still reported as one`() {
        assertEquals(
            ShareLinkResult.UnsupportedVersion("v2"),
            ShareLink.decode("v2.YW55dGhpbmc"),
        )
        assertEquals(
            ShareLinkResult.UnsupportedVersion("v10"),
            ShareLink.decode("  v10 . YW55dGhpbmc  "),
        )
    }

    /**
     * A definition for every id the pattern uses, lettered with the id itself.
     *
     * Keeping letter and id equal lets the older tests go on asserting against
     * `slots`, while the tests above cover the case that matters — the two
     * being different.
     */
    private fun defsFor(pattern: Pattern): Map<String, ShiftDefinition> =
        pattern.slots.filterNotNull().distinct().associateWith { id ->
            ShiftDefinition(id = id, code = id, name = id)
        }

    private fun fourOnFourOff() = Pattern(
        id = "p1",
        name = "4 on, 4 off",
        anchor = DayNumber.of(2026, 9, 4),
        slots = Presets.FOUR_ON_FOUR_OFF.slots,
    )

    private fun randomPattern(rnd: Random): Pattern {
        val length = rnd.nextInt(1, 41)
        return Pattern(
            id = "ignored",
            name = NAMES[rnd.nextInt(NAMES.size)],
            anchor = DayNumber(rnd.nextLong(-40_000, 40_000)),
            slots = List(length) { if (rnd.nextBoolean()) null else "shift${rnd.nextInt(0, 5)}" },
        )
    }

    private companion object {
        const val SEED = 20260904L

        /**
         * What actually arrives in a paste: hard wraps, the non-breaking space
         * several clients wrap with, the zero-width space and joiners, the
         * bidirectional marks, the byte-order mark, and the soft hyphen a text
         * renderer leaves at a wrap point.
         */
        val INVISIBLE = listOf(
            ' ', '\t', '\n', '\r',
            '\u00a0', '\u00ad', '\u2007', '\u202f',
            '\u200b', '\u200c', '\u200d', '\u200e', '\u200f', '\ufeff',
        )

        val NAMES = listOf(
            "4 on, 4 off",
            "Spätschicht",
            "",
            "Nights; days",
            "A very long rotation name that someone typed out in full",
            "Ærøskøbing",
        )
    }
}
