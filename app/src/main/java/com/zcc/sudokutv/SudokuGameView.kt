package com.zcc.sudokutv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.zcc.sudokutv.data.ProgressRepository
import com.zcc.sudokutv.data.PuzzleBank
import com.zcc.sudokutv.data.ScoreRepository
import com.zcc.sudokutv.model.BLOCK_COLUMNS
import com.zcc.sudokutv.model.BLOCK_ROWS
import com.zcc.sudokutv.model.CELL_COUNT
import com.zcc.sudokutv.model.Difficulty
import com.zcc.sudokutv.model.GameProgress
import com.zcc.sudokutv.model.Puzzle
import com.zcc.sudokutv.model.SIDE
import com.zcc.sudokutv.model.SudokuRules
import kotlin.math.min

private enum class Page { HOME, LEVEL_SELECT, GAME, REWARD, SCORES }
private enum class PickerMode { VALUE, CANDIDATES }
private enum class SideFocus { NONE, CLEAR_ALL, NEXT }

private data class PickerLayout(
    val key: Float,
    val gap: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val gridLeft: Float,
    val gridTop: Float,
) {
    val clearButton: RectF
        get() = RectF(left + 34f, bottom - 92f, right - 34f, bottom - 24f)
}

class SudokuGameView(context: Context, private val exitApp: () -> Unit) : View(context) {
    private val ink = Color.rgb(8, 19, 29)
    private val panel = Color.rgb(16, 37, 53)
    private val panelLight = Color.rgb(24, 54, 74)
    private val mint = Color.rgb(86, 214, 201)
    private val cream = Color.rgb(244, 240, 232)
    private val muted = Color.rgb(157, 176, 188)
    private val coral = Color.rgb(255, 139, 123)
    private val gold = Color.rgb(255, 208, 106)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scores = ScoreRepository(context)
    private val progress = ProgressRepository(context)
    private val bank = PuzzleBank(context)

    private var page = Page.HOME
    private var homeFocus = 0
    private var difficulty = Difficulty.EASY
    private var exitOpen = false
    private var exitSelected = false

    private var levelDifficulty = Difficulty.EASY
    private val levelInput = StringBuilder()
    private var levelFocus = 4
    private var levelMessage: String? = null

    private var scoreDifficulty = Difficulty.EASY
    private var scoreFocus = 4

    private var puzzle: Puzzle? = null
    private var entries = IntArray(0)
    private var candidateMasks = IntArray(0)
    private var selectedCell = 0
    private var incorrectCells = emptySet<Int>()
    private var statusMessage: String? = null
    private var sideFocus = SideFocus.NONE
    private var rewardFocus = 1

    private var pickerOpen = false
    private var pickerSelection = 4
    private var pickerClearFocused = false
    private var pickerMode = PickerMode.VALUE
    private var pickerDraftMask = 0
    private var pickerLimitReached = false

    private var startedAt = -1L
    private var accumulatedMillis = 0L
    private var elapsedSeconds = 0L
    private var newRecord = false

    private val touchDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true

        override fun onSingleTapUp(event: MotionEvent): Boolean {
            performClick()
            val point = toDesignPoint(event.x, event.y)
            if (handleTap(point[0], point[1])) {
                saveProgress()
                invalidate()
            }
            return true
        }

