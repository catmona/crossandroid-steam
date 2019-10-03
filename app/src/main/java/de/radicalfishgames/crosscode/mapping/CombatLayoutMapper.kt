package de.radicalfishgames.crosscode.mapping

import android.graphics.BitmapFactory
import android.view.View
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.R
import kotlinx.android.synthetic.main.combat_layout.*

class CombatLayoutMapper(game: GameWrapper) : VirtualControllerMapper(game) {

    override lateinit var containerView: View
    override val layoutResId = R.layout.combat_layout

    override fun initControls(){

        bindSimpleButton(shield_button, gamepad.leftBumper)
        bindSimpleButton(dash_button, gamepad.leftBumper)
        bindSimpleButton(quickmenu_button, gamepad.leftTrigger)

        overlay_toggle.apply {

            val viewsToHide = arrayOf(aim_pad, dash_button, quickmenu_button, shield_button, movement_pad, menu_button)

            onSwipe.add { right, left, up, down ->
                if(right || left){
                    game.useControls(MenuLayoutMapper(game))

                }else if(up || down){

                    if(game.blockWebViewClicks){
                        game.blockWebViewClicks = false

                        overlay_toggle.imageBitmap = BitmapFactory.decodeResource(context.resources,
                            R.drawable.overlay_off
                        )
                        hideViews(*viewsToHide)
                    }else{
                        game.blockWebViewClicks = true

                        overlay_toggle.imageBitmap = BitmapFactory.decodeResource(context.resources,
                            R.drawable.overlay_on
                        )
                        showViews(*viewsToHide)

                    }

                    overlay_toggle.invalidate()
                }
            }
        }

        elemental_button.onSwipe.add { right, left, up, down ->

            when {
                right -> fakeClick(gamepad.leftCross.right)
                left -> fakeClick(gamepad.leftCross.left)
                up -> fakeClick(gamepad.leftCross.top)
                down -> fakeClick(gamepad.leftCross.bottom)
            }

        }

        movement_pad.apply {

            var lastInsideInnerCircle = false

            onDirectionUpdate.add { xDir, yDir ->

                lastInsideInnerCircle = xDir <= 0.7 && xDir >= -0.7 && yDir <= 0.7 && yDir >= -0.7
                val actionButtonPressed = gamepad.leftBumper.pressed
                        || gamepad.rightBumper.pressed
                        || gamepad.rightStick.vertical.state != 0.0
                        || gamepad.rightStick.horizontal.state != 0.0

                gamepad.rightTrigger.pressed = !lastInsideInnerCircle && actionButtonPressed

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
                if(lastInsideInnerCircle && gamepad.leftTrigger.pressed){
                    fakeClick(gamepad.rightCross.bottom)
                }
            }

        }

        aim_pad.apply {

            var lastInsideInnerCircle = false

            onDirectionUpdate.add { xDir, yDir ->

                lastInsideInnerCircle = xDir <= 0.3 && xDir >= -0.3 && yDir <= 0.3 && yDir >= -0.3

                if(lastInsideInnerCircle){
                    gamepad.rightCross.bottom.pressed = true
                    gamepad.rightBumper.pressed = true

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

                    gamepad.rightStick.vertical.state = 0.0
                    gamepad.rightStick.horizontal.state = 0.0
                }else{
                    gamepad.rightCross.bottom.pressed = false

                    // CrossCode expects a button press to fire, so we give it one
                    fakeClick(gamepad.rightBumper)
                    handler.postDelayed({

                        gamepad.rightStick.vertical.state = 0.0
                        gamepad.rightStick.horizontal.state = 0.0
                    },
                        FAKE_CLICK_TIME
                    )
                }
            }
        }

        menu_button.apply {

            var startedToPressAt: Long = 0
            onTouch.add {
                startedToPressAt = System.currentTimeMillis()

            }

            onRelease.add {

                val timeDiff = (System.currentTimeMillis() - startedToPressAt)

                // Decide based on long- or short click whether to open the inventory or menu
                val openMenu = timeDiff > (0.4 * 1000)

                // Open menu
                if(openMenu){
                    fakeClick(gamepad.startOrForward)

                // Open inventory
                }else{
                    fakeClick(gamepad.selectOrBack)
                }
            }
        }
    }
}