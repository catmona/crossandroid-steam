package de.radicalfishgames.crosscode

import android.os.Handler
import android.view.View
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.activity_game.*

class ControllerMapper(private val game: GameWrapper, override val containerView: View?): LayoutContainer {

    private val gamepad = game.gamepadState

    var alwaysSprint = true

    fun initControls(){

        val handler = Handler()

        overlay_toggle.apply {
            setBodyAlphaPercent(0.3)

            onRelease.add {
                game.showControls = !game.showControls

                if(game.showControls){

                    aim_pad.visibility = View.VISIBLE
                    action_button.visibility = View.VISIBLE
                    dash_button.visibility = View.VISIBLE
                    quickmenu_button.visibility = View.VISIBLE
                    ult_button.visibility = View.VISIBLE
                    shield_button.visibility = View.VISIBLE
                    movement_pad.visibility = View.VISIBLE

                }else{

                    aim_pad.visibility = View.GONE
                    action_button.visibility = View.GONE
                    dash_button.visibility = View.GONE
                    quickmenu_button.visibility = View.GONE
                    ult_button.visibility = View.GONE
                    shield_button.visibility = View.GONE
                    movement_pad.visibility = View.GONE
                }

                containerView?.invalidate()
            }
        }

        shield_button.apply {
            setBodyAlphaPercent(0.3)

            onTouch.add {
                gamepad.leftBumper.pressed = true
            }
            onRelease.add {
                gamepad.leftBumper.pressed = false
            }
        }

        ult_button.apply {
            setBodyAlphaPercent(0.3)

            onTouch.add {
                gamepad.rightTrigger.pressed = true
            }
            onRelease.add {
                gamepad.rightTrigger.pressed = false
            }
        }

        movement_pad.apply {
            setBodyAlphaPercent(0.3)

            onDirectionUpdate.add { xDir: Double, yDir: Double ->
                if(!alwaysSprint){
                    gamepad.leftStick.horizontal.state = xDir
                    gamepad.leftStick.vertical.state = yDir

                }else{
                    val xActual = when {
                        xDir > 0.2 -> 1.0
                        xDir < -0.2 -> -1.0
                        else -> 0.0
                    }

                    val yActual = when {
                        yDir > 0.2 -> 1.0
                        yDir < -0.2 -> -1.0
                        else -> 0.0
                    }

                    gamepad.leftStick.horizontal.state = xActual
                    gamepad.leftStick.vertical.state = yActual
                }

            }

            onRelease.add {
                gamepad.leftStick.vertical.state = 0.0
                gamepad.leftStick.horizontal.state = 0.0

                // Confirm quickmenu selection automatically
                if(gamepad.leftTrigger.pressed){
                    gamepad.rightCross.bottom.pressed = true

                    handler.postDelayed({
                        gamepad.rightCross.bottom.pressed = false
                    }, FAKE_CLICK_TIME)
                }
            }

        }


        action_button.apply {
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

        dash_button.apply {
            setBodyAlphaPercent(0.3)

            onTouch.add {
                gamepad.leftBumper.pressed = true
            }
            onRelease.add {
                gamepad.leftBumper.pressed = false
            }
        }

        quickmenu_button.apply {
            setBodyAlphaPercent(0.3)

            onTouch.add {
                gamepad.leftTrigger.pressed = true
            }
            onRelease.add {
                gamepad.leftTrigger.pressed = false
            }
        }

        aim_pad.apply {
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
                }, FAKE_CLICK_TIME)
            }
        }
    }

    companion object {
        const val FAKE_CLICK_TIME = 20L
    }
}