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
import de.radicalfishgames.crosscode.overlay.CircleDirectionalPad
import de.radicalfishgames.crosscode.overlay.SwipeControl
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

    // Movement pad: walking inside this normalized radius, dashing outside it
    private const val MOVE_WALK_RADIUS = 0.7

    // Movement pad deflection is amplified so full speed is reached before the rim
    private const val MOVE_AMPLIFICATION = 1.3
    private const val DASH_PRESS_DELAY_MS = 60L

    // Aim pad: melee inside this normalized radius, aiming outside it
    private const val AIM_MELEE_RADIUS = 0.3

    // Amplify aim to compensate for the game's aim dead zones (lost aim, failed combat arts)
    private const val AIM_AMPLIFICATION = 1.2

    // Delays before a held press triggers a combat art, to avoid triggering one accidentally
    private const val RANGED_ART_DELAY_MS = 100L
    private const val MELEE_ART_DELAY_MS = 300L

    // How long aim is kept after a throw art so it doesn't turn into a melee art
    private const val THROW_ART_AIM_HOLD_MS = 300L

    override fun bindControls(
        gamepad: GamepadJsonBridge,
        layoutView: View,
        feature: VirtualControllerFeature,
        eventManager: GameEventManager,
    ) {
        val binding = CombatLayoutBinding.bind(layoutView)

        binding.apply {
            bindOverlayToggle(overlayToggle, feature, MenuLayout)

            bindSimpleButton(shieldButton, gamepad.leftBumper)
            bindSimpleButton(dashButton, gamepad.leftBumper)
            bindSimpleButton(quickmenuButton, gamepad.leftTrigger)

            bindElementSwipe(elementalButton, gamepad, eventManager)
            bindMovementPad(movementPad, gamepad)
            bindAimPad(aimPad, gamepad, feature)

            // Short press opens the inventory, long press the menu
            bindTapOrHold(
                menuButton,
                onTap = { fakeClick(gamepad.selectOrBack) },
                onHold = { fakeClick(gamepad.startOrForward) },
            )
        }
    }

    private fun bindElementSwipe(
        elementalButton: SwipeControl,
        gamepad: GamepadJsonBridge,
        eventManager: GameEventManager,
    ) {
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
    }

    private fun bindMovementPad(
        movementPad: CircleDirectionalPad,
        gamepad: GamepadJsonBridge,
    ) = movementPad.apply {
        var lastInsideInnerCircle = false
        var hasBeenInsideInnerCircle = false

        onDirectionUpdate { xDir, yDir ->

            val currentInsideInnerCircle = xDir.pow(2) + yDir.pow(2) <= MOVE_WALK_RADIUS.pow(2)
            hasBeenInsideInnerCircle = hasBeenInsideInnerCircle || currentInsideInnerCircle

            gamepad.leftStick.set(movementAxis(xDir), movementAxis(yDir))

            if (dashState == NONE && !currentInsideInnerCircle && (!dashOnlyOnTap || !hasBeenInsideInnerCircle)) {
                dashState = DASHING

                CombatLayout.handler.postDelayed({
                    gamepad.leftBumper.pressed = true

                    CombatLayout.handler.postDelayed({
                        if (dashState == DASHING_MOVE_END) {
                            gamepad.leftStick.reset()

                            gamepad.leftBumper.pressed = false

                            dashState = NONE
                        } else if (lastInsideInnerCircle) {
                            gamepad.leftBumper.pressed = false

                            dashState = NONE
                        } else {
                            dashState = DASHED
                        }
                    }, FAKE_CLICK_TIME)
                }, DASH_PRESS_DELAY_MS)
            }

            if (currentInsideInnerCircle && dashState == DASHED) {
                gamepad.leftBumper.pressed = false

                dashState = NONE
            }

            lastInsideInnerCircle = currentInsideInnerCircle
        }

        onRelease {
            if (dashState != DASHING) {
                gamepad.leftStick.reset()

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

    private fun movementAxis(value: Double) =
        when {
            alwaysSprint && value > FULL_DEFLECTION_THRESHOLD -> 1.0
            alwaysSprint && value < -FULL_DEFLECTION_THRESHOLD -> -1.0
            else -> value * MOVE_AMPLIFICATION
        }

    private fun bindAimPad(
        aimPad: CircleDirectionalPad,
        gamepad: GamepadJsonBridge,
        feature: VirtualControllerFeature,
    ) = aimPad.apply {
        var lastInsideInnerCircle = false

        var longPressPending = false
        var combatArtPending = false
        val longPressHandler = Handler(Looper.getMainLooper())

        fun isActionButtonPressed() =
            gamepad.leftBumper.pressed ||
                dashState != NONE ||
                gamepad.rightStick.vertical.state != 0.0 ||
                gamepad.rightStick.horizontal.state != 0.0

        onDirectionUpdate { xDir, yDir ->

            val currentInsideInnerCircle = xDir.pow(2) + yDir.pow(2) <= AIM_MELEE_RADIUS.pow(2)

            if (currentInsideInnerCircle) {
                if (isActionButtonPressed()) {
                    // Trigger combat arts
                    if (!longPressPending) {
                        longPressHandler.postDelayed({
                            if (isActionButtonPressed()) {
                                gamepad.rightTrigger.pressed = true
                                combatArtPending = true
                            }
                        }, RANGED_ART_DELAY_MS)
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
                        }, MELEE_ART_DELAY_MS)
                        longPressPending = true
                    }
                }
            } else {
                gamepad.rightCross.bottom.pressed = false
                gamepad.rightBumper.pressed = false

                gamepad.rightStick.set(xDir * AIM_AMPLIFICATION, yDir * AIM_AMPLIFICATION)

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
                    CombatLayout.handler.postDelayed({ gamepad.rightStick.reset() }, THROW_ART_AIM_HOLD_MS)
                } else {
                    gamepad.rightStick.reset()
                }
            } else {
                gamepad.rightCross.bottom.pressed = false

                // CrossCode expects a button press to fire, so we give it one
                fakeClick(gamepad.rightBumper) {
                    gamepad.rightStick.reset()
                }
            }
        }
    }
}
