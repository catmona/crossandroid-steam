package de.radicalfishgames.crosscode.mapping

import android.graphics.BitmapFactory
import android.os.Handler
import android.view.View
import de.radicalfishgames.crosscode.GamepadJsonBridge
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.features.VirtualControllerFeature
import de.radicalfishgames.crosscode.gamelisteners.ElementMode
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager
import de.radicalfishgames.crosscode.overlay.SwipeControl
import kotlinx.android.synthetic.main.combat_layout.*
import kotlin.math.pow

class CombatLayoutMapper(feature: VirtualControllerFeature,
                         gamepad: GamepadJsonBridge,
                         private val alwaysSprint: Boolean
) : VirtualControllerMapper(feature, gamepad) {

    override val layoutResId = R.layout.combat_layout

    override lateinit var overlayToggle: SwipeControl
    override lateinit var overlayViews: Array<View>

    private var currentElement = ElementMode.NEUTRAL
    private var dashState = NONE

    override fun initControls(){

        overlayToggle = overlay_toggle
        overlayViews = arrayOf(aim_pad, dash_button, quickmenu_button, shield_button, movement_pad, menu_button, elemental_button)

        bindSimpleButton(shield_button, gamepad.leftBumper)

        bindSimpleButton(dash_button, gamepad.leftBumper)

        bindSimpleButton(quickmenu_button, gamepad.leftTrigger)

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

                val currentInsideInnerCircle = xDir.pow(2) + yDir.pow(2) <= 0.7.pow(2)

                val xActual = when {
                    alwaysSprint && xDir > 0.15 -> 1.0
                    alwaysSprint && xDir < -0.15 -> -1.0
                    else -> xDir * 1.3
                }

                val yActual = when {
                    alwaysSprint && yDir > 0.15 -> 1.0
                    alwaysSprint && yDir < -0.15 -> -1.0
                    else -> yDir * 1.3
                }

                gamepad.leftStick.horizontal.state = xActual
                gamepad.leftStick.vertical.state = yActual

                if(dashState == NONE && !currentInsideInnerCircle){
                    dashState = DASHING

                    handler.postDelayed({
                        gamepad.leftBumper.pressed = true

                        handler.postDelayed({

                            if(dashState == DASHING_MOVE_END){
                                gamepad.leftStick.vertical.state = 0.0
                                gamepad.leftStick.horizontal.state = 0.0

                                gamepad.leftBumper.pressed = false

                                dashState = NONE

                            }else if(lastInsideInnerCircle){
                                gamepad.leftBumper.pressed = false

                                dashState = NONE

                            }else{
                                dashState = DASHED
                            }
                        }, FAKE_CLICK_TIME)
                    }, 60L)

                }

                if(currentInsideInnerCircle && dashState == DASHED){
                    gamepad.leftBumper.pressed = false

                    dashState = NONE

                }

                lastInsideInnerCircle = currentInsideInnerCircle
            }

            onRelease.add {

                if(dashState != DASHING){
                    gamepad.leftStick.vertical.state = 0.0
                    gamepad.leftStick.horizontal.state = 0.0

                    gamepad.leftBumper.pressed = false

                    dashState = NONE
                }  else {
                    dashState = DASHING_MOVE_END
                }

                // Confirm quickmenu selection
                if(lastInsideInnerCircle && gamepad.leftTrigger.pressed){
                    fakeClick(gamepad.rightCross.bottom)
                }

                lastInsideInnerCircle = false
            }

        }

        aim_pad.apply {

            var lastInsideInnerCircle = false

            var longPressPending = false
            val longPressHandler = Handler()

            onDirectionUpdate.add { xDir, yDir ->

                val currentInsideInnerCircle = xDir.pow(2) + yDir.pow(2) <= 0.3.pow(2)

                if(currentInsideInnerCircle){

                    val actionButtonPressed = (currentInsideInnerCircle != lastInsideInnerCircle)
                            || gamepad.leftBumper.pressed
                            || dashState != NONE

                    if(actionButtonPressed){
                        // Trigger combat arts for everything except melee
                        gamepad.rightTrigger.pressed = true

                    }else{
                        gamepad.rightCross.bottom.pressed = true
                        gamepad.rightBumper.pressed = true
                    }

                    // Trigger combat arts for melee
                    if(!longPressPending){
                        longPressHandler.postDelayed({
                            gamepad.rightTrigger.pressed = true
                        }, 150L)
                        longPressPending = true
                    }
                }else{

                    gamepad.rightCross.bottom.pressed = false
                    gamepad.rightBumper.pressed = false

                    gamepad.rightStick.horizontal.state = xDir
                    gamepad.rightStick.vertical.state = yDir

                    longPressHandler.removeCallbacksAndMessages(null)
                    longPressPending = false
                    gamepad.rightTrigger.pressed = false
                }

                lastInsideInnerCircle = currentInsideInnerCircle
            }

            onRelease.add {

                longPressHandler.removeCallbacksAndMessages(null)
                longPressPending = false
                gamepad.rightTrigger.pressed = false

                if(lastInsideInnerCircle){
                    gamepad.rightCross.bottom.pressed = false
                    gamepad.rightBumper.pressed = false

                    gamepad.rightStick.vertical.state = 0.0
                    gamepad.rightStick.horizontal.state = 0.0

                }else{
                    gamepad.rightCross.bottom.pressed = false

                    // CrossCode expects a button press to fire, so we give it one
                    fakeClick(gamepad.rightBumper) {
                        gamepad.rightStick.vertical.state = 0.0
                        gamepad.rightStick.horizontal.state = 0.0
                    }
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

        super.initControls()
    }

    override fun registerGameEventListeners(eventManager: GameEventManager) {
        eventManager.apply {

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

    companion object {
        const val NONE = 0
        const val DASHING = 1
        const val DASHING_MOVE_END = 2
        const val DASHED = 3
    }
}