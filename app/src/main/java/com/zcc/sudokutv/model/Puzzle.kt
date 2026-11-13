package com.zcc.sudokutv.model

import java.util.Locale

const val SIDE = 9
const val BLOCK_ROWS = 3
const val BLOCK_COLUMNS = 3
const val CELL_COUNT = SIDE * SIDE

/** The four rating buckets of the bundled puzzle bank. */
enum class Difficulty(val label: String, val fileName: String, val ratingHint: String) {
    EASY("简单", "easy", "评分 < 1.5"),
    MEDIUM("中等", "medium", "评分 < 2.5"),
    HARD("困难", "hard", "评分 < 5.0"),
    EXPERT("专家", "diabolical", "评分 ≥ 5.0"),
}

/**
 * A 9x9 puzzle taken from the bundled bank. The bank guarantees a unique solution, so the givens
 * plus the sudoku rules are enough to decide whether a completed grid is correct.
 */
data class Puzzle(
    val id: Int,
    val difficulty: Difficulty,
    val ratingTenths: Int,
    val givens: IntArray,
) {
    init {
        require(id >= 1)
        require(givens.size == CELL_COUNT)
        require(givens.all { it in 0..SIDE })
    }

    val rating: String get() = String.format(Locale.US, "%.1f", ratingTenths / 10.0)

    fun isGiven(index: Int): Boolean = givens[index] != 0

    fun isValidCompletion(values: IntArray): Boolean =
        values.size == CELL_COUNT &&
            values.indices.all { givens[it] == 0 || values[it] == givens[it] } &&
            SudokuRules.isValidSolution(values)

    override fun equals(other: Any?): Boolean =
        other is Puzzle && id == other.id && difficulty == other.difficulty &&
            ratingTenths == other.ratingTenths && givens.contentEquals(other.givens)

    override fun hashCode(): Int =
        ((id * 31 + difficulty.ordinal) * 31 + ratingTenths) * 31 + givens.contentHashCode()
}
