package com.turnus.rota.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AnchorSolverTest {

    /**
     * The invariant the whole setup flow rests on: whichever candidate the user
     * picks, the resulting pattern must agree that today is the shift they
     * said. If this ever fails, every calendar built through setup is wrong.
     */
    @Test
    fun `every candidate produces a pattern that agrees about today`() {
        val rnd = Random(SEED)
        repeat(20_000) {
            val slots = randomSlots(rnd)
            val today = DayNumber(rnd.nextLong(-20_000, 20_000))
            val answer = slots[rnd.nextInt(slots.size)]

            val candidates = AnchorSolver.candidates(slots, answer, today)
            assertTrue(candidates.isNotEmpty(), "an answer taken from the cycle must match somewhere")

            candidates.forEach { candidate ->
                val pattern = AnchorSolver.patternFor(candidate, slots, "p", "test")
                assertEquals(
                    answer,
                    ShiftEngine.scheduled(pattern, today),
                    "candidate ${candidate.slotIndex} disagrees about today",
                )
            }
        }
    }

    /** "I'm off today" has to work exactly like naming a shift. */
    @Test
    fun `handles being off today`() {
        val slots = Presets.FOUR_ON_FOUR_OFF.slots
        val today = DayNumber.of(2026, 9, 5)

        val candidates = AnchorSolver.candidates(slots, null, today)

        assertEquals(4, candidates.size, "4-on-4-off has four off positions")
        candidates.forEach { candidate ->
            val pattern = AnchorSolver.patternFor(candidate, slots, "p", "test")
            assertEquals(null, ShiftEngine.scheduled(pattern, today))
        }
    }

    @Test
    fun `finds one candidate per matching slot`() {
        val slots = Presets.FOUR_ON_FOUR_OFF.slots
        val today = DayNumber.of(2026, 9, 5)

        val working = AnchorSolver.candidates(slots, ShiftCode.DAY, today)

        assertEquals(listOf(0, 1, 2, 3), working.map { it.slotIndex })
        assertEquals(today, working[0].anchor)
        assertEquals(today - 3, working[3].anchor)
    }

    /** A rota with no repeats needs no disambiguation question at all. */
    @Test
    fun `an unambiguous cycle yields exactly one candidate`() {
        val slots = listOf(ShiftCode.EARLY, ShiftCode.LATE, ShiftCode.NIGHT, null)
        val today = DayNumber.of(2026, 9, 5)

        assertEquals(1, AnchorSolver.candidates(slots, ShiftCode.LATE, today).size)
        assertEquals(1, AnchorSolver.candidates(slots, null, today).size)
    }

    @Test
    fun `a shift outside the cycle yields no candidates`() {
        val slots = Presets.FOUR_ON_FOUR_OFF.slots
        assertTrue(AnchorSolver.candidates(slots, ShiftCode.NIGHT, DayNumber(0)).isEmpty())
    }

    @Test
    fun `preview starts with the chosen slot and follows the cycle`() {
        val slots = Presets.FOUR_ON_FOUR_OFF.slots

        val preview = AnchorSolver.previewFrom(slots, slotIndex = 2, days = 7)

        assertEquals(
            listOf(ShiftCode.DAY, ShiftCode.DAY, null, null, null, null, ShiftCode.DAY),
            preview,
        )
    }

    /** The preview must be what the calendar will actually show, not an approximation. */
    @Test
    fun `preview matches what the engine resolves for those days`() {
        val rnd = Random(SEED)
        repeat(3_000) {
            val slots = randomSlots(rnd)
            val today = DayNumber(rnd.nextLong(-20_000, 20_000))
            val index = rnd.nextInt(slots.size)
            val candidate = AnchorSolver.Candidate(index, today - index.toLong())
            val pattern = AnchorSolver.patternFor(candidate, slots, "p", "test")

            val preview = AnchorSolver.previewFrom(slots, index, days = 14)

            preview.forEachIndexed { offset, expected ->
                assertEquals(expected, ShiftEngine.scheduled(pattern, today + offset.toLong()))
            }
        }
    }

    @Test
    fun `wraps around the end of the cycle`() {
        val slots = listOf("a", "b", "c")
        assertEquals(listOf("c", "a", "b", "c", "a"), AnchorSolver.previewFrom(slots, 2, 5))
    }

    // ------------------------------------------------------------ run position

    @Test
    fun `reports where a day sits in its run`() {
        val slots = Presets.FOUR_ON_FOUR_OFF.slots

        assertEquals(AnchorSolver.RunPosition(1, 4), AnchorSolver.runPosition(slots, 0))
        assertEquals(AnchorSolver.RunPosition(2, 4), AnchorSolver.runPosition(slots, 1))
        assertEquals(AnchorSolver.RunPosition(4, 4), AnchorSolver.runPosition(slots, 3))
        // The off run.
        assertEquals(AnchorSolver.RunPosition(1, 4), AnchorSolver.runPosition(slots, 4))
        assertEquals(AnchorSolver.RunPosition(4, 4), AnchorSolver.runPosition(slots, 7))
    }

    /** A rota is a loop, so a run spanning the cycle boundary is still one run. */
    @Test
    fun `runs wrap around the end of the cycle`() {
        // Two Days at the end, two more at the start: one run of four.
        val slots = listOf("d", "d", null, null, null, null, "d", "d")

        assertEquals(AnchorSolver.RunPosition(3, 4), AnchorSolver.runPosition(slots, 0))
        assertEquals(AnchorSolver.RunPosition(4, 4), AnchorSolver.runPosition(slots, 1))
        assertEquals(AnchorSolver.RunPosition(1, 4), AnchorSolver.runPosition(slots, 6))
        assertEquals(AnchorSolver.RunPosition(2, 4), AnchorSolver.runPosition(slots, 7))
    }

    @Test
    fun `a uniform cycle is one run the length of the cycle`() {
        val slots = listOf("d", "d", "d")
        repeat(3) { assertEquals(AnchorSolver.RunPosition(1, 3), AnchorSolver.runPosition(slots, it)) }
    }

    @Test
    fun `run position stays inside the cycle for every slot`() {
        val rnd = Random(SEED)
        repeat(5_000) {
            val slots = randomSlots(rnd)
            slots.indices.forEach { index ->
                val run = AnchorSolver.runPosition(slots, index)
                assertTrue(run.position in 1..run.length, "position ${run.position} of ${run.length}")
                assertTrue(run.length in 1..slots.size, "run ${run.length} exceeds cycle ${slots.size}")
            }
        }
    }

    @Test
    fun `rejects an empty cycle`() {
        assertFailsWith<IllegalArgumentException> {
            AnchorSolver.candidates(emptyList(), null, DayNumber(0))
        }
        assertFailsWith<IllegalArgumentException> {
            AnchorSolver.previewFrom(emptyList(), 0)
        }
    }

    private fun randomSlots(rnd: Random): List<String?> =
        List(rnd.nextInt(1, 41)) { if (rnd.nextBoolean()) null else "shift${rnd.nextInt(0, 4)}" }

    private companion object {
        const val SEED = 20260905L
    }
}
