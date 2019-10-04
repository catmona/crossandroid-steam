package de.radicalfishgames.crosscode.overlay

import android.content.Context
import android.util.AttributeSet
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.View
import kotlin.math.abs


class NativeInputListener(context: Context, attrs: AttributeSet) : View(context, attrs) {

    var onButtonEvent: MutableSet<(fromDeviceId: Int, keycode: Int, isPressed: Boolean) -> Unit> = HashSet()
    var onMotion: MutableSet<(fromDeviceId: Int, axisCode: Int, axisState: Float) -> Unit> = HashSet()

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        onButtonEvent(event.deviceId, keyCode, true)

        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        onButtonEvent(event.deviceId, keyCode, false)

        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if(event.action != ACTION_MOVE){
            return false
        }

        // The axis relevant for a gamepad according to https://developer.android.com/training/game-controllers/controller-input.html#button
        val axisToCheck = intArrayOf(AXIS_HAT_Y, AXIS_HAT_X, AXIS_X, AXIS_Y, AXIS_Z, AXIS_RZ, AXIS_RTRIGGER, AXIS_LTRIGGER)
        sendMotionEventToListeners(event, *axisToCheck)

        return true
    }

    private fun sendMotionEventToListeners(event: MotionEvent, vararg forAxis: Int){
        for(axis in forAxis){
            val value = getFlattenedValue(event, axis)
            onMotion(event.deviceId, axis, value)
        }
    }

    // Get a "flattened" value (required because some gamepads don't return 0,0 for a stick at rest - flattening the values corrects that)
    private fun getFlattenedValue(event: MotionEvent, axis: Int): Float {
        val range: InputDevice.MotionRange? = event.device.getMotionRange(axis, event.source)

        val value = event.getAxisValue(axis)
        if(range != null && abs(value) > range.flat){
            return value
        }

        return 0F
    }

    private fun onButtonEvent(fromDeviceId: Int, keyCode: Int, isPressed: Boolean){
        onButtonEvent.forEach { it.invoke(fromDeviceId, keyCode, isPressed) }
    }

    private fun onMotion(fromDeviceId: Int, axisCode: Int, axisState: Float){
        onMotion.forEach { it.invoke(fromDeviceId, axisCode, axisState) }
    }
}