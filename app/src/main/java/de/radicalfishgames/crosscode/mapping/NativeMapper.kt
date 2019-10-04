package de.radicalfishgames.crosscode.mapping

import android.view.KeyEvent.*
import android.view.MotionEvent.*
import android.view.View
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.R
import kotlinx.android.synthetic.main.native_layout.*


class NativeMapper(game: GameWrapper, private val controllerDeviceId: Int) : ControllerMapper(game) {

    override lateinit var containerView: View
    override val layoutResId = R.layout.combat_layout

    override fun initControls() {

        // Mapping according to https://developer.android.com/training/game-controllers/controller-input.html#button

        native_input_detector.apply {

            onButtonEvent.add { fromDeviceId, keycode, isPressed ->
                if(fromDeviceId != controllerDeviceId){
                    return@add
                }

                gamepad.apply {
                    when(keycode){

                        KEYCODE_DPAD_UP -> leftCross.top.pressed = isPressed
                        KEYCODE_DPAD_DOWN -> leftCross.bottom.pressed = isPressed
                        KEYCODE_DPAD_LEFT -> leftCross.left.pressed = isPressed
                        KEYCODE_DPAD_RIGHT -> leftCross.right.pressed = isPressed

                        KEYCODE_BUTTON_Y -> rightCross.top.pressed = isPressed
                        KEYCODE_BUTTON_A -> rightCross.bottom.pressed = isPressed
                        KEYCODE_BUTTON_X -> rightCross.left.pressed = isPressed
                        KEYCODE_BUTTON_B -> rightCross.right.pressed = isPressed

                        KEYCODE_BUTTON_L1 -> leftBumper.pressed = isPressed
                        KEYCODE_BUTTON_R1 -> rightBumper.pressed = isPressed

                        KEYCODE_BUTTON_L2 -> leftTrigger.pressed = isPressed
                        KEYCODE_BUTTON_R2 -> rightTrigger.pressed = isPressed

                        KEYCODE_BUTTON_SELECT,
                        KEYCODE_BACK -> selectOrBack.pressed = isPressed

                        KEYCODE_BUTTON_START,
                        KEYCODE_FORWARD -> startOrForward.pressed = isPressed

                    }
                }
            }

            onMotion.add { fromDeviceId, axisCode, axisState ->
                if(fromDeviceId != controllerDeviceId){
                    return@add
                }

                gamepad.apply {
                    when(axisCode){

                        // For some reason, DPad input might be given as an axis
                        AXIS_HAT_Y -> {
                            leftCross.top.pressed = axisState < 0
                            leftCross.bottom.pressed = axisState > 0
                        }
                        AXIS_HAT_X -> {
                            leftCross.left.pressed = axisState < 0
                            leftCross.right.pressed = axisState > 0
                        }

                        AXIS_X -> leftStick.horizontal.state = axisState.toDouble()
                        AXIS_Y -> leftStick.vertical.state = axisState.toDouble()

                        AXIS_Z -> rightStick.horizontal.state = axisState.toDouble()
                        AXIS_RZ -> rightStick.vertical.state = axisState.toDouble()

                        // For some other reason, trigger input can also be given as axis
                        AXIS_RTRIGGER -> rightTrigger.pressed = axisState > 0
                        AXIS_LTRIGGER -> leftTrigger.pressed = axisState > 0
                    }
                }
            }
        }

    }
}