package de.radicalfishgames.crosscode

import android.view.ViewGroup
import de.radicalfishgames.crosscode.overlay.CircleButton
import de.radicalfishgames.crosscode.overlay.DirectionalPad


class ControllerMapper(private val game: GameWrapper, private val rootView: ViewGroup) {

    private val gamepad = game.gamepadState

    fun initControls(){

        val testButton = rootView.findViewById<CircleButton>(R.id.test_button)
        bindButton(testButton, gamepad.rightCross.bottom)

        val testPad = rootView.findViewById<DirectionalPad>(R.id.test_pad)
        bindPad(testPad, gamepad.leftStick)
    }

    private fun bindButton(button: CircleButton, gamepadButton: GamepadState.Button){
        button.onTouch = { updateState { gamepadButton.pressed = true } }
        button.onRelease = { updateState { gamepadButton.pressed = false } }
    }

    private fun bindPad(pad: DirectionalPad, gamepadStick: GamepadState.Stick){
        pad.onDirectionUpdate = { xDir: Double, yDir: Double -> updateState {
            gamepadStick.vertical = yDir
            gamepadStick.horizontal = xDir
        } }

        pad.onRelease = { updateState {
            gamepadStick.vertical = 0.0
            gamepadStick.horizontal = 0.0
        } }
    }

    private fun updateState(update: () -> Unit){
        if(game.isGameReady){
            update()
            game.updateJSGamepad()
        }else {
            update()
        }
    }
}