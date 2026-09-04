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
            val decoded = ShareLink.decode(ShareLink.encode(original))

            val success = assertIs<ShareLinkResult.Success>(decoded, "failed for $original")
            assertEquals(original.anchor, success.anchor)
            assertEquals(original.name, success.name)
            assertEquals(original.slots, success.slots)
        }
    }

    @Test
    fun `round trips through a full url`() {
        val original = fourOnFourOff()
        val url = ShareLink.url(original)
        val token = ShareLink.tokenFrom(url)

        assertTrue(url.contains('#'), "the token must live in the fragment")
        val success = assertIs<ShareLinkResult.Success>(ShareLink.decode(token!!))
        assertEquals(original.slots, success.slots)
    }

    /**
     * The fragment is the whole privacy argument: nothing after `#` is sent in
     * an HTTP request, so a shared rota cannot reach the host.
     */
    @Test
    fun `puts the token after the hash and nothing before it`() {
        val url = ShareLink.url(fourOnFourOff(), base = "https://turnus.app/r/")
        assertEquals("https://turnus.app/r/", url.substringBefore('#'))
        assertTrue(url.substringAfter('#').startsWith("v1."))
    }

    @Test
    fun `assigns the caller's id rather than inventing one`() {
        val success = assertIs<ShareLinkResult.Success>(
            ShareLink.decode(ShareLink.encode(fourOnFourOff())),
        )
        assertEquals("local-id", success.toPattern("local-id").id)
    }

    // ---------------------------------------------------------------- awkward input

    @Test
    fun `a name containing the field separator survives`() {
        val original = Pattern("p", "Days; nights; and ;;; more", DayNumber.of(2026, 5, 4), listOf("a", null))
        val success = assertIs<ShareLinkResult.Success>(ShareLink.decode(ShareLink.encode(original)))
        assertEquals(original.name, success.name)
    }

    @Test
    fun `a unicode name survives`() {
        val original = Pattern("p", "Spätschicht — 夜勤 — Ærøskøbing", DayNumber.of(2026, 5, 4), listOf("a"))
        val success = assertIs<ShareLinkResult.Success>(ShareLink.decode(ShareLink.encode(original)))
        assertEquals(original.name, success.name)
    }

    @Test
    fun `an all off pattern survives`() {
        val original = Pattern("p", "Career break", DayNumber.of(2026, 5, 4), listOf(null, null, null))
        val success = assertIs<ShareLinkResult.Success>(ShareLink.decode(ShareLink.encode(original)))
        assertEquals(listOf(null, null, null), success.slots)
    }

    @Test
    fun `an anchor far before the epoch survives`() {
        val original = Pattern("p", "old", DayNumber(-30_000), Presets.DUPONT.slots)
        val success = assertIs<ShareLinkResult.Success>(ShareLink.decode(ShareLink.encode(original)))
        assertEquals(DayNumber(-30_000), success.anchor)
    }

    @Test
    fun `tolerates a leading hash and surrounding whitespace`() {
        val token = ShareLink.encode(fourOnFourOff())
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
        assertFailsWith<IllegalArgumentException> { ShareLink.encode(tooMany) }
    }

    @Test
    fun `rejects a shift id containing a separator`() {
        assertFailsWith<IllegalArgumentException> {
            ShareLink.encode(Pattern("p", "p", DayNumber(0), listOf("a,b")))
        }
        assertFailsWith<IllegalArgumentException> {
            ShareLink.encode(Pattern("p", "p", DayNumber(0), listOf("a;b")))
        }
    }

    // ---------------------------------------------------------------- shape

    @Test
    fun `stays short enough to share in a message`() {
        val token = ShareLink.encode(
            Pattern("p", "DuPont", DayNumber.of(2026, 9, 4), Presets.DUPONT.slots),
        )
        assertTrue(token.length < 120, "token was ${token.length} chars: $token")
    }

    @Test
    fun `is url safe`() {
        val rnd = Random(SEED)
        repeat(500) {
            val token = ShareLink.encode(randomPattern(rnd))
            assertTrue(
                token.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' },
                "token is not url safe: $token",
            )
        }
    }

    @Test
    fun `encoding is deterministic`() {
        val pattern = fourOnFourOff()
        assertEquals(ShareLink.encode(pattern), ShareLink.encode(pattern))
    }

    // ---------------------------------------------------------------- helpers

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
