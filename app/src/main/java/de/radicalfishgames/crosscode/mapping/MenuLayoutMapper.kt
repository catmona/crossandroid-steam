package de.radicalfishgames.crosscode.mapping

import android.view.View
import de.radicalfishgames.crosscode.GamepadJsonBridge
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.features.VirtualControllerFeature
import de.radicalfishgames.crosscode.overlay.SwipeControl
import kotlinx.android.synthetic.main.menu_layout.*


class MenuLayoutMapper(feature: VirtualControllerFeature,
                       gamepad: GamepadJsonBridge
) : VirtualControllerMapper(feature, gamepad) {

    override val layoutResId = R.layout.menu_layout

    override lateinit var overlayToggle: SwipeControl
    override lateinit var overlayViews: Array<View>

    override fun initControls() {

        overlayToggle = overlay_toggle

        overlayViews = arrayOf(menu_directions,
            rightcross_right, rightcross_left, rightcross_top, rightcross_bottom,
            tab_left, tab_right, help_button)


        menu_directions.apply {
            onDirectionUpdate.add { xDir, yDir ->

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
                gamepad.leftStick.vertical.state = 0.0
                gamepad.leftStick.horizontal.state = 0.0
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

        super.initControls()
    }
}