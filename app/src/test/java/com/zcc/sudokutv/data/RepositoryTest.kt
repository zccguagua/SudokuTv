package com.zcc.sudokutv.data

import com.zcc.sudokutv.model.CELL_COUNT
import com.zcc.sudokutv.model.Difficulty
import com.zcc.sudokutv.model.GameProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun scoresKeepTheTenFastestPerDifficulty() {
        val repository = ScoreRepository(context)
        (30 downTo 1).forEach { repository.record(Difficulty.HARD, it.toLong()) }
        val scores = repository.scores(Difficulty.HARD)
        assertEquals(10, scores.size)
        assertEquals(listOf(1L, 2L, 3L), scores.take(3))
        assertTrue(repository.scores(Difficulty.EXPERT).isEmpty())
        assertTrue(repository.record(Difficulty.EXPERT, 50))
        assertFalse(repository.record(Difficulty.EXPERT, 60))
    }

    @Test
    fun progressKeepsOneSlotPerDifficulty() {
        val repository = ProgressRepository(context)
        assertFalse(repository.hasAny())
        assertNull(repository.latestDifficulty())

        repository.save(progress(Difficulty.EASY, 11))
        repository.save(progress(Difficulty.EXPERT, 22))
        assertTrue(repository.hasAny())
        assertTrue(repository.has(Difficulty.EASY))
        assertTrue(repository.has(Difficulty.EXPERT))
        assertEquals(Difficulty.EXPERT, repository.latestDifficulty())
        assertEquals(22, repository.load(Difficulty.EXPERT)?.puzzleId)

        repository.clear(Difficulty.EXPERT)
        assertFalse(repository.has(Difficulty.EXPERT))
        assertEquals(Difficulty.EASY, repository.latestDifficulty())
    }

    private fun progress(difficulty: Difficulty, id: Int) = GameProgress(
        difficulty = difficulty,
        puzzleId = id,
        entries = IntArray(CELL_COUNT),
        candidateMasks = IntArray(CELL_COUNT),
        selectedCell = 0,
        elapsedMillis = 1_000L,
    )
}
