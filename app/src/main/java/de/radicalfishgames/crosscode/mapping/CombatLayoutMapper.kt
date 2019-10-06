package de.radicalfishgames.crosscode.mapping

import android.graphics.BitmapFactory
import android.view.View
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.gamelisteners.ElementMode
import kotlinx.android.synthetic.main.combat_layout.*

class CombatLayoutMapper(game: GameWrapper) : VirtualControllerMapper(game) {

    override val layoutResId = R.layout.combat_layout

    private lateinit var viewsToHide: Array<View>

    private var currentElement = ElementMode.NEUTRAL

    override fun initControls(){

        viewsToHide = arrayOf(aim_pad, dash_button, quickmenu_button, shield_button, movement_pad, menu_button, elemental_button)

        bindSimpleButton(shield_button, gamepad.leftBumper)
        bindSimpleButton(dash_button, gamepad.leftBumper)
        bindSimpleButton(quickmenu_button, gamepad.leftTrigger)

        overlay_toggle.apply {

            onSwipe.add { right, left, up, down ->
                // Do in handler to avoid a crash due to the potential layout changes
                handler.post {

                    if(right || left){
                        game.blockWebViewClicks = true
                        game.useControls(MenuLayoutMapper(game))

                    }else if(up || down){

                        if(game.blockWebViewClicks){
                            disableOverlay()
                        }else{
                            enableOverlay()
                        }
                    }
                }
            }
        }

        elemental_button.onSwipe.add { right, left, up, down ->

            // Switch buttons, because with our method you go back to neutral by swiping in the
            // opposite direction as before. Eg Neutral -> Heat: Swipe Down and then Heat -> Neutral: Swipe Up
            when (currentElement) {
                ElementMode.NEUTRAL -> when {
                    right -> fakeClick(gamepad.leftCross.right)
                    left -> fakeClick(gamepad.leftCross.left)
                    up -> fakeClick(gamepad.leftCross.top)
                    down -> fakeClick(gamepad.leftCross.bottom)
                }
                ElementMode.HEAT, ElementMode.COLD -> when {
                    right -> fakeClick(gamepad.leftCross.right)
                    left -> fakeClick(gamepad.leftCross.left)
                    up -> fakeClick(gamepad.leftCross.bottom)
                    down -> fakeClick(gamepad.leftCross.top)
                }
                ElementMode.SHOCK, ElementMode.WAVE -> when {
                    right -> fakeClick(gamepad.leftCross.left)
                    left -> fakeClick(gamepad.leftCross.right)
                    up -> fakeClick(gamepad.leftCross.top)
                    down -> fakeClick(gamepad.leftCross.bottom)
                }
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
                val openMenu = timeDiff > (0.2 * 1000)

                // Open menu
                if(openMenu){
                    fakeClick(gamepad.startOrForward)

                // Open inventory
                }else{
                    fakeClick(gamepad.selectOrBack)
                }
            }
        }


        game.gameEvents.apply {

            clearListeners()

            onMenuEnter.add {
                game.useControls(MenuLayoutMapper(game))
            }

            onCutsceneEnter.add {
                disableOverlay()
            }

            onCutsceneExit.add {
                enableOverlay()
            }

            onElementChange.add { elementMode ->

                currentElement = elementMode

                val elementUIImageResID = when(elementMode){
                    ElementMode.NEUTRAL -> R.drawable.element_neutral
                    ElementMode.HEAT -> R.drawable.element_heat
                    ElementMode.COLD -> R.drawable.element_cold
                    ElementMode.SHOCK -> R.drawable.element_shock
                    ElementMode.WAVE -> R.drawable.element_wave
                }

                elemental_button.imageBitmap = BitmapFactory.decodeResource(
                    elemental_button.context.resources,
                    elementUIImageResID
                )

                elemental_button.invalidate()
            }
        }
    }

    private fun disableOverlay(){
        game.blockWebViewClicks = false

        overlay_toggle.imageBitmap = BitmapFactory.decodeResource(
            overlay_toggle.context.resources,
            R.drawable.overlay_off
        )
        hideViews(*viewsToHide)

        overlay_toggle.invalidate()
    }

    private fun enableOverlay(){
        game.blockWebViewClicks = true

        overlay_toggle.imageBitmap = BitmapFactory.decodeResource(
            overlay_toggle.context.resources,
            R.drawable.overlay_on
        )
        showViews(*viewsToHide)

        overlay_toggle.invalidate()
    }
}