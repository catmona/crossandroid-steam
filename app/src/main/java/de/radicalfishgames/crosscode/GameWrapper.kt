package de.radicalfishgames.crosscode

import android.annotation.SuppressLint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import de.radicalfishgames.crosscode.mapping.ControllerMapper


class GameWrapper(private val hostActivity: GameActivity, private val webView: WebView, private val interceptControllerInput: Boolean) {

    val gamepadState: GamepadJsonBridge = GamepadJsonBridge {updateJSGamepad()}

    private var isJSControllerReady = false

    var blockWebViewClicks = true

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun initWebView() {

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
            blockWebViewClicks
        }
    }

    fun loadGame(fromLocation: String) {
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
    }

    fun useControls(controls: ControllerMapper){
        Log.d("CrossCode", "Switching to virtual controller ${controls.javaClass.simpleName}")

        val view = hostActivity.loadControlLayout(controls.layoutResId)
        controls.containerView = view
        controls.initControls()
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