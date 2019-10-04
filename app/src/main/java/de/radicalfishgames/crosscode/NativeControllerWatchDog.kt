package de.radicalfishgames.crosscode

import android.os.Handler
import android.view.InputDevice


class NativeControllerWatchDog(
    private val onFirstControllerConnected: () -> Unit,
    private val onControllerSwitched: () -> Unit,
    private val onLastControllerDisconnected: () -> Unit
) {

    private val handler = Handler()

    var nativeControllerDeviceId = -1
        private set

    var hasNativeController: Boolean
        get() {
            return nativeControllerDeviceId != -1
        }
        private set(_) {}

    fun startWatching(){

        nativeControllerDeviceId = -1

        var foundControllerId = -1
        InputDevice.getDeviceIds().forEach { deviceId ->
            val device = InputDevice.getDevice(deviceId)

            if(isSupportedController(device) && foundControllerId == -1){
                foundControllerId = deviceId
            }
        }

        val foundController = foundControllerId != -1

        if(foundController && !hasNativeController){
            nativeControllerDeviceId = foundControllerId
            onFirstControllerConnected()

        }else if(foundController && nativeControllerDeviceId != foundControllerId){
            nativeControllerDeviceId = foundControllerId
            onControllerSwitched()

        }else if(!foundController && hasNativeController){
            nativeControllerDeviceId = -1
            onLastControllerDisconnected()
        }

        // Repeat check ad infinitum to listen for controllers added during the game
        handler.postDelayed(::startWatching, CHECK_DELAY)
    }

    private fun isSupportedController(device: InputDevice): Boolean{
        return device.supportsSource(InputDevice.SOURCE_GAMEPAD)
                && device.supportsSource(InputDevice.SOURCE_DPAD)
                && device.supportsSource(InputDevice.SOURCE_JOYSTICK)
    }

    fun stopWatching(){
        // Remove all callbacks from this handler (there should really be a "sugar" method for this)
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        // Delay in ms
        const val CHECK_DELAY = 2 * 1000L
    }
}