package de.radicalfishgames.crosscode.mapping

import android.graphics.BitmapFactory
import android.view.View
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.R
import kotlinx.android.synthetic.main.combat_layout.*
import kotlinx.android.synthetic.main.menu_layout.*
import kotlinx.android.synthetic.main.menu_layout.overlay_toggle


class MenuLayoutMapper(game: GameWrapper) : VirtualControllerMapper(game) {

    override lateinit var containerView: View
    override val layoutResId = R.layout.menu_layout

    override fun initControls() {

        overlay_toggle.apply {

            val viewsToHide = arrayOf(leftcross_left, leftcross_right, leftcross_top, leftcross_bottom,
                rightcross_right, rightcross_left, rightcross_top, rightcross_bottom,
                tab_left, tab_right, help_button)

            onSwipe.add { right, left, up, down ->
                if(right || left){
                    game.blockWebViewClicks = true
                    game.useControls(CombatLayoutMapper(game))

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


        tab_left.apply {
            var startedToPressAt: Long = 0
            onTouch.add {
                startedToPressAt = System.currentTimeMillis()
            }

            onRelease.add {

                val timeDiff = (System.currentTimeMillis() - startedToPressAt)

                // Decide based on long- or short click whether to press bumper or trigger
                val useTrigger = timeDiff > (0.2 * 1000)

                if(useTrigger){
                    fakeClick(gamepad.leftTrigger)

                }else{
                    fakeClick(gamepad.leftBumper)
                }
            }
        }
        tab_right.apply {
            var startedToPressAt: Long = 0
            onTouch.add {
                startedToPressAt = System.currentTimeMillis()
            }

            onRelease.add {

                val timeDiff = (System.currentTimeMillis() - startedToPressAt)

                // Decide based on long- or short click whether to press bumper or trigger
                val useTrigger = timeDiff > (0.2 * 1000)

                if(useTrigger){
                    fakeClick(gamepad.rightTrigger)

                }else{
                    fakeClick(gamepad.rightBumper)
                }
            }
        }

        bindSimpleButton(rightcross_right, gamepad.rightCross.right)
        bindSimpleButton(rightcross_left, gamepad.rightCross.left)
        bindSimpleButton(rightcross_top, gamepad.rightCross.top)
        bindSimpleButton(rightcross_bottom, gamepad.rightCross.bottom)

        bindSimpleButton(help_button, gamepad.startOrForward)
    }
}