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
