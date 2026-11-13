package com.zcc.sudokutv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuRulesTest {
    private val solved = (
        "534678912" + "672195348" + "198342567" +
            "859761423" + "426853791" + "713924856" +
            "961537284" + "287419635" + "345286179"
        ).map { it - '0' }.toIntArray()

    @Test
    fun solvedGridIsValid() {
        assertTrue(SudokuRules.isValidSolution(solved))
        assertTrue(SudokuRules.conflictingCells(solved).isEmpty())
    }

    @Test
    fun duplicateInRowIsReported() {
        val broken = solved.copyOf()
        broken[0] = broken[1]
        assertFalse(SudokuRules.isValidSolution(broken))
        assertTrue(SudokuRules.conflictingCells(broken).containsAll(listOf(0, 1)))
    }

    @Test
    fun duplicateInColumnIsReported() {
        val broken = solved.copyOf()
        broken[0] = broken[9]
        assertTrue(SudokuRules.conflictingCells(broken).containsAll(listOf(0, 9)))
    }

    @Test
    fun duplicateInBlockIsReported() {
        val broken = solved.copyOf()
        broken[0] = broken[10]
        assertTrue(SudokuRules.conflictingCells(broken).containsAll(listOf(0, 10)))
    }

    @Test
    fun blankCellIsNotAConflict() {
        val partial = solved.copyOf()
        partial[0] = 0
        assertTrue(SudokuRules.conflictingCells(partial).isEmpty())
    }

    @Test
    fun completionMustKeepGivens() {
        val givens = solved.copyOf()
        givens.indices.forEach { if (it != 0) givens[it] = 0 }
        val puzzle = Puzzle(1, Difficulty.EASY, 12, givens)
        assertTrue(puzzle.isValidCompletion(solved))

        val changed = solved.copyOf()
        changed[0] = 6
        assertFalse(puzzle.isValidCompletion(changed))
    }
}
