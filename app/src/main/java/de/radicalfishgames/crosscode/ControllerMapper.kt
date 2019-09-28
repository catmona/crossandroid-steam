package de.radicalfishgames.crosscode

import android.view.ViewGroup
import de.radicalfishgames.crosscode.overlay.CircleButton
import de.radicalfishgames.crosscode.overlay.DirectionalPad


class ControllerMapper(game: GameWrapper, private val rootView: ViewGroup) {

    private val gamepad = game.gamepadState

    fun initControls(){

        val actionButton = rootView.findViewById<CircleButton>(R.id.action_button)
        actionButton.setBodyAlphaPercent(0.3)
        actionButton.onTouch.add {
            gamepad.rightCross.bottom.pressed = true
            gamepad.rightBumper.pressed = true
        }
        actionButton.onRelease.add {
            gamepad.rightCross.bottom.pressed = false
            gamepad.rightBumper.pressed = false
        }

        val movementPad = rootView.findViewById<DirectionalPad>(R.id.movement_pad)
        movementPad.setBodyAlphaPercent(0.3)
        movementPad.onDirectionUpdate.add { xDir: Double, yDir: Double ->
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


            gamepad.leftStick.horizontal.state = actualX
            gamepad.leftStick.vertical.state = actualY

            gamepad.leftBumper.pressed = dash
        }

        movementPad.onRelease.add {
            gamepad.leftStick.vertical.state = 0.0
            gamepad.leftStick.horizontal.state = 0.0

            gamepad.leftBumper.pressed = false
        }
    }
}