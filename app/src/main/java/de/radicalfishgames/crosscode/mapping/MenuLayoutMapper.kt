package de.radicalfishgames.crosscode.mapping

import android.view.View
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.R
import kotlinx.android.synthetic.main.menu_layout.*


class MenuLayoutMapper(game: GameWrapper) : VirtualControllerMapper(game) {

    override lateinit var containerView: View
    override val layoutResId = R.layout.menu_layout

    override fun initControls() {

        overlay_toggle.apply {
            onRelease.add {
                game.useControls(CombatLayoutMapper(game))
            }
        }

        bindSimpleButton(leftcross_left, gamepad.leftCross.left)
        leftcross_left.apply {
            onTouch.add {
                gamepad.leftStick.horizontal.state = -1.0
            }
            onRelease.add {
                gamepad.leftStick.horizontal.state = 0.0
            }
        }

        bindSimpleButton(leftcross_right, gamepad.leftCross.right)
        leftcross_right.apply {
            onTouch.add {
                gamepad.leftStick.horizontal.state = 1.0
            }
            onRelease.add {
                gamepad.leftStick.horizontal.state = 0.0
            }
        }

        bindSimpleButton(leftcross_top, gamepad.leftCross.top)
        leftcross_top.apply {
            onTouch.add {
                gamepad.leftStick.vertical.state = -1.0
            }
            onRelease.add {
                gamepad.leftStick.vertical.state = 0.0
            }
        }

        bindSimpleButton(leftcross_bottom, gamepad.leftCross.bottom)
        leftcross_bottom.apply {
            onTouch.add {
                gamepad.leftStick.vertical.state = 1.0
            }
            onRelease.add {
                gamepad.leftStick.vertical.state = 0.0
            }
        }

        bindSimpleButton(rightcross_right, gamepad.rightCross.right)
        bindSimpleButton(rightcross_left, gamepad.rightCross.left)
        bindSimpleButton(rightcross_top, gamepad.rightCross.top)
        bindSimpleButton(rightcross_bottom, gamepad.rightCross.bottom)

        bindSimpleButton(tab_left, gamepad.leftBumper)
        bindSimpleButton(tab_right, gamepad.rightBumper)

        bindSimpleButton(help_button, gamepad.startOrForward)

    }
}