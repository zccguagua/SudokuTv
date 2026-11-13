package com.zcc.sudokutv.data

import android.content.Context
import com.zcc.sudokutv.model.Difficulty
import com.zcc.sudokutv.model.GameProgress

/**
 * One unfinished game per difficulty. "latest" only remembers which slot to resume from the
 * home screen shortcut; every slot stays readable on its own.
 */
class ProgressRepository(context: Context) {
    private val preferences = context.getSharedPreferences("sudoku_progress", Context.MODE_PRIVATE)

    fun load(difficulty: Difficulty): GameProgress? =
        preferences.getString(key(difficulty), null)?.let(GameProgress::decode)

    fun has(difficulty: Difficulty): Boolean = preferences.contains(key(difficulty))

    fun hasAny(): Boolean = Difficulty.entries.any(::has)

    fun latestDifficulty(): Difficulty? {
        val latest = preferences.getString(KEY_LATEST, null)
            ?.let { name -> Difficulty.entries.firstOrNull { it.name == name } }
        if (latest != null && has(latest)) return latest
        return Difficulty.entries.firstOrNull(::has)
    }

    fun save(progress: GameProgress) {
        preferences.edit()
            .putString(key(progress.difficulty), progress.encode())
            .putString(KEY_LATEST, progress.difficulty.name)
            .apply()
    }

    fun clear(difficulty: Difficulty) {
        val editor = preferences.edit().remove(key(difficulty))
        if (preferences.getString(KEY_LATEST, null) == difficulty.name) editor.remove(KEY_LATEST)
        editor.apply()
    }

    private fun key(difficulty: Difficulty) = "slot_${difficulty.name}"

    private companion object { const val KEY_LATEST = "latest" }
}
