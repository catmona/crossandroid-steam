package de.radicalfishgames.crosscode

import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject


class GameWrapper(private val webView: WebView) {

    val gamepadState: GamepadState = GamepadState {updateJSGamepad()}

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

    fun loadGame(fromLocation: String) {

        webView.loadUrl(fromLocation)

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

    private fun updateJSGamepad(){
        if(isGameReady){
            webView.evaluateJavascript(
                "$JS_GAMEPADS_VAR = [JSON.parse('${gamepadState.asJsonString}')];",
                null
            )
        }
    }

    companion object {
        const val JS_GAMEPADS_VAR = "injectedGamepads"
        const val JS_GAMEPADS_FUNC = "navigator.getGamepads"
    }
}