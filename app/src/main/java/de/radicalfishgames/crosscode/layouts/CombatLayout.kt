package de.radicalfishgames.crosscode.layouts

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.ValueCallback
import de.radicalfishgames.crosscode.GamepadJsonBridge
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.databinding.CombatLayoutBinding
import de.radicalfishgames.crosscode.features.VirtualControllerFeature
import de.radicalfishgames.crosscode.gamelisteners.ElementMode
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager
import kotlin.math.pow

object CombatLayout : VirtualControllerLayout() {
    override val layoutResId = R.layout.combat_layout

    var alwaysSprint = true
    var dashOnlyOnTap = false

    private var currentElement = ElementMode.NEUTRAL

    // Dash states
    private const val NONE = 0
    private const val DASHING = 1
    private const val DASHING_MOVE_END = 2
    private const val DASHED = 3

    private var dashState = NONE

    override fun bindControls(
        gamepad: GamepadJsonBridge,
        layoutView: View,
        feature: VirtualControllerFeature,
        eventManager: GameEventManager,
    ) {
        val binding = CombatLayoutBinding.bind(layoutView)

        binding.apply {
            overlayToggle.onSwipe { right, left, up, down ->
                // Do in handler to avoid a crash due to the potential layout changes
                handler.post {
                    if (right || left) {
                        feature.switchToLayout(MenuLayout)
                    } else if (up || down) {
                        feature.enterDirect()
                    }
                }
            }

            bindSimpleButton(shieldButton, gamepad.leftBumper)
            bindSimpleButton(dashButton, gamepad.leftBumper)
            bindSimpleButton(quickmenuButton, gamepad.leftTrigger)

            elementalButton.onSwipe { right, left, up, down ->

                // Directions are mirrored per element so swiping the opposite way returns to neutral
                // (e.g. Neutral -> Heat: swipe down, Heat -> Neutral: swipe up)
                when (currentElement) {
                    ElementMode.NEUTRAL -> {
                        when {
                            right -> fakeClick(gamepad.leftCross.right)
                            left -> fakeClick(gamepad.leftCross.left)
                            up -> fakeClick(gamepad.leftCross.top)
                            down -> fakeClick(gamepad.leftCross.bottom)
                        }
                    }

                    ElementMode.HEAT, ElementMode.COLD -> {
                        when {
                            right -> fakeClick(gamepad.leftCross.right)
                            left -> fakeClick(gamepad.leftCross.left)
                            up -> fakeClick(gamepad.leftCross.bottom)
                            down -> fakeClick(gamepad.leftCross.top)
                        }
                    }

                    ElementMode.SHOCK, ElementMode.WAVE -> {
                        when {
                            right -> fakeClick(gamepad.leftCross.left)
                            left -> fakeClick(gamepad.leftCross.right)
                            up -> fakeClick(gamepad.leftCross.top)
                            down -> fakeClick(gamepad.leftCross.bottom)
                        }
                    }
                }
            }

            eventManager.onElementChange { elementMode ->

                currentElement = elementMode

                val elementUIImageResID =
                    when (elementMode) {
                        ElementMode.NEUTRAL -> R.drawable.element_neutral
                        ElementMode.HEAT -> R.drawable.element_heat
                        ElementMode.COLD -> R.drawable.element_cold
                        ElementMode.SHOCK -> R.drawable.element_shock
                        ElementMode.WAVE -> R.drawable.element_wave
                    }

                elementalButton.imageBitmap =
                    BitmapFactory.decodeResource(
                        elementalButton.context.resources,
                        elementUIImageResID,
                    )

                elementalButton.invalidate()
            }

            movementPad.apply {
                var lastInsideInnerCircle = false
                var hasBeenInsideInnerCircle = false

                onDirectionUpdate { xDir, yDir ->

                    val currentInsideInnerCircle = xDir.pow(2) + yDir.pow(2) <= 0.7.pow(2)
                    hasBeenInsideInnerCircle = hasBeenInsideInnerCircle || currentInsideInnerCircle

                    val xActual =
                        when {
                            alwaysSprint && xDir > 0.15 -> 1.0
                            alwaysSprint && xDir < -0.15 -> -1.0
                            else -> (xDir * 1.3).coerceIn(-1.0, 1.0)
                        }

                    val yActual =
                        when {
                            alwaysSprint && yDir > 0.15 -> 1.0
                            alwaysSprint && yDir < -0.15 -> -1.0
                            else -> (yDir * 1.3).coerceIn(-1.0, 1.0)
                        }

                    gamepad.leftStick.horizontal.state = xActual
                    gamepad.leftStick.vertical.state = yActual

                    if (dashState == NONE && !currentInsideInnerCircle && (!dashOnlyOnTap || !hasBeenInsideInnerCircle)) {
                        dashState = DASHING

                        CombatLayout.handler.postDelayed({
                            gamepad.leftBumper.pressed = true

                            CombatLayout.handler.postDelayed({
                                if (dashState == DASHING_MOVE_END) {
                                    gamepad.leftStick.vertical.state = 0.0
                                    gamepad.leftStick.horizontal.state = 0.0

                                    gamepad.leftBumper.pressed = false

                                    dashState = NONE
                                } else if (lastInsideInnerCircle) {
                                    gamepad.leftBumper.pressed = false

                                    dashState = NONE
                                } else {
                                    dashState = DASHED
                                }
                            }, FAKE_CLICK_TIME)
                        }, 60L)
                    }

                    if (currentInsideInnerCircle && dashState == DASHED) {
                        gamepad.leftBumper.pressed = false

                        dashState = NONE
                    }

                    lastInsideInnerCircle = currentInsideInnerCircle
                }

                onRelease {
                    if (dashState != DASHING) {
                        gamepad.leftStick.vertical.state = 0.0
                        gamepad.leftStick.horizontal.state = 0.0

                        gamepad.leftBumper.pressed = false

                        dashState = NONE
                    } else {
                        dashState = DASHING_MOVE_END
                    }

                    // Confirm quickmenu selection
                    if (lastInsideInnerCircle && gamepad.leftTrigger.pressed) {
                        fakeClick(gamepad.rightCross.bottom)
                    }

                    lastInsideInnerCircle = false
                    hasBeenInsideInnerCircle = false
                }
            }

            aimPad.apply {
                var lastInsideInnerCircle = false

                var longPressPending = false
                var combatArtPending = false
                val longPressHandler = Handler(Looper.getMainLooper())

                onDirectionUpdate { xDir, yDir ->

                    val currentInsideInnerCircle = xDir.pow(2) + yDir.pow(2) <= 0.3.pow(2)

                    if (currentInsideInnerCircle) {
                        val actionButtonPressed =
                            gamepad.leftBumper.pressed ||
                                dashState != NONE ||
                                gamepad.rightStick.vertical.state != 0.0 ||
                                gamepad.rightStick.horizontal.state != 0.0

                        if (actionButtonPressed) {
                            // Trigger combat arts - the delay is there to avoid triggering the art accidentally
                            if (!longPressPending) {
                                longPressHandler.postDelayed({
                                    val actionButtonStillPressed =
                                        gamepad.leftBumper.pressed ||
                                            dashState != NONE ||
                                            gamepad.rightStick.vertical.state != 0.0 ||
                                            gamepad.rightStick.horizontal.state != 0.0

                                    if (actionButtonStillPressed) {
                                        gamepad.rightTrigger.pressed = true
                                        combatArtPending = true
                                    }
                                }, 100L)
                                longPressPending = true
                            }
                        } else {
                            gamepad.rightCross.bottom.pressed = true
                            gamepad.rightBumper.pressed = true

                            // Trigger combat arts for melee
                            if (!longPressPending) {
                                longPressHandler.postDelayed({
                                    // Pressing next to a push/pull block grabs it, so check right before
                                    // firing; skip the art while grabbing or if the press already ended
                                    feature.runJS(
                                        "ig.game.playerEntity.interactObject === null",
                                        ValueCallback { notGrabbing ->
                                            if (notGrabbing.toBoolean() && longPressPending) {
                                                gamepad.rightTrigger.pressed = true
                                            }
                                        },
                                    )
                                }, 300L)
                                longPressPending = true
                            }
                        }
                    } else {
                        gamepad.rightCross.bottom.pressed = false
                        gamepad.rightBumper.pressed = false

                        // Amplify to compensate for the game's aim dead zones (lost aim, failed combat arts)
                        gamepad.rightStick.horizontal.state = (xDir * 1.2).coerceIn(-1.0, 1.0)
                        gamepad.rightStick.vertical.state = (yDir * 1.2).coerceIn(-1.0, 1.0)

                        longPressHandler.removeCallbacksAndMessages(null)
                        longPressPending = false
                        gamepad.rightTrigger.pressed = false
                    }

                    lastInsideInnerCircle = currentInsideInnerCircle
                }

                onRelease {
                    longPressHandler.removeCallbacksAndMessages(null)
                    longPressPending = false
                    gamepad.rightTrigger.pressed = false

                    if (lastInsideInnerCircle) {
                        gamepad.rightCross.bottom.pressed = false
                        gamepad.rightBumper.pressed = false

                        // If we are executing a throw art, delay resetting the direction
                        // to avoid accidentally switching to a melee art
                        if (combatArtPending) {
                            combatArtPending = false
                            CombatLayout.handler.postDelayed({
                                gamepad.rightStick.vertical.state = 0.0
                                gamepad.rightStick.horizontal.state = 0.0
                            }, 300L)
                        } else {
                            gamepad.rightStick.vertical.state = 0.0
                            gamepad.rightStick.horizontal.state = 0.0
                        }
                    } else {
                        gamepad.rightCross.bottom.pressed = false

                        // CrossCode expects a button press to fire, so we give it one
                        fakeClick(gamepad.rightBumper) {
                            gamepad.rightStick.vertical.state = 0.0
                            gamepad.rightStick.horizontal.state = 0.0
                        }
                    }
                }
            }

            menuButton.apply {
                var startedToPressAt: Long = 0
                onTouch {
                    startedToPressAt = System.currentTimeMillis()
                }

                onRelease {
                    val timeDiff = (System.currentTimeMillis() - startedToPressAt)

                    // Long press opens the menu, short press the inventory
                    val openMenu = timeDiff > (0.2 * 1000)

                    if (openMenu) {
                        fakeClick(gamepad.startOrForward)
                    } else {
                        fakeClick(gamepad.selectOrBack)
                    }
                }
            }
        }
    }
}
