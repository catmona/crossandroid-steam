package de.radicalfishgames.crosscode

import android.view.ViewGroup
import de.radicalfishgames.crosscode.overlay.CircleButton
import de.radicalfishgames.crosscode.overlay.DirectionalPad
import kotlin.math.abs


class ControllerMapper(game: GameWrapper, private val rootView: ViewGroup) {

    private val gamepad = game.gamepadState

    fun initControls(){

        rootView.findViewById<CircleButton>(R.id.action_button).apply {
            setBodyAlphaPercent(0.3)
            onTouch.add {
                gamepad.rightCross.bottom.pressed = true
                gamepad.rightBumper.pressed = true
            }
            onRelease.add {
                gamepad.rightCross.bottom.pressed = false
                gamepad.rightBumper.pressed = false
            }
        }

        rootView.findViewById<CircleButton>(R.id.dash_button).apply {
            setBodyAlphaPercent(0.3)

            onTouch.add {
                gamepad.leftBumper.pressed = true
            }
            onRelease.add {
                gamepad.leftBumper.pressed = false
            }
        }

        rootView.findViewById<CircleButton>(R.id.ult_button).apply {
            setBodyAlphaPercent(0.3)

            onTouch.add {
                gamepad.rightTrigger.pressed = true
            }
            onRelease.add {
                gamepad.rightTrigger.pressed = false
            }
        }

        rootView.findViewById<DirectionalPad>(R.id.movement_pad).apply {
            setBodyAlphaPercent(0.3)

            onDirectionUpdate.add { xDir: Double, yDir: Double ->
                // Scale the results up a bit. This way the maximum running speed is achieved
                // even without tapping the outermost end of the circle,
                // and we can activate dash for the highest values.
                val factor = 1.4

                val actualX: Double
                if(abs(xDir * factor) <= 1.0){
                    actualX = xDir * factor
                }else{
                    if(xDir > 0){
                        actualX = 1.0
                    }else{
                        actualX = -1.0
                    }
                }

                val actualY: Double
                if(abs(yDir * factor) <= 1.0){
                    actualY = yDir * factor
                }else{
                    if(yDir > 0){
                        actualY = 1.0
                    }else{
                        actualY = -1.0
                    }
                }


                gamepad.leftStick.horizontal.state = actualX
                gamepad.leftStick.vertical.state = actualY

            }

            onRelease.add {
                gamepad.leftStick.vertical.state = 0.0
                gamepad.leftStick.horizontal.state = 0.0

                gamepad.leftBumper.pressed = false
            }

        }

    }
}