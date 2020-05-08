package de.radicalfishgames.crosscode

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.*
import de.radicalfishgames.crosscode.features.Feature
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.*
import kotlin.reflect.KClass


class GameWrapper(private val webView: WebView, private val modLoaderPresent: Boolean = false, private val gameDir: String? = null) {

    val features = LinkedList<Feature>()

    var blockWebViewClicks = true

    private val handler = Handler()

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

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                if(modLoaderPresent && request != null && view != null){

                    var uri = request.url
                    if(!uri.path.toString().startsWith("/mnt")){
                        uri = Uri.parse("file:///$gameDir${uri.path}")
                    }

                    var mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri.toString()))
                    if(uri.toString().endsWith(".js")){
                        mimeType = "application/javascript"
                    }

                    if(!File(uri.path!!).exists()){
                        Log.w("CrossAndroid", "Path $uri not found!")
                        return null
                    }

                    return WebResourceResponse(mimeType, "utf-8", view.context.contentResolver.openInputStream(uri)!!)
                }
                return null
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

        if(!modLoaderPresent){
            Log.d("CrossCode", "Starting game manually.")

            startCrossCodeManually()

            executePostGameLoad()

        }else{
            Log.d("CrossCode", "Modloader is present, it will start the game for us.")

            exposeJSInterface(this, "CrossAndroid")
        }
    }

    @JavascriptInterface
    fun executePostGameLoad(){
        handler.post {
            if(modLoaderPresent){
                // Restore access to variables that the event listeners, controls etc. require
                // These would otherwise be "hidden away" within the iframe
                runJS("""
                    let ig = frame.contentWindow.ig;
                    let sc = frame.contentWindow.sc;
                    let navigator = frame.contentWindow.navigator;
                """.trimIndent())
            }

            for(feature in features){
                feature.onPostGamePageLoad()
            }
        }
    }

    fun <T : Feature> getFeature(ofType: KClass<T>): T {
        return features.first { ofType.isInstance(it) } as T
    }

    @SuppressLint("JavascriptInterface")
    fun exposeJSInterface(obj: Any, name:String) = webView.addJavascriptInterface(obj, name)

    private fun startCrossCodeManually() = runJS("doStartCrossCodePlz()")

    fun onResume() = runJS("window.dispatchEvent(new FocusEvent(\"focus\"));")
    fun onPause() = runJS("window.dispatchEvent(new FocusEvent(\"blur\"));")

    fun runJS(js: String, callback: ValueCallback<String>? = null){
        webView.evaluateJavascript(js, callback)
    }
}