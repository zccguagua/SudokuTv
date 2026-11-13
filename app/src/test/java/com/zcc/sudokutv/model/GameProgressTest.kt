package com.zcc.sudokutv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameProgressTest {
    private fun progress(difficulty: Difficulty = Difficulty.HARD): GameProgress {
        val entries = IntArray(CELL_COUNT)
        val candidates = IntArray(CELL_COUNT)
        entries[3] = 7
        candidates[4] = 0b1010
        return GameProgress(difficulty, 12345, entries, candidates, 4, 61_000L)
    }

    @Test
    fun roundTripKeepsEveryField() {
        val original = progress()
        val decoded = GameProgress.decode(original.encode())
        assertEquals(Difficulty.HARD, decoded?.difficulty)
        assertEquals(12345, decoded?.puzzleId)
        assertTrue(original.entries.contentEquals(decoded?.entries))
        assertTrue(original.candidateMasks.contentEquals(decoded?.candidateMasks))
        assertEquals(4, decoded?.selectedCell)
        assertEquals(61_000L, decoded?.elapsedMillis)
    }

    @Test
    fun filledCellCannotKeepCandidates() {
        val original = progress()
        original.candidateMasks[3] = 0b0011
        assertNull(GameProgress.decode(original.encode()))
    }

    @Test
    fun rejectsUnknownVersion() {
        val encoded = progress().encode().replaceFirst("1|", "2|")
        assertNull(GameProgress.decode(encoded))
    }

    @Test
    fun rejectsTooManyCandidates() {
        val original = progress()
        original.candidateMasks[5] = 0b11111
        assertNull(GameProgress.decode(original.encode()))
    }
}
