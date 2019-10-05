package de.radicalfishgames.crosscode

import android.hardware.input.InputManager
import android.view.InputDevice


class NativeControllerListener(
    private val onFirstControllerConnected: () -> Unit,
    private val onControllerSwitched: () -> Unit,
    private val onLastControllerDisconnected: () -> Unit
) : InputManager.InputDeviceListener {

    var nativeControllerDeviceId = -1
        private set

    var hasNativeController: Boolean
        get() {
            return nativeControllerDeviceId != -1
        }
        private set(_) {}

    init {
        findNativeController()
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        val device = InputDevice.getDevice(deviceId)

        if(!hasNativeController && isSupportedController(device)){
            nativeControllerDeviceId = deviceId
        }
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        val device = InputDevice.getDevice(deviceId)

        if(deviceId == nativeControllerDeviceId && !isSupportedController(device)){
            // The current controller somehow lost some of its required functionality, so it's dead to us now.
            onInputDeviceRemoved(deviceId)
        }
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        if(deviceId == nativeControllerDeviceId){
            nativeControllerDeviceId = -1
            findNativeController()

            if(!hasNativeController){
                onLastControllerDisconnected()
            }
        }
    }

    private fun findNativeController(isSwitch: Boolean = false){
        InputDevice.getDeviceIds().forEach { deviceId ->
            val device = InputDevice.getDevice(deviceId)

            if(!hasNativeController && isSupportedController(device)){
                nativeControllerDeviceId = deviceId

                if(!isSwitch){
                    onFirstControllerConnected()
                }else{
                    onControllerSwitched()
                }
            }
        }
    }

    private fun isSupportedController(device: InputDevice): Boolean{
        return device.supportsSource(InputDevice.SOURCE_GAMEPAD)
                && device.supportsSource(InputDevice.SOURCE_DPAD)
                && device.supportsSource(InputDevice.SOURCE_JOYSTICK)
    }
}