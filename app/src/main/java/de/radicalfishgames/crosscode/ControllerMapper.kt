package de.radicalfishgames.crosscode

import android.view.View
import kotlinx.android.extensions.LayoutContainer


abstract class ControllerMapper(protected val game: GameWrapper): LayoutContainer {

    protected val gamepad = game.gamepadState
    abstract override var containerView: View

    abstract val layoutResId: Int

    abstract fun initControls()
    
}