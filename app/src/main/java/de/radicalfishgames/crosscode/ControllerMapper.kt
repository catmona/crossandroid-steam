package de.radicalfishgames.crosscode

import android.os.Handler
import android.view.ViewGroup
import de.radicalfishgames.crosscode.overlay.CircleButton
import de.radicalfishgames.crosscode.overlay.DirectionalPad
import de.radicalfishgames.crosscode.overlay.RingDirectionalPad


class ControllerMapper(game: GameWrapper, private val rootView: ViewGroup) {

    private val gamepad = game.gamepadState

    fun initControls(){

        val handler = Handler()

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
                gamepad.leftStick.horizontal.state = xDir
                gamepad.leftStick.vertical.state = yDir

            }

            onRelease.add {
                gamepad.leftStick.vertical.state = 0.0
                gamepad.leftStick.horizontal.state = 0.0
            }

        }


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

        rootView.findViewById<RingDirectionalPad>(R.id.aim_pad).apply {
            setBodyAlphaPercent(0.3)

            onDirectionUpdate.add { xDir: Double, yDir: Double ->
                gamepad.rightStick.horizontal.state = xDir
                gamepad.rightStick.vertical.state = yDir
            }

            onRelease.add {
                gamepad.rightBumper.pressed = true

                // CrossCode expects a button press to fire, so we give it one
                handler.postDelayed({
                    gamepad.rightBumper.pressed = false

                    gamepad.rightStick.vertical.state = 0.0
                    gamepad.rightStick.horizontal.state = 0.0
                }, 20)
            }
        }
    }
}