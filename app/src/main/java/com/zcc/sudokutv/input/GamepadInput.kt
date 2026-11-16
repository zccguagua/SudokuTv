package com.zcc.sudokutv.input

import android.view.InputDevice
import android.view.KeyEvent
import kotlin.math.abs

/**
 * Gamepad support for the TV app.
 *
 * The rest of the app already understands the remote's key codes, so this class only translates:
 *  - A / B            -> confirm (same as the remote's OK key)
 *  - X / Y            -> menu (candidate marks, same as the remote's MENU key)
 *  - D-pad / left stick -> direction keys
 *
 * Two duplicate guards are needed on real hardware:
 *  - ignore ACTION_DOWN repeats (`repeatCount > 0`) and keys that are still held;
 *  - collapse two different key codes that arrive from one physical press
 *    (some pads send BUTTON_A together with DPAD_CENTER) within [DUPLICATE_WINDOW_MS].
 */
class GamepadInput(
    private val dispatchKey: (Int) -> Unit,
    private val uptimeMillis: () -> Long,
) {
    private val pressedButtons = HashSet<Int>()
    private var directionMask = 0
    private var lastAction = ACTION_NONE
    private var lastActionAt = 0L

    /** Consumes gamepad buttons that map onto an existing key; returns false for everything else. */
    fun handleKey(keyCode: Int, action: Int, source: Int, repeatCount: Int): Boolean {
        val mapped = actionFor(keyCode, source)
        if (mapped == ACTION_NONE) return false

        when (action) {
            KeyEvent.ACTION_DOWN -> {
                if (repeatCount > 0) return true
                if (!pressedButtons.add(keyCode)) return true
                if (!isDuplicate(mapped)) {
                    dispatchKey(if (mapped == ACTION_CONFIRM) KeyEvent.KEYCODE_ENTER else KeyEvent.KEYCODE_MENU)
                }
            }
            KeyEvent.ACTION_UP -> pressedButtons.remove(keyCode)
        }
        return true
    }

    /** Feeds the D-pad hat and the left stick; sends one direction key per press edge. */
    fun updateAxes(hatX: Float, hatY: Float, stickX: Float, stickY: Float) {
        val useHat = hatX != 0f || hatY != 0f
        val x = if (useHat) hatX else if (abs(stickX) >= STICK_DEAD_ZONE) stickX else 0f
        val y = if (useHat) hatY else if (abs(stickY) >= STICK_DEAD_ZONE) stickY else 0f
        updateDirection(MASK_UP, y <= -DIRECTION_THRESHOLD, KeyEvent.KEYCODE_DPAD_UP)
        updateDirection(MASK_DOWN, y >= DIRECTION_THRESHOLD, KeyEvent.KEYCODE_DPAD_DOWN)
        updateDirection(MASK_LEFT, x <= -DIRECTION_THRESHOLD, KeyEvent.KEYCODE_DPAD_LEFT)
        updateDirection(MASK_RIGHT, x >= DIRECTION_THRESHOLD, KeyEvent.KEYCODE_DPAD_RIGHT)
    }

    /** Clears held state, e.g. when the activity pauses while a button is still down. */
    fun reset() {
        pressedButtons.clear()
        directionMask = 0
        lastAction = ACTION_NONE
    }

    private fun updateDirection(mask: Int, pressed: Boolean, keyCode: Int) {
        val was = directionMask and mask != 0
        if (pressed && !was) {
            directionMask = directionMask or mask
            dispatchKey(keyCode)
        } else if (!pressed && was) {
            directionMask = directionMask and mask.inv()
        }
    }

    private fun isDuplicate(action: Int): Boolean {
        val now = uptimeMillis()
        val duplicate = action == lastAction && now - lastActionAt < DUPLICATE_WINDOW_MS
        lastAction = action
        lastActionAt = now
        return duplicate
    }

    private fun actionFor(keyCode: Int, source: Int): Int {
        val isPad = isGamepadSource(source)
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B -> ACTION_CONFIRM
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y -> ACTION_MENU
            // Some pads report A as DPAD_CENTER or ENTER; treat those as confirm too.
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
                if (isPad) ACTION_CONFIRM else ACTION_NONE
            KeyEvent.KEYCODE_MENU -> if (isPad) ACTION_MENU else ACTION_NONE
            else -> ACTION_NONE
        }
    }

    private companion object {
        const val ACTION_NONE = 0
        const val ACTION_CONFIRM = 1
        const val ACTION_MENU = 2
        const val DUPLICATE_WINDOW_MS = 80L
        const val STICK_DEAD_ZONE = 0.5f
        const val DIRECTION_THRESHOLD = 0.5f
        const val MASK_UP = 1
        const val MASK_DOWN = 2
        const val MASK_LEFT = 4
        const val MASK_RIGHT = 8

        /**
         * Source constants share their class bits, so a plain `source and SOURCE_GAMEPAD != 0`
         * also matches a remote (SOURCE_DPAD). Compare the masked value instead.
         */
        fun isGamepadSource(source: Int): Boolean =
            (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
    }
}
