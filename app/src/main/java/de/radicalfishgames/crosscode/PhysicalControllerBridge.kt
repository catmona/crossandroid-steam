package de.radicalfishgames.crosscode

import android.view.KeyEvent
import android.view.MotionEvent

class PhysicalControllerBridge(
    private val gamepadState: GamepadJsonBridge,
) {
    private companion object {
        const val DEAD_ZONE = 0.1
    }

    // Triggers and D-pad can arrive both as key events and as axes (e.g. digital trigger mode sends
    // BUTTON_R2 while the axis stays 0). Track each source separately so one doesn't release the other.
    private val keyHeld = HashSet<GamepadJsonBridge.Button>()
    private val axisHeld = HashSet<GamepadJsonBridge.Button>()

    fun onKeyEvent(event: KeyEvent): Boolean {
        val pressed = event.action == KeyEvent.ACTION_DOWN
        val button = mapButton(event.keyCode) ?: return false
        setHeld(button, pressed, keyHeld)
        return true
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        setStick(gamepadState.leftStick, event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y))
        setStick(gamepadState.rightStick, event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RZ))

        // Some layouts report triggers as BRAKE/GAS instead of LTRIGGER/RTRIGGER
        val lt = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rt = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        setHeld(gamepadState.leftTrigger, lt > DEAD_ZONE, axisHeld)
        setHeld(gamepadState.rightTrigger, rt > DEAD_ZONE, axisHeld)

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        setHeld(gamepadState.leftCross.left, hatX < -0.5f, axisHeld)
        setHeld(gamepadState.leftCross.right, hatX > 0.5f, axisHeld)
        setHeld(gamepadState.leftCross.top, hatY < -0.5f, axisHeld)
        setHeld(gamepadState.leftCross.bottom, hatY > 0.5f, axisHeld)

        return true
    }

    private fun setHeld(
        button: GamepadJsonBridge.Button,
        held: Boolean,
        source: MutableSet<GamepadJsonBridge.Button>,
    ) {
        if (held) source.add(button) else source.remove(button)
        button.pressed = button in keyHeld || button in axisHeld
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

    // Sticks go to the game raw, as a desktop browser's Gamepad API reports them: the game applies
    // its own dead zones, and stick-shaping mods need the unclipped values.
    private fun setStick(
        stick: GamepadJsonBridge.Stick,
        rawX: Float,
        rawY: Float,
    ) {
        stick.horizontal.state = rawX.toDouble()
        stick.vertical.state = rawY.toDouble()
    }
}
