package de.radicalfishgames.crosscode.features

import android.util.Log
import android.view.View
import android.view.ViewGroup
import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.GamepadJsonBridge
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager
import de.radicalfishgames.crosscode.mapping.CombatLayoutMapper
import de.radicalfishgames.crosscode.mapping.MenuLayoutMapper
import de.radicalfishgames.crosscode.mapping.VirtualControllerMapper
import kotlinx.android.synthetic.main.activity_game.*
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass


class VirtualControllerFeature(gameWrapper: GameWrapper,
                               hostActivity: GameActivity
) : Feature(gameWrapper, hostActivity) {

    internal val enabled = !hostActivity.isNativeControllerActive()

    private val gamepadState: GamepadJsonBridge = GamepadJsonBridge {updateGamepadJS()}

    // Controller layout instance store so we don't have to initialize a new one with every change
    internal val controllerInstances = setOf(
        CombatLayoutMapper(this, gamepadState),
        MenuLayoutMapper(this, gamepadState)
    )

    lateinit var currentController: VirtualControllerMapper
        private set

    private var isControllerReady = false


    override fun onPostGamePageLoad() {
        if(!enabled){
            return
        }

        // Hook the getGamepads-function to only return our gamepad
        runJS(
            """
                var $JS_GAMEPADS_VAR = [];
                navigator.getGamepads = function(){
                    return $JS_GAMEPADS_VAR;
                };
            """
                .trimIndent()
                .replace("\n", "")
        )

        initializeControllers()
        currentController = getControllerInstance(CombatLayoutMapper::class)
        currentController.containerView.visibility = View.VISIBLE

        isControllerReady = true
    }

    private fun initializeControllers(){
        for(controller in controllerInstances){

            val parentView = hostActivity.layoutInflater.inflate(controller.layoutResId, hostActivity.control_container) as ViewGroup
            val view = parentView.getChildAt(parentView.childCount - 1)

            // Hide all controllers by default and only show them when they are needed
            view.visibility = View.GONE
            controller.containerView = view
            controller.initControls()

            controller.registerGameEventListeners(gameWrapper.getFeature(GameEventManager::class))

        }
    }

    fun switchToLayout(newLayout: KClass<out VirtualControllerMapper>){
        if(!enabled){
            return
        }
        Log.d("CrossCode", "Switching to virtual controller ${newLayout.java.simpleName}")

        currentController.containerView.visibility = View.GONE
        currentController = getControllerInstance(newLayout)
        currentController.containerView.visibility = View.VISIBLE
    }

    private fun getControllerInstance(controllerType: KClass<out VirtualControllerMapper>): VirtualControllerMapper{
        // Reuse already initialized instances

        for(instance in controllerInstances){
            if(controllerType.isInstance(instance)){
                return instance
            }
        }

        throw IllegalArgumentException("Requested unknown virtual controller type ${controllerType.simpleName}")
    }

    private fun updateGamepadJS(){
        if(!enabled){
            return
        }

        if(isControllerReady){
            val jsonGamepad = gamepadState.asJsonString

            runJS("$JS_GAMEPADS_VAR = [JSON.parse('$jsonGamepad')];")
        }
    }

    internal fun disableWebViewInteraction(){
        gameWrapper.blockWebViewClicks = true
    }
    
    internal fun enableWebViewInteraction(){
        gameWrapper.blockWebViewClicks = false
    }

    companion object {
        const val JS_GAMEPADS_VAR = "injectedGamepads"
    }
}