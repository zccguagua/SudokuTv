package com.zcc.sudokutv.data

import com.zcc.sudokutv.model.CELL_COUNT
import com.zcc.sudokutv.model.Difficulty
import com.zcc.sudokutv.model.Puzzle
import com.zcc.sudokutv.model.SIDE

/** Shared 42-byte record layout used by the generated bank assets. */
object PuzzleCodec {
    const val RECORD_BYTES = 42

    fun decodeRecord(bytes: ByteArray, id: Int, difficulty: Difficulty): Puzzle {
        require(bytes.size == RECORD_BYTES)
        val givens = IntArray(CELL_COUNT) { index ->
            val packed = bytes[index / 2].toInt() and 0xff
            if (index % 2 == 0) packed ushr 4 else packed and 0x0f
        }
        require(givens.all { it in 0..SIDE })
        return Puzzle(id, difficulty, bytes[41].toInt() and 0xff, givens)
    }
}
