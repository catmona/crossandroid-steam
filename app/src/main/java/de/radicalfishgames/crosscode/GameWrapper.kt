package de.radicalfishgames.crosscode

import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject


class GameWrapper(private val webView: WebView) {

    val gamepadState: GamepadState = GamepadState()

    var isGameReady = false
        private set

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

            val cacheDbPath = webView.context.applicationContext.getDir("cache_database", MODE_PRIVATE).path
            setAppCacheEnabled(true)
            setAppCachePath(cacheDbPath)

        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                onGameLoaded()
            }
        }

        // Disable clicking the game, since we have our own input method
        webView.setOnTouchListener { _, _ -> true }
    }

    fun loadGame() {

        webView.loadUrl("file:///android_asset/www/node-webkit.html")

    }


    fun onGameLoaded(){

        interceptJSGamepadFunc()
        updateJSGamepad()

        isGameReady = true
    }

    private fun interceptJSGamepadFunc(){
        webView.evaluateJavascript(
                """
                    var $JS_GAMEPADS_VAR = [];
                    $JS_GAMEPADS_FUNC = function(){
                        return $JS_GAMEPADS_VAR;
                    };
                """
                    .trimIndent()
                    .replace("\n", ""),
            null
        )
    }

    fun updateJSGamepad(){
        webView.evaluateJavascript(
            "$JS_GAMEPADS_VAR = [JSON.parse('${createGamepadJson(gamepadState)}')];",
            null
        )
    }

    private fun createGamepadJson(gamepad: GamepadState) : JSONObject{

        val jsonGamepad = JSONObject()

        // Mapping layout from https://w3c.github.io/gamepad/standard_gamepad.svg

        val axes = JSONArray().apply {
            put(0, gamepad.leftStick.horizontal)
            put(1, gamepad.leftStick.vertical)

            put(2, gamepad.rightStick.horizontal)
            put(3, gamepad.rightStick.vertical)
        }

        val buttons = JSONArray()
        buttons.apply {

            put(10, gamepad.leftStick.button.value)
            put(11, gamepad.rightStick.button.value)

            put(12, gamepad.leftCross.top.value)
            put(13, gamepad.leftCross.bottom.value)
            put(14, gamepad.leftCross.left.value)
            put(15, gamepad.leftCross.right.value)

            put(0, gamepad.rightCross.bottom.value)
            put(1, gamepad.rightCross.right.value)
            put(2, gamepad.rightCross.left.value)
            put(3, gamepad.rightCross.top.value)

            put(4, gamepad.leftBumper.value)
            put(5, gamepad.rightBumper.value)

            put(6, gamepad.leftTrigger.value)
            put(7, gamepad.rightTrigger.value)

            put(8, gamepad.selectOrBack.value)
            put(9, gamepad.startOrForward.value)
            put(16, gamepad.center.value)

        }

        jsonGamepad.apply {
            put("id", "Virtual Controller")
            put("index", 0)
            put("connected", true)
            put("timestamp", 0)
            put("mapping", "standard")
            put("axes", axes)
            put("buttons", buttons)
        }

        return jsonGamepad
    }

    companion object {
        const val JS_GAMEPADS_VAR = "injectedGamepads"
        const val JS_GAMEPADS_FUNC = "navigator.getGamepads"
    }
}