package com.zcc.sudokutv.input

import android.view.InputDevice
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadInputTest {
    private val dispatched = mutableListOf<Int>()
    private var clock = 1_000L
    private val input = GamepadInput({ dispatched += it }, { clock })

    private fun down(
        keyCode: Int,
        source: Int = InputDevice.SOURCE_GAMEPAD,
        repeat: Int = 0,
    ) = input.handleKey(keyCode, KeyEvent.ACTION_DOWN, source, repeat)

    private fun up(keyCode: Int, source: Int = InputDevice.SOURCE_GAMEPAD) =
        input.handleKey(keyCode, KeyEvent.ACTION_UP, source, 0)

    @Test
    fun faceButtonsMapToConfirmAndMenu() {
        down(KeyEvent.KEYCODE_BUTTON_A)
        up(KeyEvent.KEYCODE_BUTTON_A)
        clock += 200
        down(KeyEvent.KEYCODE_BUTTON_B)
        up(KeyEvent.KEYCODE_BUTTON_B)
        clock += 200
        down(KeyEvent.KEYCODE_BUTTON_X)
        up(KeyEvent.KEYCODE_BUTTON_X)
        clock += 200
        down(KeyEvent.KEYCODE_BUTTON_Y)
        up(KeyEvent.KEYCODE_BUTTON_Y)

        assertEquals(
            listOf(
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_MENU,
            ),
            dispatched,
        )
    }

    @Test
    fun repeatedKeyDownIsIgnored() {
        down(KeyEvent.KEYCODE_BUTTON_A)
        down(KeyEvent.KEYCODE_BUTTON_A, repeat = 1)
        down(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(listOf(KeyEvent.KEYCODE_ENTER), dispatched)
    }

    @Test
    fun secondKeyCodeFromOnePressIsCollapsed() {
        down(KeyEvent.KEYCODE_BUTTON_A)
        clock += 5
        down(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(KeyEvent.KEYCODE_ENTER), dispatched)
    }

    @Test
    fun separatePressesAfterTheWindowBothFire() {
        down(KeyEvent.KEYCODE_BUTTON_A)
        up(KeyEvent.KEYCODE_BUTTON_A)
        clock += 200
        down(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_ENTER), dispatched)
    }

    @Test
    fun remoteKeysAreNotIntercepted() {
        assertFalse(down(KeyEvent.KEYCODE_DPAD_CENTER, InputDevice.SOURCE_DPAD))
        assertFalse(down(KeyEvent.KEYCODE_MENU, InputDevice.SOURCE_DPAD))
        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun dpadButtonsFallThroughToTheNormalPath() {
        assertFalse(down(KeyEvent.KEYCODE_DPAD_UP))
        assertTrue(dispatched.isEmpty())
    }

    @Test
    fun hatSendsOneDirectionPerEdge() {
        input.updateAxes(0f, -1f, 0f, 0f)
        input.updateAxes(0f, -1f, 0f, 0f)
        input.updateAxes(-1f, 0f, 0f, 0f)
        input.updateAxes(0f, 0f, 0f, 0f)
        assertEquals(
            listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT),
            dispatched,
        )
    }

    @Test
    fun stickUsesDeadZone() {
        input.updateAxes(0f, 0f, 0.4f, 0f)
        input.updateAxes(0f, 0f, 0.8f, 0f)
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_RIGHT), dispatched)
    }

    @Test
    fun hatWinsOverStick() {
        input.updateAxes(1f, 0f, -1f, 0f)
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_RIGHT), dispatched)
    }

    @Test
    fun resetClearsHeldButtons() {
        down(KeyEvent.KEYCODE_BUTTON_A)
        input.reset()
        clock += 10
        down(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_ENTER), dispatched)
    }
}
