package de.radicalfishgames.crosscode

import android.view.KeyEvent
import android.view.MotionEvent

class PhysicalControllerBridge(
    private val gamepadState: GamepadJsonBridge,
) {
    private companion object {
        const val DEAD_ZONE = 0.1
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val pressed = event.action == KeyEvent.ACTION_DOWN
        val button = mapButton(event.keyCode) ?: return false
        button.pressed = pressed
        return true
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        gamepadState.leftStick.horizontal.state = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_X))
        gamepadState.leftStick.vertical.state = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_Y))
        gamepadState.rightStick.horizontal.state = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_Z))
        gamepadState.rightStick.vertical.state = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_RZ))

        val lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER).toDouble()
        val rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER).toDouble()
        gamepadState.leftTrigger.pressed = lt > DEAD_ZONE
        gamepadState.rightTrigger.pressed = rt > DEAD_ZONE

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        gamepadState.leftCross.left.pressed = hatX < -0.5f
        gamepadState.leftCross.right.pressed = hatX > 0.5f
        gamepadState.leftCross.top.pressed = hatY < -0.5f
        gamepadState.leftCross.bottom.pressed = hatY > 0.5f

        return true
    }

    private fun mapButton(keyCode: Int): GamepadJsonBridge.Button? =
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> gamepadState.rightCross.bottom
            KeyEvent.KEYCODE_BUTTON_B -> gamepadState.rightCross.right
            KeyEvent.KEYCODE_BUTTON_X -> gamepadState.rightCross.left
            KeyEvent.KEYCODE_BUTTON_Y -> gamepadState.rightCross.top
            KeyEvent.KEYCODE_BUTTON_L1 -> gamepadState.leftBumper
            KeyEvent.KEYCODE_BUTTON_R1 -> gamepadState.rightBumper
            KeyEvent.KEYCODE_BUTTON_L2 -> gamepadState.leftTrigger
            KeyEvent.KEYCODE_BUTTON_R2 -> gamepadState.rightTrigger
            KeyEvent.KEYCODE_BUTTON_START -> gamepadState.startOrForward
            KeyEvent.KEYCODE_BUTTON_SELECT -> gamepadState.selectOrBack
            KeyEvent.KEYCODE_BUTTON_THUMBL -> gamepadState.leftStick.button
            KeyEvent.KEYCODE_BUTTON_THUMBR -> gamepadState.rightStick.button
            KeyEvent.KEYCODE_BUTTON_MODE -> gamepadState.center
            KeyEvent.KEYCODE_DPAD_UP -> gamepadState.leftCross.top
            KeyEvent.KEYCODE_DPAD_DOWN -> gamepadState.leftCross.bottom
            KeyEvent.KEYCODE_DPAD_LEFT -> gamepadState.leftCross.left
            KeyEvent.KEYCODE_DPAD_RIGHT -> gamepadState.leftCross.right
            else -> null
        }

    private fun applyDeadZone(value: Float): Double = if (kotlin.math.abs(value) < DEAD_ZONE) 0.0 else value.toDouble()
}
