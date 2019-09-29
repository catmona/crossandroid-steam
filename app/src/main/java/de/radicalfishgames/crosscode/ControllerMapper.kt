package de.radicalfishgames.crosscode

import android.os.Handler
import android.util.Log
import android.view.View
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.activity_game.*

class ControllerMapper(private val game: GameWrapper, override val containerView: View?): LayoutContainer {

    private val gamepad = game.gamepadState

    fun initControls(){

        val handler = Handler()

        overlay_toggle.apply {
            setBodyAlphaPercent(0.1)

            onRelease.add {
                game.showControls = !game.showControls

                if(game.showControls){

                    aim_pad.visibility = View.VISIBLE
                    dash_button.visibility = View.VISIBLE
                    quickmenu_button.visibility = View.VISIBLE
                    shield_button.visibility = View.VISIBLE
                    movement_pad.visibility = View.VISIBLE
                    menu_button.visibility = View.VISIBLE
                }else{

                    aim_pad.visibility = View.GONE
                    dash_button.visibility = View.GONE
                    quickmenu_button.visibility = View.GONE
                    shield_button.visibility = View.GONE
                    movement_pad.visibility = View.GONE
                    menu_button.visibility = View.GONE
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

        movement_pad.apply {
            setBodyAlphaPercent(0.3)
            innerRadiusPercent = 0.7
            invalidate()

            var lastInsideInnerCircle = false

            onDirectionUpdate.add { xDir, yDir, isInnerCircle ->

                lastInsideInnerCircle = isInnerCircle

                gamepad.rightTrigger.pressed = !isInnerCircle

                val xActual = when {
                    xDir > 0.15 -> 1.0
                    xDir < -0.15 -> -1.0
                    else -> 0.0
                }

                val yActual = when {
                    yDir > 0.15 -> 1.0
                    yDir < -0.15 -> -1.0
                    else -> 0.0
                }

                gamepad.leftStick.horizontal.state = xActual
                gamepad.leftStick.vertical.state = yActual
            }

            onRelease.add {

                gamepad.rightTrigger.pressed = false

                gamepad.leftStick.vertical.state = 0.0
                gamepad.leftStick.horizontal.state = 0.0

                // Confirm quickmenu selection
                //TODO Do this right even when when there was no movement
                if(!lastInsideInnerCircle && gamepad.leftTrigger.pressed){
                    gamepad.rightCross.bottom.pressed = true

                    handler.postDelayed({
                        gamepad.rightCross.bottom.pressed = false
                    }, FAKE_CLICK_TIME)
                }
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

            var lastInsideInnerCircle = false

            onDirectionUpdate.add { xDir, yDir, isInsideInner ->

                lastInsideInnerCircle = isInsideInner

                if(isInsideInner){
                    gamepad.rightCross.bottom.pressed = true
                    gamepad.rightBumper.pressed = true

                    gamepad.rightStick.horizontal.state = 0.0
                    gamepad.rightStick.vertical.state = 0.0

                }else{

                    gamepad.rightCross.bottom.pressed = false
                    gamepad.rightBumper.pressed = false

                    gamepad.rightStick.horizontal.state = xDir
                    gamepad.rightStick.vertical.state = yDir
                }
            }

            onRelease.add {

                if(lastInsideInnerCircle){
                    gamepad.rightCross.bottom.pressed = false
                    gamepad.rightBumper.pressed = false

                }else{
                    gamepad.rightCross.bottom.pressed = false
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

        menu_button.apply {
            setBodyAlphaPercent(0.2)

            var startedToPressAt: Long = 0
            onTouch.add {
                startedToPressAt = System.currentTimeMillis()

            }

            onRelease.add {

                val timeDiff = (System.currentTimeMillis() - startedToPressAt)

                // Decide based on long- or short click whether to open the inventory or menu
                val openMenu = timeDiff > (0.5 * 1000)

                // Open menu
                if(openMenu){
                    gamepad.startOrForward.pressed = true

                // Open inventory
                }else{
                    gamepad.selectOrBack.pressed = true
                }

                handler.postDelayed({

                    if(openMenu){
                        gamepad.startOrForward.pressed = false
                    }else{
                        gamepad.selectOrBack.pressed = false
                    }

                }, FAKE_CLICK_TIME)
            }
        }
    }

    companion object {
        const val FAKE_CLICK_TIME = 20L
    }
}