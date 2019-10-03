package de.radicalfishgames.crosscode

import android.os.Handler
import android.view.View
import de.radicalfishgames.crosscode.overlay.ControlView


abstract class VirtualControllerMapper(game: GameWrapper) : ControllerMapper(game) {

    private val handler = Handler()

    protected fun bindSimpleButton(view: ControlView, to: GamepadState.Button){
        view.apply {
            onTouch.add {
                to.pressed = true
            }

            onRelease.add {
                to.pressed = false
            }
        }
    }

    protected fun fakeClick(button: GamepadState.Button){
        button.pressed = true

        handler.postDelayed({

            button.pressed = false

        }, FAKE_CLICK_TIME)
    }

    protected fun hideViews(vararg views: View){
        for(view in views){
            view.visibility = View.GONE
        }
    }

    protected fun showViews(vararg views: View){
        for(view in views){
            view.visibility = View.VISIBLE
        }
    }

    companion object {
        const val FAKE_CLICK_TIME = 20L
    }

}