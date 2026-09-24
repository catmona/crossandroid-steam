package de.radicalfishgames.crosscode.features

import android.app.UiModeManager
import android.content.res.Configuration
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.GamepadJsonBridge
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager
import de.radicalfishgames.crosscode.layouts.CombatLayout
import de.radicalfishgames.crosscode.layouts.DirectLayout
import de.radicalfishgames.crosscode.layouts.MenuLayout
import de.radicalfishgames.crosscode.layouts.VirtualControllerLayout

class VirtualControllerFeature(
    gameWrapper: GameWrapper,
    hostActivity: GameActivity,
) : Feature(gameWrapper, hostActivity) {
    internal val gamepadState: GamepadJsonBridge = GamepadJsonBridge { scheduleGamepadUpdate() }

    private val forceVirtualController = hostActivity.preferences.getBoolean("force_virtual", false)
    private val combatAlwaysSprint = hostActivity.preferences.getBoolean("always_sprint", true)
    private val dashOnlyOnTap = hostActivity.preferences.getBoolean("disable_swipe_dash", false)

    private val availableLayouts = setOf(CombatLayout, MenuLayout, DirectLayout)
    lateinit var currentLayout: VirtualControllerLayout
        private set

    private val isTelevision =
        hostActivity
            .getSystemService(UiModeManager::class.java)
            .currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

    internal val enabled = !isTelevision && (!hostActivity.isNativeControllerActive() || forceVirtualController)

    val layoutViews = HashMap<VirtualControllerLayout, ViewGroup>()

    private var isControllerReady = false

    // Input events can arrive far faster than the display refreshes and each one changes several
    // buttons/axes, so coalesce pushes to at most one evaluateJavascript per frame
    private var gamepadUpdatePending = false
    private val gamepadFrameCallback =
        Choreographer.FrameCallback {
            gamepadUpdatePending = false
            updateGamepadJS()
        }

    override fun onPostGamePageLoad() {
        // Always inject the getGamepads hook — for virtual overlay AND physical controllers.
        // WebView's built-in Gamepad API has broken button mapping on some devices,
        // so we always route through our own GamepadJsonBridge.
        runJS(
            """
            var $JS_GAMEPADS_VAR = [];
            navigator.getGamepads = function(){
                return $JS_GAMEPADS_VAR;
            };
            """.trimIndent()
                .replace("\n", ""),
        )

        isControllerReady = true

        // Push initial gamepad state so the game detects a controller immediately
        updateGamepadJS()

        if (!enabled) {
            return
        }

        CombatLayout.alwaysSprint = combatAlwaysSprint
        CombatLayout.dashOnlyOnTap = dashOnlyOnTap

        initializeLayouts()

        currentLayout = DirectLayout
        DirectLayout.layoutToEnable = MenuLayout
        gameWrapper.blockWebViewClicks = false
        layoutViews[DirectLayout]!!.visibility = View.VISIBLE
    }

    private fun initializeLayouts() {
        for (controller in availableLayouts) {
            val parentView = hostActivity.layoutInflater.inflate(controller.layoutResId, hostActivity.binding.controlContainer) as ViewGroup
            parentView.visibility = View.VISIBLE

            val view = parentView.getChildAt(parentView.childCount - 1) as ViewGroup

            // Hide all controllers by default and only show them when they are needed
            view.visibility = View.GONE
            layoutViews[controller] = view

            controller.bindControls(gamepadState, view, this, gameWrapper.getFeature<GameEventManager>())
        }
    }

    fun switchToLayout(newLayout: VirtualControllerLayout) {
        if (!enabled) {
            return
        }
        Log.d("CrossCode", "Switching to virtual controller ${newLayout.javaClass.simpleName}")

        layoutViews[currentLayout]!!.visibility = View.GONE
        currentLayout = newLayout
        layoutViews[currentLayout]!!.visibility = View.VISIBLE

        gameWrapper.blockWebViewClicks = !currentLayout.allowWebViewInteraction
    }

    private fun scheduleGamepadUpdate() {
        if (!gamepadUpdatePending) {
            gamepadUpdatePending = true
            Choreographer.getInstance().postFrameCallback(gamepadFrameCallback)
        }
    }

    private fun updateGamepadJS() {
        if (isControllerReady) {
            val jsonGamepad = gamepadState.asJsonString

            runJS("$JS_GAMEPADS_VAR = [JSON.parse('$jsonGamepad')];")
        }
    }

    override fun onDestroy() {
        Choreographer.getInstance().removeFrameCallback(gamepadFrameCallback)
    }

    companion object {
        const val JS_GAMEPADS_VAR = "injectedGamepads"
    }
}
