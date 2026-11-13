package com.zcc.sudokutv.model

/** Sudoku rules for a fixed 9x9 board with 3x3 blocks. */
object SudokuRules {
    fun isValidSolution(values: IntArray): Boolean {
        if (values.size != CELL_COUNT || values.any { it !in 1..SIDE }) return false
        val expected = (1..SIDE).toSet()
        for (row in 0 until SIDE) {
            if ((0 until SIDE).map { values[row * SIDE + it] }.toSet() != expected) return false
        }
        for (column in 0 until SIDE) {
            if ((0 until SIDE).map { values[it * SIDE + column] }.toSet() != expected) return false
        }
        for (blockRow in 0 until SIDE step BLOCK_ROWS) {
            for (blockColumn in 0 until SIDE step BLOCK_COLUMNS) {
                val block = buildSet {
                    for (row in blockRow until blockRow + BLOCK_ROWS) {
                        for (column in blockColumn until blockColumn + BLOCK_COLUMNS) {
                            add(values[row * SIDE + column])
                        }
                    }
                }
                if (block != expected) return false
            }
        }
        return true
    }

    /** Returns every cell that duplicates a value in its row, column or block. */
    fun conflictingCells(values: IntArray): Set<Int> {
        if (values.size != CELL_COUNT) return values.indices.toSet()
        val conflicts = mutableSetOf<Int>()

        values.forEachIndexed { index, value ->
            if (value !in 0..SIDE) conflicts += index
        }

        fun collectDuplicates(indices: Iterable<Int>) {
            indices
                .filter { values[it] in 1..SIDE }
                .groupBy { values[it] }
                .values
                .filter { it.size > 1 }
                .forEach { conflicts.addAll(it) }
        }

        for (row in 0 until SIDE) {
            collectDuplicates((0 until SIDE).map { column -> row * SIDE + column })
        }
        for (column in 0 until SIDE) {
            collectDuplicates((0 until SIDE).map { row -> row * SIDE + column })
        }
        for (blockRow in 0 until SIDE step BLOCK_ROWS) {
            for (blockColumn in 0 until SIDE step BLOCK_COLUMNS) {
                collectDuplicates(buildList {
                    for (row in blockRow until blockRow + BLOCK_ROWS) {
                        for (column in blockColumn until blockColumn + BLOCK_COLUMNS) {
                            add(row * SIDE + column)
                        }
                    }
                })
            }
        }
        return conflicts
    }
}
