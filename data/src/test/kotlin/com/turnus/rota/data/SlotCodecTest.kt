package com.turnus.rota.data

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SlotCodecTest {

    @Test
    fun `round trips any slot list`() {
        val rnd = Random(20260905)
        repeat(5_000) {
            val original = List(rnd.nextInt(1, 41)) {
                if (rnd.nextBoolean()) null else "id-${rnd.nextInt(0, 6)}"
            }
            assertEquals(original, SlotCodec.decode(SlotCodec.encode(original)))
        }
    }

    @Test
    fun `distinguishes a day off from a shift`() {
        val encoded = SlotCodec.encode(listOf("a", null, "b"))
        assertEquals("a,-,b", encoded)
        assertEquals(listOf("a", null, "b"), SlotCodec.decode(encoded))
    }

    @Test
    fun `handles an all off cycle`() {
        assertEquals(listOf(null, null, null), SlotCodec.decode(SlotCodec.encode(listOf(null, null, null))))
    }

    @Test
    fun `handles a single slot`() {
        assertEquals(listOf("a"), SlotCodec.decode(SlotCodec.encode(listOf("a"))))
    }

    @Test
    fun `rejects an empty cycle`() {
        assertFailsWith<IllegalArgumentException> { SlotCodec.encode(emptyList()) }
        assertFailsWith<IllegalArgumentException> { SlotCodec.decode("") }
    }

    @Test
    fun `rejects ids that would collide with the format`() {
        assertFailsWith<IllegalArgumentException> { SlotCodec.encode(listOf("a,b")) }
        assertFailsWith<IllegalArgumentException> { SlotCodec.encode(listOf("-")) }
    }

    @Test
    fun `encoded length always matches slot count`() {
        val rnd = Random(7)
        repeat(500) {
            val slots = List(rnd.nextInt(1, 41)) { if (rnd.nextBoolean()) null else "x$it" }
            assertEquals(slots.size, SlotCodec.decode(SlotCodec.encode(slots)).size)
        }
    }
}
