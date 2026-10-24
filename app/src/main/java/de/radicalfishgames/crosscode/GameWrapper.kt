package de.radicalfishgames.crosscode

import android.annotation.SuppressLint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import de.radicalfishgames.crosscode.BuildConfig
import de.radicalfishgames.crosscode.features.Feature
import java.util.LinkedList

class GameWrapper(
    private val webView: WebView,
    val modLoaderPresent: Boolean,
    val gameFolderManager: GameFolderManager,
    private val pixelatedScaling: Boolean = false,
) {
    val features = LinkedList<Feature>()

    var blockWebViewClicks = true

    private val handler = Handler(Looper.getMainLooper())

    private var calledPostGameLoad = false

    private val webViewClient = GameWebViewClient(this)

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun initWebView() {
        Log.d("CrossCode", "Initializing WebView")

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        webView.setInitialScale(0)
        webView.isVerticalScrollBarEnabled = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // On some devices the focus highlight brightens the screen when using a controller.
            webView.defaultFocusHighlightEnabled = false
        }

        webView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false

            // Everything is served through the virtual HTTPS origin, so local file access stays off.
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            allowFileAccess = false
            allowContentAccess = false
        }

        webView.webViewClient = webViewClient

        // Font fix: ig.Font._loadMetrics finds glyph boundaries by reading alpha=0 separator pixels
        // with getImageData. On some WebViews (e.g. Chrome 109 on Adreno) drawing an image into a
        // GPU-backed canvas shifts alpha by 1-2 (0 becomes 1), so boundaries are missed and the
        // wrong glyphs are drawn. Make only the canvases created during _loadMetrics CPU-backed.
        // Injected at document start, before the game defines ig.Font. The assignment of Font is
        // trapped through Object.prototype rather than window.ig, because loaders like CCLoader3
        // redefine window.ig themselves and would replace our trap.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                """(function(){
                    function patch(Font){
                        var loadMetrics=Font.prototype._loadMetrics;
                        Font.prototype._loadMetrics=function(){
                            var proto=HTMLCanvasElement.prototype,getContext=proto.getContext;
                            proto.getContext=function(type,attrs){
                                if(type==='2d') attrs=Object.assign({willReadFrequently:true},attrs);
                                return getContext.call(this,type,attrs);
                            };
                            try{return loadMetrics.apply(this,arguments);}finally{proto.getContext=getContext;}
                        };
                    }
                    // Any obj.Font=v without an own Font lands here; store it as the plain assignment would
                    Object.defineProperty(Object.prototype,'Font',{configurable:true,set:function(v){
                        Object.defineProperty(this,'Font',{value:v,writable:true,enumerable:true,configurable:true});
                        if(v&&v.prototype&&typeof v.prototype._loadMetrics==='function'){
                            delete Object.prototype.Font;
                            patch(v);
                            console.log('CrossAndroid: font fix applied');
                        }
                    }});
                })();""",
                setOf("*"),
            )
        }

        // Service worker fetches (used by CCLoader3) bypass the WebViewClient, so route them to it.
        ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(
            object : ServiceWorkerClientCompat() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
                    webViewClient.handleRequest(request)
            },
        )

        // Forward the JS console to logcat under the "CCLoader" tag.
        webView.webChromeClient =
            object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    val level =
                        when (consoleMessage.messageLevel()) {
                            ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
                            ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                            ConsoleMessage.MessageLevel.DEBUG -> Log.DEBUG
                            else -> Log.INFO
                        }
                    Log.println(
                        level,
                        "CCLoader",
                        "${consoleMessage.message()} [${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}]",
                    )
                    return true
                }
            }

        // Disable clicking the game, usually while custom controls are active
        webView.setOnTouchListener { view: View, motionEvent: MotionEvent ->
            // Allow ACTION_UP to prevent accidentally locking into aiming when switching this boolean
            blockWebViewClicks && motionEvent.action != MotionEvent.ACTION_UP
        }
    }

    fun loadGame(gameEntryPoint: String) {
        exposeJSInterface(this, "CrossAndroid")

        webViewClient.buildCcmodAssetIndex()

        for (feature in features) {
            feature.onPreGamePageLoad()
        }

        // The crossandroid=true query lets the modloader detect it is running in CrossAndroid.
        webView.loadUrl(
            webViewClient
                .buildVirtualUrl()
                .path(gameEntryPoint)
                .encodedQuery("crossandroid=true")
                .build()
                .toString(),
        )
    }

    fun onPageLoaded() {
        Log.d("CrossCode", "Game page loaded.")

        // Smooth frame pacing: ig.Timer.step uses Date.now(), which jitters by several ms per frame
        // in WebView, so scrolling stutters even at a steady 60 fps. Drive the game clock from the
        // vsync-aligned document.timeline instead; a rAF hook won't do because the game loop
        // doesn't always run through requestAnimationFrame (e.g. during loading).
        runJS(
            """(function(){
                var iv=setInterval(function(){
                    if(typeof ig==='undefined'||!ig.Timer||!ig.Timer.step)return;
                    clearInterval(iv);
                    ig.Timer._last=document.timeline.currentTime;
                    ig.Timer.step=function(){
                        var now=document.timeline.currentTime;
                        ig.Timer.time+=Math.min((now-ig.Timer._last)/1E3,ig.Timer.maxStep)*ig.Timer.timeScale;
                        ig.Timer._last=now;
                    };
                },100);
            })();""",
        )

        // Avoid bilinear blur on the pixel art when the canvas is CSS-stretched to fill the screen.
        if (pixelatedScaling) {
            runJS(
                """(function(){
                    var iv=setInterval(function(){
                        if(typeof ig==='undefined'||!ig.system)return;
                        clearInterval(iv);
                        ig.system.canvas.style.imageRendering='pixelated';
                    },100);
                })();""",
            )
        }

        if (!modLoaderPresent) {
            Log.d("CrossCode", "Starting game manually.")

            startCrossCodeManually()

            executePostGameLoad()
        } else {
            Log.d("CrossCode", "Modloader is present, it will start the game for us.")
        }
    }

    /** Called by the modloader from JS (or directly when unmodded) once the game has started; runs once. */
    @JavascriptInterface
    fun executePostGameLoad() {
        if (calledPostGameLoad) {
            return
        }
        calledPostGameLoad = true

        handler.post {
            for (feature in features) {
                feature.onPostGamePageLoad()
            }
        }
    }

    inline fun <reified T : Feature> getFeature(): T = features.filterIsInstance<T>().first()

    @SuppressLint("JavascriptInterface")
    fun exposeJSInterface(
        obj: Any,
        name: String,
    ) = webView.addJavascriptInterface(obj, name)

    private fun startCrossCodeManually() = runJS("doStartCrossCodePlz()")

    fun onResume() = runJS("window.dispatchEvent(new FocusEvent(\"focus\"));")

    fun onPause() = runJS("window.dispatchEvent(new FocusEvent(\"blur\"));")

    fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        for (feature in features) {
            feature.onDestroy()
        }
        webViewClient.closeCcmodManager()
    }

    fun runJS(
        js: String,
        callback: ValueCallback<String>? = null,
    ) {
        webView.evaluateJavascript(js, callback)
    }
}
