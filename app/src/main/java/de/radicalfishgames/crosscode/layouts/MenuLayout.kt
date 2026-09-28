package de.radicalfishgames.crosscode.layouts

import android.view.View
import de.radicalfishgames.crosscode.GamepadJsonBridge
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.databinding.MenuLayoutBinding
import de.radicalfishgames.crosscode.features.VirtualControllerFeature
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager

object MenuLayout : VirtualControllerLayout() {
    override val layoutResId = R.layout.menu_layout

    override fun bindControls(
        gamepad: GamepadJsonBridge,
        layoutView: View,
        feature: VirtualControllerFeature,
        eventManager: GameEventManager,
    ) {
        val binding = MenuLayoutBinding.bind(layoutView)

        binding.apply {
            bindOverlayToggle(overlayToggle, feature, CombatLayout)

            menuDirections.apply {
                onDirectionUpdate { xDir, yDir ->
                    gamepad.leftStick.set(snapToDirection(xDir), snapToDirection(yDir))
                }

                onRelease {
                    gamepad.leftStick.reset()
                }
            }

            // Short press switches tabs with the bumper, long press with the trigger
            bindTapOrHold(tabLeft, onTap = { fakeClick(gamepad.leftBumper) }, onHold = { fakeClick(gamepad.leftTrigger) })
            bindTapOrHold(tabRight, onTap = { fakeClick(gamepad.rightBumper) }, onHold = { fakeClick(gamepad.rightTrigger) })

            bindSimpleButton(rightcrossRight, gamepad.rightCross.right)
            bindSimpleButton(rightcrossLeft, gamepad.rightCross.left)
            bindSimpleButton(rightcrossTop, gamepad.rightCross.top)
            bindSimpleButton(rightcrossBottom, gamepad.rightCross.bottom)

            bindSimpleButton(helpButton, gamepad.startOrForward)
        }
    }

    // Menus move in whole steps, so each axis is either fully pressed or released
    private fun snapToDirection(value: Double) =
        when {
            value > FULL_DEFLECTION_THRESHOLD -> 1.0
            value < -FULL_DEFLECTION_THRESHOLD -> -1.0
            else -> 0.0
        }
}
