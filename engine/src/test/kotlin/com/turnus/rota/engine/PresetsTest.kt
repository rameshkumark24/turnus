package com.turnus.rota.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * These pin the published definitions of each named rotation. People search for
 * these by name and will notice immediately if "DuPont" isn't DuPont, so the
 * shapes are asserted rather than trusted.
 */
class PresetsTest {

    @Test
    fun `cycle lengths and working days match the published definitions`() {
        val expected = mapOf(
            // key             cycle  working days
            "4on4off" to (8 to 4),
            "4on4off_dn" to (16 to 8),
            "pitman" to (14 to 7),
            "panama" to (28 to 14),
            "continental" to (8 to 6),
            "dupont" to (28 to 14),
            "5on2off" to (7 to 5),
        )

        expected.forEach { (key, shape) ->
            val preset = assertNotNull(Presets.byKey(key), "missing preset $key")
            val (cycle, working) = shape
            assertEquals(cycle, preset.cycleLength, "$key cycle length")
            assertEquals(working, preset.slots.count { it != null }, "$key working days")
        }
    }

    /** DuPont is 4 nights, 3 off, 3 days, 1 off, 3 nights, 3 off, 4 days, 7 off. */
    @Test
    fun `dupont has the right shape week by week`() {
        val s = Presets.DUPONT.slots
        assertEquals(listOf(ShiftCode.NIGHT).repeated(4), s.subList(0, 4))
        assertEquals(listOf(null, null, null), s.subList(4, 7))
        assertEquals(listOf(ShiftCode.DAY).repeated(3), s.subList(7, 10))
        assertEquals(null, s[10])
        assertEquals(listOf(ShiftCode.NIGHT).repeated(3), s.subList(11, 14))
        assertEquals(listOf(null, null, null), s.subList(14, 17))
        assertEquals(listOf(ShiftCode.DAY).repeated(4), s.subList(17, 21))
        assertTrue(s.subList(21, 28).all { it == null }, "final week must be all off")
    }

    /** Pitman averages 42 hours a week on 12-hour shifts: 7 working days in 14. */
    @Test
    fun `pitman is a two two three rhythm`() {
        val working = Presets.PITMAN.slots.map { it != null }
        assertEquals(
            listOf(true, true, false, false, true, true, true, false, false, true, true, false, false, false),
            working,
        )
    }

    @Test
    fun `panama runs the pitman rhythm on days then on nights`() {
        val s = Presets.PANAMA.slots
        val firstHalf = s.subList(0, 14).map { it != null }
        val secondHalf = s.subList(14, 28).map { it != null }
        assertEquals(firstHalf, secondHalf, "both halves share the 2-2-3 rhythm")
        assertTrue(s.subList(0, 14).filterNotNull().all { it == ShiftCode.DAY })
        assertTrue(s.subList(14, 28).filterNotNull().all { it == ShiftCode.NIGHT })
    }

    @Test
    fun `keys are unique and every preset is listed`() {
        val keys = Presets.ALL.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "duplicate preset key")
        keys.forEach { assertNotNull(Presets.byKey(it)) }
    }

    @Test
    fun `every preset converts to a usable pattern anchored on slot zero`() {
        val anchor = DayNumber.of(2026, 6, 1)
        Presets.ALL.forEach { preset ->
            val pattern = preset.toPattern(id = preset.key, anchor = anchor)
            assertEquals(preset.cycleLength, pattern.cycleLength)
            assertEquals(preset.slots[0], ShiftEngine.scheduled(pattern, anchor))
            // A full cycle later, we are back to slot zero.
            assertEquals(
                preset.slots[0],
                ShiftEngine.scheduled(pattern, anchor + preset.cycleLength.toLong()),
            )
        }
    }

    @Test
    fun `no preset is entirely off`() {
        Presets.ALL.forEach { preset ->
            assertTrue(
                preset.slots.any { it != null },
                "${preset.key} has no working days",
            )
        }
    }

    private fun <T> List<T>.repeated(times: Int): List<T> =
        List(times) { this[0] }
}
