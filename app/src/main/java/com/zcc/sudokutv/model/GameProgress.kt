package com.zcc.sudokutv.model

/** One saved, unfinished game. Each difficulty owns its own slot. */
data class GameProgress(
    val difficulty: Difficulty,
    val puzzleId: Int,
    val entries: IntArray,
    val candidateMasks: IntArray,
    val selectedCell: Int,
    val elapsedMillis: Long,
) {
    fun encode(): String = listOf(
        VERSION, difficulty.name, puzzleId,
        entries.joinToString(","), candidateMasks.joinToString(","),
        selectedCell, elapsedMillis,
    ).joinToString("|")

    companion object {
        private const val VERSION = "1"

        fun decode(encoded: String): GameProgress? = runCatching {
            val fields = encoded.split('|')
            require(fields.size == 7 && fields[0] == VERSION)
            val difficulty = Difficulty.valueOf(fields[1])
            val puzzleId = fields[2].toInt()
            fun numbers(index: Int) = fields[index].split(',').map { it.toInt() }.toIntArray()
            val entries = numbers(3)
            val candidates = numbers(4)
            val selectedCell = fields[5].toInt()
            val elapsedMillis = fields[6].toLong()
            require(entries.size == CELL_COUNT && candidates.size == CELL_COUNT)
            require(entries.all { it in 0..SIDE })
            require(candidates.indices.all {
                candidates[it] in 0 until (1 shl SIDE) && Integer.bitCount(candidates[it]) <= 4
            })
            require(candidates.indices.all { entries[it] == 0 || candidates[it] == 0 })
            require(selectedCell in 0 until CELL_COUNT && elapsedMillis >= 0)
            GameProgress(difficulty, puzzleId, entries, candidates, selectedCell, elapsedMillis)
        }.getOrNull()
    }
}
