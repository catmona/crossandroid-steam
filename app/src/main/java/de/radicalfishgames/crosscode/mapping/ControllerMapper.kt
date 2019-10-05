package de.radicalfishgames.crosscode.mapping

import android.view.View
import de.radicalfishgames.crosscode.GameWrapper
import kotlinx.android.extensions.LayoutContainer


abstract class ControllerMapper(protected val game: GameWrapper): LayoutContainer {

    protected val gamepad = game.gamepadState
    override lateinit var containerView: View

    abstract val layoutResId: Int

    abstract fun initControls()

}