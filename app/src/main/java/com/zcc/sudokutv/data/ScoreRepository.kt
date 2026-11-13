package com.zcc.sudokutv.data

import android.content.Context
import com.zcc.sudokutv.model.Difficulty

class ScoreRepository(context: Context) {
    private val preferences = context.getSharedPreferences("sudoku_scores", Context.MODE_PRIVATE)

    fun scores(difficulty: Difficulty): List<Long> =
        preferences.getString(key(difficulty), null)
            ?.split(',')
            ?.mapNotNull(String::toLongOrNull)
            ?.sorted()
            ?.take(MAX_SCORES)
            .orEmpty()

    fun record(difficulty: Difficulty, elapsedSeconds: Long): Boolean {
        val oldScores = scores(difficulty)
        val updated = (oldScores + elapsedSeconds).sorted().take(MAX_SCORES)
        preferences.edit().putString(key(difficulty), updated.joinToString(",")).apply()
        return oldScores.isEmpty() || elapsedSeconds < oldScores.first()
    }

    private fun key(difficulty: Difficulty) = difficulty.name

    private companion object { const val MAX_SCORES = 10 }
}
