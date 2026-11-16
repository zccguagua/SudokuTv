package com.zcc.sudokutv

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.window.OnBackInvokedDispatcher
import com.zcc.sudokutv.input.GamepadInput

class MainActivity : Activity() {
    private lateinit var gameView: SudokuGameView
    private lateinit var gamepad: GamepadInput

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameView = SudokuGameView(this) { finishAffinity() }
        gamepad = GamepadInput(
            dispatchKey = { keyCode -> gameView.handleKey(keyCode) },
            uptimeMillis = SystemClock::uptimeMillis,
        )
        setContentView(gameView)
        gameView.requestFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                if (!gameView.handleKey(KeyEvent.KEYCODE_BACK)) finish()
            }
        }
    }

    override fun onPause() {
        gamepad.reset()
        gameView.pauseGame()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        gameView.resumeGame()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (gamepad.handleKey(event.keyCode, event.action, event.source, event.repeatCount)) return true
        if (event.action == KeyEvent.ACTION_DOWN && gameView.handleKey(event.keyCode)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val source = event.source
        val isGamepad = (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (isGamepad) {
            gamepad.updateAxes(
                hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X),
                hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                stickX = event.getAxisValue(MotionEvent.AXIS_X),
                stickY = event.getAxisValue(MotionEvent.AXIS_Y),
            )
        }
        return super.dispatchGenericMotionEvent(event)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (!gameView.handleKey(KeyEvent.KEYCODE_BACK)) super.onBackPressed()
    }
}
