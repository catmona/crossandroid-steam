package de.radicalfishgames.crosscode.layouts

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import de.radicalfishgames.crosscode.GamepadJsonBridge
import de.radicalfishgames.crosscode.features.VirtualControllerFeature
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager
import de.radicalfishgames.crosscode.overlay.ControlView
import de.radicalfishgames.crosscode.overlay.SwipeControl

abstract class VirtualControllerLayout {
    abstract val layoutResId: Int
    open val allowWebViewInteraction = false

    protected val handler = Handler(Looper.getMainLooper())

    abstract fun bindControls(
        gamepad: GamepadJsonBridge,
        layoutView: View,
        feature: VirtualControllerFeature,
        eventManager: GameEventManager,
    )

    protected fun bindSimpleButton(
        view: ControlView,
        to: GamepadJsonBridge.Button,
    ) {
        view.apply {
            onTouch {
                to.pressed = true
            }

            onRelease {
                to.pressed = false
            }
        }
    }

    /** Horizontal swipes switch to [horizontalTarget], vertical ones to [DirectLayout]. */
    protected fun bindOverlayToggle(
        toggle: SwipeControl,
        feature: VirtualControllerFeature,
        horizontalTarget: VirtualControllerLayout,
    ) {
        toggle.onSwipe { right, left, up, down ->
            // Do in handler to avoid a crash due to the potential layout changes
            handler.post {
                if (right || left) {
                    feature.switchToLayout(horizontalTarget)
                } else if (up || down) {
                    feature.enterDirect()
                }
            }
        }
    }

    /** Runs [onTap] on a short press or [onHold] on one longer than [HOLD_THRESHOLD_MS], on release. */
    protected fun bindTapOrHold(
        view: ControlView,
        onTap: () -> Unit,
        onHold: () -> Unit,
    ) {
        var pressedAt = 0L
        view.onTouch { pressedAt = SystemClock.uptimeMillis() }
        view.onRelease {
            if (SystemClock.uptimeMillis() - pressedAt > HOLD_THRESHOLD_MS) onHold() else onTap()
        }
    }

    protected fun fakeClick(button: GamepadJsonBridge.Button) = fakeClick(button) {}

    protected fun fakeClick(
        button: GamepadJsonBridge.Button,
        callback: () -> Unit,
    ) {
        button.pressed = true

        handler.postDelayed({
            button.pressed = false
            callback.invoke()
        }, FAKE_CLICK_TIME)
    }

    companion object {
        const val FAKE_CLICK_TIME = 40L
        const val HOLD_THRESHOLD_MS = 200L

        // Normalized pad deflection beyond which a direction counts as fully pressed
        const val FULL_DEFLECTION_THRESHOLD = 0.15
    }
}
