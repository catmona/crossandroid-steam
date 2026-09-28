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
import de.radicalfishgames.crosscode.BuildConfig
import de.radicalfishgames.crosscode.features.Feature
import java.util.LinkedList

class GameWrapper(
    private val webView: WebView,
    val modLoaderPresent: Boolean,
    val gameFolderManager: GameFolderManager,
    private val hardwareAcceleration: Boolean = true,
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

        // Font fix, applied when the hardware acceleration setting is off: force CPU-backed 2D
        // canvases via willReadFrequently. GPU canvases corrupt alpha in getImageData (breaking
        // _loadMetrics glyph boundary detection) and bilinear-filter drawImage at non-integer
        // source coords despite imageSmoothingEnabled=false (blurring _drawChar).
        if (!hardwareAcceleration) {
            runJS(
                """(function(){
                    var orig=HTMLCanvasElement.prototype.getContext;
                    HTMLCanvasElement.prototype.getContext=function(type,attrs){
                        if(type==='2d') attrs=Object.assign({willReadFrequently:true},attrs||{});
                        return orig.call(this,type,attrs);
                    };
                })();""",
            )
        }

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
