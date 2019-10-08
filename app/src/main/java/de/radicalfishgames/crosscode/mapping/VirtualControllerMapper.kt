package de.radicalfishgames.crosscode.mapping

import android.graphics.BitmapFactory
import android.os.Handler
import android.view.View
import de.radicalfishgames.crosscode.GamepadJsonBridge
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.features.VirtualControllerFeature
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager
import de.radicalfishgames.crosscode.overlay.ControlView
import de.radicalfishgames.crosscode.overlay.SwipeControl
import kotlinx.android.extensions.LayoutContainer


abstract class VirtualControllerMapper(protected val feature: VirtualControllerFeature, protected val gamepad: GamepadJsonBridge): LayoutContainer {

    override lateinit var containerView: View
    abstract val layoutResId: Int

    private var isOverlayHidden = false
    internal abstract val overlayToggle: SwipeControl
    internal abstract val overlayViews: Array<View>

    private val handler = Handler()


    open fun initControls(){
        overlayToggle.apply {

            onSwipe.add { right, left, up, down ->
                // Do in handler to avoid a crash due to the potential layout changes
                handler.post {

                    if(right || left){
                        enableOverlay()

                        if(feature.currentController is CombatLayoutMapper){
                            feature.switchToLayout(MenuLayoutMapper::class)

                        }else if(feature.currentController is MenuLayoutMapper){
                            feature.switchToLayout(CombatLayoutMapper::class)
                        }

                    }else if(up || down){

                        if(isOverlayHidden){
                            enableOverlay()
                        }else{
                            disableOverlay()
                        }
                    }
                }
            }
        }
    }


    open fun registerGameEventListeners(eventManager: GameEventManager){ }

    fun disableOverlay(){
        isOverlayHidden = true

        feature.enableWebViewInteraction()

        overlayToggle.imageBitmap = BitmapFactory.decodeResource(
            overlayToggle.context.resources,
            R.drawable.overlay_off
        )
        hideViews(*overlayViews)

        overlayToggle.invalidate()
    }

    fun enableOverlay(){
        isOverlayHidden = false

        feature.disableWebViewInteraction()

        overlayToggle.imageBitmap = BitmapFactory.decodeResource(
            overlayToggle.context.resources,
            R.drawable.overlay_on
        )
        showViews(*overlayViews)

        overlayToggle.invalidate()
    }

    // Utility functions

    protected fun bindSimpleButton(view: ControlView, to: GamepadJsonBridge.Button){
        view.apply {
            onTouch.add {
                to.pressed = true
            }

            onRelease.add {
                to.pressed = false
            }
        }
    }

    protected fun fakeClick(button: GamepadJsonBridge.Button){
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