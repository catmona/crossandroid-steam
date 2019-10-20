package de.radicalfishgames.crosscode.layouts

import android.view.View
import de.radicalfishgames.crosscode.GamepadJsonBridge
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.features.VirtualControllerFeature
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager
import kotlinx.android.synthetic.main.direct_layout.view.*


object DirectLayout : VirtualControllerLayout() {

    override val layoutResId = R.layout.direct_layout
    override val allowWebViewInteraction = true

    lateinit var layoutToEnable: VirtualControllerLayout

    override fun bindControls(
        gamepad: GamepadJsonBridge,
        layoutView: View,
        feature: VirtualControllerFeature,
        eventManager: GameEventManager
    ) {
        layoutView.apply {

            overlay_toggle.apply {
                onRelease.add {
                    // Do in handler to avoid a crash due to the potential layout changes
                    handler.post {
                        feature.switchToLayout(layoutToEnable)
                    }
                }
            }

        }
    }
}