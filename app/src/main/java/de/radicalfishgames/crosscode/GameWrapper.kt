package de.radicalfishgames.crosscode

import android.annotation.SuppressLint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import de.radicalfishgames.crosscode.gamelisteners.GameEventListener
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager
import de.radicalfishgames.crosscode.mapping.ControllerMapper
import java.util.*


class GameWrapper(private val hostActivity: GameActivity, private val webView: WebView, private val interceptControllerInput: Boolean) {

    val gamepadState: GamepadJsonBridge = GamepadJsonBridge {updateJSGamepad()}
    val gameEvents = GameEventManager()

    var blockWebViewClicks = true


    private var isJSControllerReady = false


    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun initWebView() {

        Log.d("CrossCode, ", "Initializing WebView")

        webView.setInitialScale(0)
        webView.isVerticalScrollBarEnabled = false

        webView.settings.apply {

            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL

            domStorageEnabled = true
            allowUniversalAccessFromFileURLs = true
            mediaPlaybackRequiresUserGesture = false

        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                onPageLoaded()
            }
        }

        // Disable clicking the game, usually while custom controls are active
        webView.setOnTouchListener { view: View, motionEvent: MotionEvent ->
            // Allow ACTION_UP to prevent accidentally locking into aiming when switching this boolean
            blockWebViewClicks && motionEvent.action != MotionEvent.ACTION_UP
        }
    }

    fun loadGame(fromLocation: String) {
        exposeListenerInterfaces()
        webView.loadUrl(fromLocation)
    }


    fun onPageLoaded(){

        Log.d("CrossCode", "Game page loaded.")

        if(interceptControllerInput){
            Log.d("CrossCode", "Intercepting JS controller input...")

            interceptJSGamepadFunc()
            isJSControllerReady = true
            updateJSGamepad()
        }

        Log.d("CrossCode", "Starting game.")

        startCrossCode()

        injectListenerJS(gameEvents.listeners)
    }

    fun useControls(controls: ControllerMapper){
        Log.d("CrossCode", "Switching to virtual controller ${controls.javaClass.simpleName}")

        val view = hostActivity.loadControlLayout(controls.layoutResId)
        controls.containerView = view
        controls.initControls()
    }

    private fun exposeListenerInterfaces(){
        for(listener in gameEvents.listeners){
            webView.addJavascriptInterface(listener, listener.interfaceName)
        }
    }

    private fun injectListenerJS(listeners: List<GameEventListener>){

        Log.d("CrossCode", "Injecting JS listeners...")

        var requiredModulesJS = ""
        val modulesAddedAlready = LinkedList<String>()

        var observerRegistrationJS = ""
        val observerObjectsAddedAlready = LinkedList<String>()

        var eventIfsJS = ""

        for(listener in listeners){

            if(!modulesAddedAlready.contains(listener.requiredModuleName)){
                modulesAddedAlready.add(listener.requiredModuleName)

                requiredModulesJS += ".requires(\"${listener.requiredModuleName}\")"
            }

            if(!observerObjectsAddedAlready.contains(listener.eventObjectName)){
                observerObjectsAddedAlready.add(listener.eventObjectName)

                observerRegistrationJS += "sc.Model.addObserver(sc.${listener.eventObjectName}, this);"
            }

            eventIfsJS +=
                """
                    if(origin === sc.${listener.eventObjectName} && eventNumber === sc.${listener.eventType}) {
                        ${listener.onEventJS} 
                    }
                """
        }

        var jsForAllListeners =
        """
            ig.module("crossandroid.events.listeners")$requiredModulesJS.defines(function(){
                sc.CrossAndroidEventListener = ig.GameAddon.extend({
                    init: function() {
                        $observerRegistrationJS
                    },
                    
                    modelChanged(origin, eventNumber, optionalArgs){
                        $eventIfsJS
                    }
                });
                ig.addGameAddon(function() {
                    return sc.crossAndroidEventListener = new sc.CrossAndroidEventListener
                })
            })
        """

        jsForAllListeners = jsForAllListeners
            .trimIndent()
            .replace("\n", "")

        runJS(jsForAllListeners)
    }

    private fun interceptJSGamepadFunc(){
        runJS(
            """
                var $JS_GAMEPADS_VAR = [];
                $JS_GAMEPADS_FUNC = function(){
                    return $JS_GAMEPADS_VAR;
                };
            """
            .trimIndent()
            .replace("\n", "")
        )
    }

    private fun updateJSGamepad(){
        if(isJSControllerReady){
            runJS("$JS_GAMEPADS_VAR = [JSON.parse('${gamepadState.asJsonString}')];")
        }
    }

    private fun startCrossCode() = runJS("doStartCrossCodePlz()")

    fun onResume() = runJS("window.dispatchEvent(new FocusEvent(\"focus\"));")
    fun onPause() = runJS("window.dispatchEvent(new FocusEvent(\"blur\"));")

    private fun runJS(js: String){
        webView.evaluateJavascript(js, null)
    }

    companion object {
        const val JS_GAMEPADS_VAR = "injectedGamepads"
        const val JS_GAMEPADS_FUNC = "navigator.getGamepads"
    }
}