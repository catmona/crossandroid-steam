package de.radicalfishgames.crosscode

import android.annotation.SuppressLint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import de.radicalfishgames.crosscode.features.Feature
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager
import java.util.*
import kotlin.reflect.KClass


class GameWrapper(private val webView: WebView) {

    val features = LinkedList<Feature>()

    var blockWebViewClicks = true


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
        for(feature in features){
            feature.onPreGamePageLoad()
        }

        webView.loadUrl(fromLocation)
    }


    fun onPageLoaded(){

        Log.d("CrossCode", "Game page loaded.")

        Log.d("CrossCode", "Starting game.")

        startCrossCode()

        for(feature in features){
            feature.onPostGamePageLoad()
        }
    }

    fun <T : Feature> getFeature(ofType: KClass<T>): T {
        return features.first { ofType.isInstance(it) } as T
    }

    @SuppressLint("JavascriptInterface")
    fun exposeJSInterface(obj: Any, name:String) = webView.addJavascriptInterface(obj, name)

    private fun startCrossCode() = runJS("doStartCrossCodePlz()")

    fun onResume() = runJS("window.dispatchEvent(new FocusEvent(\"focus\"));")
    fun onPause() = runJS("window.dispatchEvent(new FocusEvent(\"blur\"));")

    fun runJS(js: String){
        webView.evaluateJavascript(js, null)
    }
}