        override fun onLongPress(event: MotionEvent) {
            val point = toDesignPoint(event.x, event.y)
            if (handleLongPress(point[0], point[1])) invalidate()
        }
    })

    private val ticker = object : Runnable {
        override fun run() {
            if (page == Page.GAME && startedAt >= 0L) {
                elapsedSeconds = currentElapsedMillis() / 1000L
                invalidate()
            }
            postDelayed(this, 250L)
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(ticker, 250L)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
        canvas.save()
        canvas.translate((width - DESIGN_WIDTH * scale) / 2f, (height - DESIGN_HEIGHT * scale) / 2f)
        canvas.scale(scale, scale)
        drawBackground(canvas)
        when (page) {
            Page.HOME -> drawHome(canvas)
            Page.LEVEL_SELECT -> drawLevelSelect(canvas)
            Page.GAME -> drawGame(canvas)
            Page.REWARD -> drawReward(canvas)
            Page.SCORES -> drawScores(canvas)
        }
        canvas.restore()
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        touchDetector.onTouchEvent(event)
        return true
    }

    fun handleKey(keyCode: Int): Boolean {
        val handled = when (page) {
            Page.HOME -> handleHomeKey(keyCode)
            Page.LEVEL_SELECT -> handleLevelKey(keyCode)
            Page.GAME -> handleGameKey(keyCode)
            Page.REWARD -> handleRewardKey(keyCode)
            Page.SCORES -> handleScoresKey(keyCode)
        }
        if (handled) {
            saveProgress()
            invalidate()
        }
        return handled
    }

    fun saveProgress() {
        if (page != Page.GAME) return
        val current = puzzle ?: return
        progress.save(
            GameProgress(current.difficulty, current.id, entries, candidateMasks, selectedCell, currentElapsedMillis())
        )
    }

    fun pauseGame() {
        if (page == Page.GAME) {
            accumulatedMillis = currentElapsedMillis()
            startedAt = -1L
            elapsedSeconds = accumulatedMillis / 1000L
        }
        saveProgress()
    }

    fun resumeGame() {
        if (page == Page.GAME && startedAt < 0L) startedAt = SystemClock.elapsedRealtime()
    }

    // ---------------------------------------------------------------- navigation

    private fun handleTap(x: Float, y: Float): Boolean = when (page) {
        Page.HOME -> handleHomeTap(x, y)
        Page.LEVEL_SELECT -> handleLevelTap(x, y)
        Page.GAME -> handleGameTap(x, y)
        Page.REWARD -> handleRewardTap(x, y)
        Page.SCORES -> handleScoresTap(x, y)
    }

    private fun handleLongPress(x: Float, y: Float): Boolean {
        if (page != Page.GAME || pickerOpen) return false
        val current = puzzle ?: return false
        val cellIndex = boardCellAt(x, y) ?: return false
        selectedCell = cellIndex
        sideFocus = SideFocus.NONE
        if (!current.isGiven(cellIndex) && entries[cellIndex] == 0) {
            openCandidatePicker()
        }
        return true
    }

    // ---------------------------------------------------------------- home

    private fun difficultyCardRect(index: Int): RectF {
        val gap = 20f
        val width = (914f - gap * (Difficulty.entries.size - 1)) / Difficulty.entries.size
        val left = 802f + index * (width + gap)
        return RectF(left, 264f, left + width, 452f)
    }

    private fun homeButtonRect(index: Int): RectF = when (index) {
        0 -> RectF(802f, 800f, 1076f, 920f)
        1 -> RectF(1092f, 800f, 1366f, 920f)
        else -> RectF(1382f, 800f, 1716f, 920f)
    }

    private fun handleHomeKey(keyCode: Int): Boolean {
        if (exitOpen) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> exitSelected = false
                KeyEvent.KEYCODE_DPAD_RIGHT -> exitSelected = true
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (exitSelected) exitApp() else exitOpen = false
                }
                KeyEvent.KEYCODE_BACK -> exitOpen = false
                else -> return true
            }
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> homeFocus = if (homeFocus < 4) {
                (homeFocus - 1).coerceAtLeast(0)
            } else {
                (homeFocus - 1).coerceAtLeast(4)
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> homeFocus = if (homeFocus < 4) {
                (homeFocus + 1).coerceAtMost(3)
            } else {
                (homeFocus + 1).coerceAtMost(6)
            }
            KeyEvent.KEYCODE_DPAD_UP -> homeFocus = if (homeFocus < 4) homeFocus else (homeFocus - 4).coerceAtMost(3)
            KeyEvent.KEYCODE_DPAD_DOWN -> homeFocus = if (homeFocus < 4) (4 + homeFocus).coerceAtMost(6) else homeFocus
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> activateHome()
            KeyEvent.KEYCODE_BACK -> {
                exitSelected = false
                exitOpen = true
            }
            else -> return false
        }
        return true
    }

    private fun activateHome() {
        when (homeFocus) {
            in 0..3 -> {
                difficulty = Difficulty.entries[homeFocus]
                startDifficulty(difficulty)
            }
            4 -> {
                scoreDifficulty = difficulty
                scoreFocus = 4
                page = Page.SCORES
            }
            5 -> {
                levelDifficulty = difficulty
                levelInput.clear()
                levelFocus = 4
                levelMessage = null
                page = Page.LEVEL_SELECT
            }
            6 -> progress.latestDifficulty()?.let(::startDifficulty)
        }
    }

    private fun handleHomeTap(x: Float, y: Float): Boolean {
        if (exitOpen) {
            when {
                contains(x, y, 485f, 588f, 904f, 710f) -> {
                    exitSelected = false
                    exitOpen = false
                }
                contains(x, y, 936f, 588f, 1435f, 710f) -> exitApp()
                else -> Unit
            }
            return true
        }
        Difficulty.entries.forEachIndexed { index, item ->
            if (difficultyCardRect(index).contains(x, y)) {
                homeFocus = index
                difficulty = item
                startDifficulty(item)
                return true
            }
        }
        repeat(3) { index ->
            if (homeButtonRect(index).contains(x, y)) {
                homeFocus = 4 + index
                activateHome()
                return true
            }
        }
        return false
    }

    // ---------------------------------------------------------------- level select

    private fun levelTabRect(index: Int): RectF =
        RectF(96f + index * 196f, 140f, 96f + index * 196f + 180f, 206f)

    private fun levelKeyRect(index: Int): RectF {
        val row = index / 3
        val column = index % 3
        return RectF(LEVEL_KEY_LEFT + column * (LEVEL_KEY + LEVEL_KEY_GAP), LEVEL_KEY_TOP + row * (LEVEL_KEY + LEVEL_KEY_GAP),
            LEVEL_KEY_LEFT + column * (LEVEL_KEY + LEVEL_KEY_GAP) + LEVEL_KEY,
            LEVEL_KEY_TOP + row * (LEVEL_KEY + LEVEL_KEY_GAP) + LEVEL_KEY)
    }

    private fun levelButtonRect(index: Int): RectF = when (index) {
        0 -> RectF(96f, 780f, 340f, 890f)
        else -> RectF(360f, 780f, 604f, 890f)
    }

    private fun levelMaxDigits(): Int = bank.count(levelDifficulty).toString().length

    private fun handleLevelKey(keyCode: Int): Boolean {
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            appendLevelDigit(keyCode - KeyEvent.KEYCODE_0)
            return true
        }
        if (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
            appendLevelDigit(keyCode - KeyEvent.KEYCODE_NUMPAD_0)
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
                if (levelInput.isNotEmpty()) levelInput.deleteCharAt(levelInput.length - 1)
                levelMessage = null
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> levelFocus = when {
                levelFocus in 0..3 -> (levelFocus - 1).coerceAtLeast(0)
                levelFocus in 4..15 -> if ((levelFocus - 4) % 3 > 0) levelFocus - 1 else levelFocus
                levelFocus == 17 -> 16
                else -> levelFocus
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> levelFocus = when {
                levelFocus in 0..3 -> (levelFocus + 1).coerceAtMost(3)
                levelFocus in 4..15 -> if ((levelFocus - 4) % 3 < 2) levelFocus + 1 else levelFocus
                levelFocus == 16 -> 17
                else -> levelFocus
            }
            KeyEvent.KEYCODE_DPAD_UP -> levelFocus = when {
                levelFocus in 0..3 -> levelFocus
                levelFocus in 4..15 -> if (levelFocus - 4 >= 3) levelFocus - 3 else levelFocus - 4
                levelFocus == 16 -> 13
                levelFocus == 17 -> 14
                else -> levelFocus
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> levelFocus = when {
                levelFocus in 0..3 -> 4 + levelFocus.coerceAtMost(2)
                levelFocus in 4..15 -> if (levelFocus - 4 < 9) levelFocus + 3 else 16
                else -> levelFocus
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> activateLevelFocus()
            KeyEvent.KEYCODE_BACK -> page = Page.HOME
            else -> return false
        }
        return true
    }

    private fun activateLevelFocus() {
        when {
            levelFocus in 0..3 -> {
                levelDifficulty = Difficulty.entries[levelFocus]
                levelMessage = null
            }
            levelFocus in 4..15 -> when (val key = levelFocus - 4) {
                in 0..8 -> appendLevelDigit(key + 1)
                9 -> if (levelInput.isNotEmpty()) levelInput.deleteCharAt(levelInput.length - 1)
                10 -> appendLevelDigit(0)
                else -> confirmLevelInput()
            }
            levelFocus == 16 -> {
                levelInput.clear()
                page = Page.HOME
            }
            levelFocus == 17 -> {
                levelInput.clear()
                levelMessage = null
                startRandom(levelDifficulty)
            }
        }
    }

    private fun appendLevelDigit(digit: Int) {
        if (digit == 0 && levelInput.isEmpty()) return
        if (levelInput.length >= levelMaxDigits()) return
        levelInput.append(digit)
        levelMessage = null
    }

    private fun confirmLevelInput() {
        val count = bank.count(levelDifficulty)
        val id = levelInput.toString().toIntOrNull()
        if (id == null || id !in 1..count) {
            levelMessage = "请输入 1–$count"
            return
        }
        val selected = bank.read(levelDifficulty, id)
        if (selected == null) {
            levelMessage = "题库读取失败，请重试"
            return
        }
        levelMessage = null
        beginGame(selected)
    }

    private fun handleLevelTap(x: Float, y: Float): Boolean {
        Difficulty.entries.forEachIndexed { index, item ->
            if (levelTabRect(index).contains(x, y)) {
                levelFocus = index
                levelDifficulty = item
                levelMessage = null
                return true
            }
        }
        repeat(12) { index ->
            if (levelKeyRect(index).contains(x, y)) {
                levelFocus = 4 + index
                activateLevelFocus()
                return true
            }
        }
        repeat(2) { index ->
            if (levelButtonRect(index).contains(x, y)) {
                levelFocus = 16 + index
                activateLevelFocus()
                return true
            }
        }
        return false
    }

    // ---------------------------------------------------------------- game start / resume

    private fun startDifficulty(target: Difficulty) {
        difficulty = target
        if (progress.has(target)) startSaved(target) else startRandom(target)
    }

    private fun startRandom(target: Difficulty) {
        val id = bank.randomId(target)
        val selected = bank.read(target, id) ?: return
        beginGame(selected)
    }

    private fun startSaved(target: Difficulty) {
        val saved = progress.load(target)
        val selected = saved?.let { bank.read(it.difficulty, it.puzzleId) }
        if (saved == null || selected == null || !isConsistent(saved, selected)) {
            progress.clear(target)
            startRandom(target)
            return
        }
        puzzle = selected
        entries = saved.entries.copyOf()
        candidateMasks = saved.candidateMasks.copyOf()
        selectedCell = saved.selectedCell
        incorrectCells = emptySet()
        statusMessage = null
        sideFocus = SideFocus.NONE
        pickerOpen = false
        accumulatedMillis = saved.elapsedMillis
        elapsedSeconds = accumulatedMillis / 1000L
        startedAt = SystemClock.elapsedRealtime()
        newRecord = false
        page = Page.GAME
    }

    private fun isConsistent(saved: GameProgress, selected: Puzzle): Boolean =
        saved.entries.size == CELL_COUNT &&
            saved.candidateMasks.size == CELL_COUNT &&
            saved.selectedCell in 0 until CELL_COUNT &&
            saved.entries.indices.all { !selected.isGiven(it) || saved.entries[it] == selected.givens[it] }

    private fun beginGame(selected: Puzzle) {
        puzzle = selected
        difficulty = selected.difficulty
        entries = selected.givens.copyOf()
        candidateMasks = IntArray(CELL_COUNT)
        selectedCell = selected.givens.indexOfFirst { it == 0 }.coerceAtLeast(0)
        incorrectCells = emptySet()
        statusMessage = null
        sideFocus = SideFocus.NONE
        pickerOpen = false
        accumulatedMillis = 0L
        elapsedSeconds = 0L
        startedAt = SystemClock.elapsedRealtime()
        newRecord = false
        page = Page.GAME
        saveProgress()
    }

    private fun clearAllEntries() {
        val current = puzzle ?: return
        entries = current.givens.copyOf()
        candidateMasks = IntArray(CELL_COUNT)
        incorrectCells = emptySet()
        pickerOpen = false
        statusMessage = "已清除所有填写"
        sideFocus = SideFocus.NONE
    }

    private fun nextPuzzle() {
        val current = puzzle ?: return
        val id = bank.randomId(current.difficulty, current.id)
        val next = bank.read(current.difficulty, id) ?: return
        beginGame(next)
    }

    private fun currentElapsedMillis(): Long = accumulatedMillis +
        if (startedAt >= 0L) (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L) else 0L

    // ---------------------------------------------------------------- game keys

    private fun handleGameKey(keyCode: Int): Boolean {
        val current = puzzle ?: return false
        if (pickerOpen) return handlePickerKey(keyCode)

        if (sideFocus != SideFocus.NONE) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN ->
                    sideFocus = if (sideFocus == SideFocus.CLEAR_ALL) SideFocus.NEXT else SideFocus.CLEAR_ALL
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (sideFocus == SideFocus.CLEAR_ALL) clearAllEntries() else nextPuzzle()
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK -> sideFocus = SideFocus.NONE
                else -> return false
            }
            return true
        }

        val row = selectedCell / SIDE
        val column = selectedCell % SIDE
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> selectedCell = (row - 1).coerceAtLeast(0) * SIDE + column
            KeyEvent.KEYCODE_DPAD_DOWN -> selectedCell = (row + 1).coerceAtMost(SIDE - 1) * SIDE + column
            KeyEvent.KEYCODE_DPAD_LEFT -> selectedCell = row * SIDE + (column - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (column == SIDE - 1) {
                sideFocus = SideFocus.CLEAR_ALL
            } else {
                selectedCell = row * SIDE + column + 1
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (!current.isGiven(selectedCell)) openValuePicker()
            }
            KeyEvent.KEYCODE_MENU -> {
                if (!current.isGiven(selectedCell) && entries[selectedCell] == 0) openCandidatePicker()
            }
            KeyEvent.KEYCODE_BACK -> showHome()
            else -> return false
        }
        return true
    }

    private fun handleGameTap(x: Float, y: Float): Boolean {
        val current = puzzle ?: return false
        if (pickerOpen) return handlePickerTap(x, y)

        if (clearAllRect.contains(x, y)) {
            clearAllEntries()
            return true
        }
        if (nextPuzzleRect.contains(x, y)) {
            nextPuzzle()
            return true
        }
        val cellIndex = boardCellAt(x, y) ?: return false
        selectedCell = cellIndex
        sideFocus = SideFocus.NONE
        if (!current.isGiven(cellIndex)) openValuePicker()
        return true
    }

    private fun enterValue(value: Int) {
        val current = puzzle ?: return
        if (current.isGiven(selectedCell)) return
        entries[selectedCell] = value
        candidateMasks[selectedCell] = 0
        incorrectCells = emptySet()
        statusMessage = null
        if (entries.all { it != 0 }) {
            if (current.isValidCompletion(entries)) {
                finishGame(current)
            } else {
                incorrectCells = SudokuRules.conflictingCells(entries)
                    .filterTo(mutableSetOf()) { !current.isGiven(it) }
                statusMessage = "还有数字不正确，请继续检查"
            }
        }
    }

    private fun finishGame(current: Puzzle) {
        elapsedSeconds = currentElapsedMillis() / 1000L
        startedAt = -1L
        progress.clear(current.difficulty)
        newRecord = scores.record(current.difficulty, elapsedSeconds)
        rewardFocus = 1
        page = Page.REWARD
    }

    // ---------------------------------------------------------------- picker

    private fun openValuePicker() {
        pickerMode = PickerMode.VALUE
        pickerClearFocused = false
        pickerSelection = 4
        pickerDraftMask = 0
        pickerLimitReached = false
        pickerOpen = true
    }

    private fun openCandidatePicker() {
        pickerMode = PickerMode.CANDIDATES
        pickerClearFocused = false
        pickerDraftMask = candidateMasks[selectedCell]
        pickerSelection = (0 until SIDE).firstOrNull { pickerDraftMask and (1 shl it) != 0 } ?: 4
        pickerLimitReached = false
        pickerOpen = true
    }

    private fun togglePickerCandidate() {
        val bit = 1 shl pickerSelection
        if (pickerDraftMask and bit != 0) {
            pickerDraftMask = pickerDraftMask and bit.inv()
            pickerLimitReached = false
        } else if (Integer.bitCount(pickerDraftMask) < 4) {
            pickerDraftMask = pickerDraftMask or bit
            pickerLimitReached = false
        } else {
            pickerLimitReached = true
        }
    }

    private fun handlePickerKey(keyCode: Int): Boolean {
        val row = pickerSelection / 3
        val column = pickerSelection % 3
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (!pickerClearFocused && column > 0) {
                pickerSelection--
                pickerLimitReached = false
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (!pickerClearFocused && column < 2) {
                pickerSelection++
                pickerLimitReached = false
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (pickerClearFocused) {
                    pickerClearFocused = false
                } else if (row > 0) {
                    pickerSelection -= 3
                    pickerLimitReached = false
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (!pickerClearFocused) {
                if (row < 2) {
                    pickerSelection += 3
                    pickerLimitReached = false
                } else if (pickerMode == PickerMode.VALUE) {
                    pickerClearFocused = true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                pickerOpen = false
                if (pickerMode == PickerMode.VALUE) {
                    enterValue(if (pickerClearFocused) 0 else pickerSelection + 1)
                } else {
                    candidateMasks[selectedCell] = pickerDraftMask
                }
            }
            KeyEvent.KEYCODE_MENU -> if (pickerMode == PickerMode.CANDIDATES) togglePickerCandidate()
            KeyEvent.KEYCODE_BACK -> {
                pickerOpen = false
                pickerLimitReached = false
            }
            else -> return false
        }
        return true
    }

    private fun handlePickerTap(x: Float, y: Float): Boolean {
        val layout = pickerLayout()
        repeat(9) { index ->
            val column = index % 3
            val row = index / 3
            val left = layout.gridLeft + column * (layout.key + layout.gap)
            val top = layout.gridTop + row * (layout.key + layout.gap)
            if (contains(x, y, left, top, left + layout.key, top + layout.key)) {
                pickerSelection = index
                pickerLimitReached = false
                if (pickerMode == PickerMode.VALUE) {
                    pickerOpen = false
                    enterValue(index + 1)
                } else {
                    togglePickerCandidate()
                }
                return true
            }
        }
        if (pickerMode == PickerMode.CANDIDATES) {
            val buttonTop = layout.bottom - 92f
            val middle = (layout.left + layout.right) / 2f
            when {
                contains(x, y, layout.left + 34f, buttonTop, middle - 8f, layout.bottom - 24f) -> {
                    pickerOpen = false
                    pickerLimitReached = false
                    return true
                }
                contains(x, y, middle + 8f, buttonTop, layout.right - 34f, layout.bottom - 24f) -> {
                    candidateMasks[selectedCell] = pickerDraftMask
                    pickerOpen = false
                    pickerLimitReached = false
                    return true
                }
                else -> Unit
            }
        } else {
            val clearButton = layout.clearButton
            if (clearButton.contains(x, y)) {
                pickerOpen = false
                enterValue(0)
                return true
            }
        }
        if (!contains(x, y, layout.left, layout.top, layout.right, layout.bottom)) {
            pickerOpen = false
            pickerLimitReached = false
        }
        return true
    }

    private fun pickerLayout(): PickerLayout {
        val columns = 3
        val rows = 3
        val panelWidth = 620f
        val key = 128f
        val gap = 16f
        val footerHeight = 170f
        val panelHeight = 125f + rows * (key + gap) + footerHeight
        val right = 1848f
        val left = right - panelWidth
        val top = (1080f - panelHeight) / 2f
        val gridWidth = columns * key + (columns - 1) * gap
        return PickerLayout(
            key = key,
            gap = gap,
            left = left,
            top = top,
            right = right,
            bottom = top + panelHeight,
            gridLeft = (left + right - gridWidth) / 2f,
            gridTop = top + 105f,
        )
    }

    private fun boardCellAt(x: Float, y: Float): Int? {
        val left = 96f
        val top = 48f
        val boardPixels = 984f
        if (!contains(x, y, left, top, left + boardPixels, top + boardPixels)) return null
        val cell = boardPixels / SIDE
        val column = ((x - left) / cell).toInt().coerceIn(0, SIDE - 1)
        val row = ((y - top) / cell).toInt().coerceIn(0, SIDE - 1)
        return row * SIDE + column
    }

    // ---------------------------------------------------------------- reward / scores

    private fun handleRewardKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> rewardFocus = (rewardFocus - 1).coerceAtLeast(0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> rewardFocus = (rewardFocus + 1).coerceAtMost(2)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> activateReward()
            KeyEvent.KEYCODE_BACK -> showHome()
            else -> return false
        }
        return true
    }

    private fun activateReward() {
        when (rewardFocus) {
            0 -> {
                scoreDifficulty = puzzle?.difficulty ?: difficulty
                scoreFocus = 4
                page = Page.SCORES
            }
            1 -> nextPuzzle()
            else -> showHome()
        }
    }

    private fun handleRewardTap(x: Float, y: Float): Boolean {
        when {
            contains(x, y, 560f, 680f, 860f, 800f) -> {
                rewardFocus = 0
                activateReward()
            }
            contains(x, y, 900f, 680f, 1200f, 800f) -> {
                rewardFocus = 1
                activateReward()
            }
            contains(x, y, 1240f, 680f, 1540f, 800f) -> {
                rewardFocus = 2
                activateReward()
            }
            else -> return false
        }
        return true
    }

    private fun scoreTabRect(index: Int): RectF =
        RectF(64f + index * 160f, 190f, 64f + index * 160f + 144f, 246f)

    private fun handleScoresKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (scoreFocus in 1 until 4) scoreFocus--
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (scoreFocus in 0 until 3) scoreFocus++
            KeyEvent.KEYCODE_DPAD_UP -> if (scoreFocus == 4) scoreFocus = scoreDifficulty.ordinal
            KeyEvent.KEYCODE_DPAD_DOWN -> if (scoreFocus in 0..3) scoreFocus = 4
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (scoreFocus in 0..3) scoreDifficulty = Difficulty.entries[scoreFocus] else showHome()
            }
            KeyEvent.KEYCODE_BACK -> showHome()
            else -> return false
        }
        return true
    }

    private fun handleScoresTap(x: Float, y: Float): Boolean {
        Difficulty.entries.forEachIndexed { index, item ->
            if (scoreTabRect(index).contains(x, y)) {
                scoreFocus = index
                scoreDifficulty = item
                return true
            }
        }
        if (contains(x, y, 1635f, 65f, 1855f, 130f)) {
            scoreFocus = 4
            showHome()
            return true
        }
        return false
    }

    private fun showHome() {
        pauseGame()
        puzzle = null
        entries = IntArray(0)
        candidateMasks = IntArray(0)
        pickerOpen = false
        sideFocus = SideFocus.NONE
        exitOpen = false
        page = Page.HOME
    }

    // ---------------------------------------------------------------- drawing

    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(ink)
        paint.color = panel
        canvas.drawCircle(110f, 25f, 390f, paint)
        paint.color = Color.argb(15, 86, 214, 201)
        canvas.drawCircle(1760f, 970f, 500f, paint)
    }

    private fun drawHome(canvas: Canvas) {
        text(canvas, "数独", 128f, 330f, 126f, cream, true)
        text(canvas, "题库版", 132f, 430f, 44f, mint)
        text(canvas, "选择难度开始，或输入关卡号", 132f, 500f, 30f, muted)
        paint.color = mint
        canvas.drawCircle(137f, 600f, 8f, paint)
        text(canvas, "遥控器与触摸屏均可操作", 164f, 613f, 27f, cream)
        val latest = progress.latestDifficulty()
        if (latest != null) {
            text(canvas, "未完成：${latest.label} · 可继续", 132f, 660f, 25f, muted)
        }

        rounded(canvas, 726f, 84f, 1792f, 996f, 32f, panel)
        text(canvas, "选择难度", 803f, 200f, 46f, cream, true)
        text(canvas, "评分来自题库", 1715f, 201f, 28f, muted, align = Paint.Align.RIGHT)
        Difficulty.entries.forEachIndexed { index, item ->
            val rect = difficultyCardRect(index)
            val saved = progress.has(item)
            optionCard(
                canvas, rect.left, rect.top, rect.right, rect.bottom,
                item.label,
                if (saved) "继续上局" else item.ratingHint,
                difficulty == item, homeFocus == index, saved,
            )
        }
        text(canvas, "有存档的难度会继续上次的一局，其余随机开新题", 803f, 520f, 27f, muted)

        actionButton(canvas, 802f, 800f, 1076f, 920f, "最好成绩", homeFocus == 4, false)
        actionButton(canvas, 1092f, 800f, 1366f, 920f, "关卡选择", homeFocus == 5, false)
        actionButton(
            canvas, 1382f, 800f, 1716f, 920f,
            if (latest != null) "继续上局" else "继续上局", homeFocus == 6, latest != null,
            enabled = latest != null,
        )
        if (exitOpen) drawExitDialog(canvas)
    }

    private fun drawExitDialog(canvas: Canvas) {
        paint.color = Color.argb(215, 8, 19, 29)
        canvas.drawRect(0f, 0f, 1920f, 1080f, paint)
        rounded(canvas, 390f, 300f, 1530f, 782f, 32f, panel)
        strokeRound(canvas, 390f, 300f, 1530f, 782f, 32f, panelLight, 4f)
        textCenter(canvas, "退出数独？", 960f, 430f, 52f, cream, true)
        textCenter(canvas, "未完成进度已保存，下次可以继续。", 960f, 510f, 30f, muted)
        actionButton(canvas, 485f, 588f, 904f, 710f, "取消", !exitSelected, !exitSelected)
        actionButton(canvas, 936f, 588f, 1435f, 710f, "退出应用", exitSelected, exitSelected)
    }

    private fun drawLevelSelect(canvas: Canvas) {
        text(canvas, "关卡选择", 96f, 110f, 54f, cream, true)
        text(canvas, "题号 = 该难度题库的行号，同一题号在不同难度下是不同题目", 96f, 700f, 25f, muted)

        Difficulty.entries.forEachIndexed { index, item ->
            val rect = levelTabRect(index)
            rounded(canvas, rect.left, rect.top, rect.right, rect.bottom, 16f,
                if (levelDifficulty == item) Color.rgb(31, 74, 84) else ink)
            if (levelFocus == index) strokeRound(canvas, rect.left, rect.top, rect.right, rect.bottom, 16f, cream, 5f)
            textCenter(canvas, item.label, (rect.left + rect.right) / 2f, (rect.top + rect.bottom) / 2f + 12f,
                30f, if (levelDifficulty == item) mint else cream, true)
        }

        rounded(canvas, 96f, 250f, 900f, 430f, 28f, panel)
        strokeRound(canvas, 96f, 250f, 900f, 430f, 28f, panelLight, 3f)
        text(canvas, "题号", 132f, 320f, 30f, muted)
        val count = bank.count(levelDifficulty)
        val input = levelInput.toString()
        text(canvas, if (input.isEmpty()) "—" else input, 132f, 400f, 92f,
            if (input.isEmpty()) muted else cream, true)
        textCenter(canvas, "可输入 1–$count", 690f, 400f, 26f, muted)
        levelMessage?.let { text(canvas, it, 96f, 500f, 34f, coral, true) }

        repeat(3) { row ->
            repeat(3) { column ->
                val index = row * 3 + column
                drawLevelKey(canvas, index, (index + 1).toString())
            }
        }
        drawLevelKey(canvas, 9, "←")
        drawLevelKey(canvas, 10, "0")
        drawLevelKey(canvas, 11, "确定")
        actionButton(canvas, levelButtonRect(0).left, levelButtonRect(0).top, levelButtonRect(0).right,
            levelButtonRect(0).bottom, "返回", levelFocus == 16, false)
        actionButton(canvas, levelButtonRect(1).left, levelButtonRect(1).top, levelButtonRect(1).right,
            levelButtonRect(1).bottom, "随机一题", levelFocus == 17, false)
    }

    private fun drawLevelKey(canvas: Canvas, index: Int, label: String) {
        val rect = levelKeyRect(index)
        val focused = levelFocus == 4 + index
        rounded(canvas, rect.left, rect.top, rect.right, rect.bottom, 20f, if (focused) mint else ink)
        if (focused) {
            strokeRound(canvas, rect.left, rect.top, rect.right, rect.bottom, 20f, cream, 5f)
        } else {
            strokeRound(canvas, rect.left, rect.top, rect.right, rect.bottom, 20f, panelLight, 2f)
        }
        textCenter(canvas, label, (rect.left + rect.right) / 2f, (rect.top + rect.bottom) / 2f + 14f,
            if (index == 11) 34f else 56f, if (focused) ink else cream, true)
    }

    private fun drawGame(canvas: Canvas) {
        val current = puzzle ?: return
        val left = 96f
        val top = 48f
        val boardPixels = 984f
        val cell = boardPixels / SIDE
        paint.style = Paint.Style.FILL
        paint.color = cream
        canvas.drawRect(left, top, left + boardPixels, top + boardPixels, paint)

        val selectedRow = selectedCell / SIDE
        val selectedColumn = selectedCell % SIDE
        entries.indices.forEach { index ->
            val row = index / SIDE
            val column = index % SIDE
            val sameGroup = row == selectedRow || column == selectedColumn ||
                (row / BLOCK_ROWS == selectedRow / BLOCK_ROWS && column / BLOCK_COLUMNS == selectedColumn / BLOCK_COLUMNS)
            paint.color = when {
                index == selectedCell -> mint
                sameGroup -> Color.rgb(225, 234, 231)
                current.isGiven(index) -> Color.rgb(241, 238, 231)
                else -> Color.WHITE
            }
            canvas.drawRect(left + column * cell, top + row * cell, left + (column + 1) * cell, top + (row + 1) * cell, paint)
        }
        for (line in 0..SIDE) {
            paint.color = if (line % BLOCK_COLUMNS == 0) panel else Color.rgb(154, 173, 181)
            paint.strokeWidth = if (line % BLOCK_COLUMNS == 0) 5f else 1.5f
            canvas.drawLine(left + line * cell, top, left + line * cell, top + boardPixels, paint)
            paint.color = if (line % BLOCK_ROWS == 0) panel else Color.rgb(154, 173, 181)
            paint.strokeWidth = if (line % BLOCK_ROWS == 0) 5f else 1.5f
            canvas.drawLine(left, top + line * cell, left + boardPixels, top + line * cell, paint)
        }
        entries.forEachIndexed { index, value ->
            if (value != 0) {
                val color = when {
                    index in incorrectCells -> coral
                    index == selectedCell -> ink
                    current.isGiven(index) -> Color.rgb(20, 38, 50)
                    else -> Color.rgb(19, 121, 119)
                }
                val row = index / SIDE
                val column = index % SIDE
                textCenter(canvas, value.toString(), left + column * cell + cell / 2f,
                    top + row * cell + cell * .72f, cell * .66f, color, true)
            }
        }
        candidateMasks.forEachIndexed { index, mask ->
            if (entries[index] == 0 && mask != 0) {
                val row = index / SIDE
                val column = index % SIDE
                val cellLeft = left + column * cell
                val cellTop = top + row * cell
                var slot = 0
                repeat(SIDE) { numberIndex ->
                    if (mask and (1 shl numberIndex) != 0 && slot < 4) {
                        val xFactor = if (slot % 2 == 0) .23f else .77f
                        val yFactor = if (slot < 2) .32f else .86f
                        textCenter(
                            canvas, (numberIndex + 1).toString(),
                            cellLeft + cell * xFactor, cellTop + cell * yFactor,
                            min(cell * .28f, 34f),
                            if (index == selectedCell) ink else Color.rgb(19, 121, 119), true,
                        )
                        slot++
                    }
                }
            }
        }
        strokeRect(canvas, left + selectedColumn * cell + 3f, top + selectedRow * cell + 3f,
            left + (selectedColumn + 1) * cell - 3f, top + (selectedRow + 1) * cell - 3f, mint, 7f)

        text(canvas, "${current.difficulty.label} · 第 ${current.id} 题", 1165f, 135f, 40f, mint, true)
        text(canvas, "评分 ${current.rating}", 1165f, 190f, 28f, muted)
        text(canvas, formatTime(elapsedSeconds), 1165f, 290f, 74f, cream)
        text(canvas, "本局用时", 1165f, 345f, 26f, muted)

        actionButton(canvas, 1165f, 400f, 1480f, 520f, "清除填写",
            sideFocus == SideFocus.CLEAR_ALL, false)
        actionButton(canvas, 1495f, 400f, 1824f, 520f, "下一题",
            sideFocus == SideFocus.NEXT, false)
        text(canvas, "遥控器：最右列按右进入按钮", 1165f, 580f, 23f, muted)

        val filled = entries.count { it != 0 }
        text(canvas, "完成度  $filled / ${entries.size}", 1165f, 640f, 27f, cream)
        rounded(canvas, 1165f, 660f, 1824f, 676f, 8f, panelLight)
        rounded(canvas, 1165f, 660f, 1165f + 659f * filled / entries.size, 676f, 8f, mint)

        text(canvas, "遥控器操作", 1165f, 740f, 32f, cream, true)
        text(canvas, "方向键移动 · 确定键填数", 1165f, 782f, 25f, muted)
        text(canvas, "菜单键为空格添加预选数字", 1165f, 822f, 25f, muted)
        statusMessage?.let { text(canvas, it, 1165f, 890f, 27f, coral, true) }
        text(canvas, "返回首页自动保存进度", 1165f, 970f, 25f, muted)

        if (pickerOpen) drawPicker(canvas)
    }

    private fun drawPicker(canvas: Canvas) {
        paint.color = Color.argb(145, 8, 19, 29)
        canvas.drawRect(0f, 0f, 1920f, 1080f, paint)
        val layout = pickerLayout()
        rounded(canvas, layout.left, layout.top, layout.right, layout.bottom, 28f, panel)
        strokeRound(canvas, layout.left, layout.top, layout.right, layout.bottom, 28f, panelLight, 3f)
        textCenter(canvas, if (pickerMode == PickerMode.VALUE) "选择数字" else "预选数字",
            (layout.left + layout.right) / 2f, layout.top + 76f, 38f, cream, true)
        repeat(9) { index ->
            val column = index % 3
            val row = index / 3
            val x = layout.gridLeft + column * (layout.key + layout.gap)
            val y = layout.gridTop + row * (layout.key + layout.gap)
            val focused = !pickerClearFocused && index == pickerSelection
            val chosen = pickerMode == PickerMode.CANDIDATES && pickerDraftMask and (1 shl index) != 0
            rounded(canvas, x, y, x + layout.key, y + layout.key, 18f, when {
                focused -> mint
                chosen -> Color.rgb(31, 74, 84)
                else -> ink
            })
            strokeRound(canvas, x, y, x + layout.key, y + layout.key, 18f, when {
                focused -> cream
                chosen -> gold
                else -> panelLight
            }, if (focused) 5f else if (chosen) 4f else 2f)
            textCenter(canvas, "${index + 1}", x + layout.key / 2f, y + layout.key * .7f, 58f, when {
                focused -> ink
                chosen -> gold
                else -> cream
            }, true)
            if (chosen) {
                paint.color = gold
                canvas.drawCircle(x + layout.key - 18f, y + 18f, 8f, paint)
            }
        }
        if (pickerMode == PickerMode.CANDIDATES) {
            val buttonTop = layout.bottom - 92f
            val middle = (layout.left + layout.right) / 2f
            actionButton(canvas, layout.left + 34f, buttonTop, middle - 8f, layout.bottom - 24f, "取消", false, false)
            actionButton(canvas, middle + 8f, buttonTop, layout.right - 34f, layout.bottom - 24f, "保存预选", false, true)
            val footer = if (pickerLimitReached) "每格最多预选 4 个数字" else "点按数字切换 · 遥控器菜单键切换"
            textCenter(canvas, footer, (layout.left + layout.right) / 2f, layout.bottom - 112f, 22f,
                if (pickerLimitReached) coral else muted)
        } else {
            val clearButton = layout.clearButton
            actionButton(canvas, clearButton.left, clearButton.top, clearButton.right, clearButton.bottom,
                "清除", pickerClearFocused, false)
            textCenter(canvas, "点按或确定键选择 · 向下可选清除", (layout.left + layout.right) / 2f,
                layout.bottom - 112f, 22f, muted)
        }
    }

    private fun drawReward(canvas: Canvas) {
        val current = puzzle
        textCenter(canvas, "完成！", 960f, 315f, 108f, mint, true)
        val subtitle = current?.let { "${it.difficulty.label} · 第 ${it.id} 题 · 评分 ${it.rating}" }
            ?: "本局完成"
        textCenter(canvas, subtitle, 960f, 385f, 30f, muted)
        textCenter(canvas, formatTime(elapsedSeconds), 960f, 525f, 86f, cream)
        if (newRecord) textCenter(canvas, "新的最好成绩", 960f, 590f, 31f, gold, true)
        actionButton(canvas, 560f, 680f, 860f, 800f, "查看成绩", rewardFocus == 0, false)
        actionButton(canvas, 900f, 680f, 1200f, 800f, "下一题", rewardFocus == 1, true)
        actionButton(canvas, 1240f, 680f, 1540f, 800f, "返回首页", rewardFocus == 2, false)
    }

    private fun drawScores(canvas: Canvas) {
        text(canvas, "最好成绩", 64f, 110f, 54f, cream, true)
        text(canvas, "每个难度保留最快的 10 次", 64f, 150f, 25f, muted)
        Difficulty.entries.forEachIndexed { index, item ->
            val rect = scoreTabRect(index)
            actionButton(canvas, rect.left, rect.top, rect.right, rect.bottom, item.label,
                scoreFocus == index, scoreDifficulty == item)
        }
        actionButton(canvas, 1635f, 65f, 1855f, 130f, "返回", scoreFocus == 4, true)

        val values = scores.scores(scoreDifficulty)
        if (values.isEmpty()) {
            textCenter(canvas, "还没有通关记录", 960f, 540f, 42f, cream, true)
            textCenter(canvas, "完成一局后，成绩会出现在这里", 960f, 595f, 28f, muted)
        } else {
            values.forEachIndexed { index, seconds ->
                val column = index / 5
                val row = index % 5
                val x = 150f + column * 850f
                val y = 300f + row * 120f
                rounded(canvas, x, y, x + 760f, y + 92f, 16f, panel)
                text(canvas, "%02d".format(index + 1), x + 35f, y + 60f, 28f,
                    if (index < 3) gold else muted, true)
                text(canvas, formatTime(seconds), x + 140f, y + 62f, 36f, cream, true)
            }
        }
    }

    // ---------------------------------------------------------------- primitives

    private val clearAllRect: RectF get() = RectF(1165f, 400f, 1480f, 520f)
    private val nextPuzzleRect: RectF get() = RectF(1495f, 400f, 1824f, 520f)

    private fun optionCard(
        canvas: Canvas, l: Float, t: Float, r: Float, b: Float,
        title: String, subtitle: String, selected: Boolean, focused: Boolean, saved: Boolean,
    ) {
        rounded(canvas, l, t, r, b, 24f, if (selected) Color.rgb(31, 74, 84) else ink)
        if (selected || focused) strokeRound(canvas, l, t, r, b, 24f, if (focused) cream else mint, if (focused) 5f else 4f)
        textCenter(canvas, title, (l + r) / 2f, t + 89f, 42f, if (selected) mint else cream, true)
        textCenter(canvas, subtitle, (l + r) / 2f, t + 145f, 24f, if (saved) gold else muted)
    }

    private fun actionButton(
        canvas: Canvas, l: Float, t: Float, r: Float, b: Float,
        label: String, focused: Boolean, primary: Boolean, enabled: Boolean = true,
    ) {
        val background = when {
            !enabled -> panel
            primary || focused -> mint
            else -> panelLight
        }
        rounded(canvas, l, t, r, b, 18f, background)
        if (focused) strokeRound(canvas, l, t, r, b, 18f, cream, 7f)
        val textColor = when {
            !enabled -> muted
            primary || focused -> ink
            else -> cream
        }
        textCenter(canvas, label, (l + r) / 2f, (t + b) / 2f + 14f, 36f, textColor, true)
    }

    private fun text(
        canvas: Canvas, value: String, x: Float, baseline: Float, size: Float, color: Int,
        bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        canvas.drawText(value, x, baseline, paint)
    }

    private fun textCenter(canvas: Canvas, value: String, x: Float, baseline: Float, size: Float, color: Int, bold: Boolean = false) =
        text(canvas, value, x, baseline, size, color, bold, Paint.Align.CENTER)

    private fun rounded(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, paint)
    }

    private fun strokeRound(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int, width: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = color
        canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    private fun strokeRect(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, width: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = color
        canvas.drawRect(l, t, r, b, paint)
        paint.style = Paint.Style.FILL
    }

    private fun contains(x: Float, y: Float, l: Float, t: Float, r: Float, b: Float): Boolean =
        x >= l && x < r && y >= t && y < b

    private fun toDesignPoint(x: Float, y: Float): FloatArray {
        val scale = min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT)
        if (scale <= 0f) return floatArrayOf(-1f, -1f)
        return floatArrayOf(
            (x - (width - DESIGN_WIDTH * scale) / 2f) / scale,
            (y - (height - DESIGN_HEIGHT * scale) / 2f) / scale,
        )
    }

    private fun formatTime(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = totalSeconds % 3600 / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
    }

    private companion object {
        const val DESIGN_WIDTH = 1920f
        const val DESIGN_HEIGHT = 1080f
        const val LEVEL_KEY_LEFT = 1050f
        const val LEVEL_KEY_TOP = 230f
        const val LEVEL_KEY = 170f
        const val LEVEL_KEY_GAP = 20f
    }
}
