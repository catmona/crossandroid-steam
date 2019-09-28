package de.radicalfishgames.crosscode

import android.view.ViewGroup
import de.radicalfishgames.crosscode.overlay.CircleButton
import de.radicalfishgames.crosscode.overlay.DirectionalPad


class ControllerMapper(private val game: GameWrapper, private val rootView: ViewGroup) {

    private val gamepad = game.gamepadState

    fun initControls(){

        val testButton = rootView.findViewById<CircleButton>(R.id.test_button)
        testButton.setBodyAlphaPercent(0.3)
        bindButton(testButton, gamepad.rightCross.bottom)

        val movementPad = rootView.findViewById<DirectionalPad>(R.id.movement_pad)
        movementPad.setBodyAlphaPercent(0.3)
        movementPad.onDirectionUpdate.add { xDir: Double, yDir: Double -> updateState {
            // Scale the results up a bit. This way the maximum running speed is achieved
            // sooner, and we can activate dash for higher values.
            val factor = 1.3
            var dash = false

            val actualX: Double
            if(xDir * factor <= 1.0){
                actualX = xDir * factor
            }else{
                actualX = 1.0
                dash = true
            }

            val actualY: Double
            if(yDir * factor <= 1.0){
                actualY = yDir * factor
            }else{
                actualY = 1.0
                dash = true
            }


            gamepad.leftStick.horizontal = actualX
            gamepad.leftStick.vertical = actualY

            gamepad.leftBumper.pressed = dash
        } }

        movementPad.onRelease.add { run {
            updateState {
                gamepad.leftStick.vertical = 0.0
                gamepad.leftStick.horizontal = 0.0

                gamepad.leftBumper.pressed = false
            }
        } }
    }

    private fun bindButton(button: CircleButton, gamepadButton: GamepadState.Button){
        button.onTouch.add { run {
            updateState { gamepadButton.pressed = true }
        } }
        button.onRelease.add { run {
            updateState { gamepadButton.pressed = false }
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