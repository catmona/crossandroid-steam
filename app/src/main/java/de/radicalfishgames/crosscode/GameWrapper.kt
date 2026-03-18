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
        Log.d("CrossCode, ", "Initializing WebView")

        // Enable WebView debugging for debug builds
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        webView.setInitialScale(0)
        webView.isVerticalScrollBarEnabled = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Prevents screen turning bright from the focus effect when using a controller
            // on some devices.
            webView.defaultFocusHighlightEnabled = false
        }

        webView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false

            // Explicitly disable file access for security (suppress deprecation warnings)
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            allowFileAccess = false
            allowContentAccess = false
        }

        webView.webViewClient = webViewClient

        ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(
            object : ServiceWorkerClientCompat() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
                    webViewClient.handleRequest(request)
            },
        )

        // Log JavaScript console messages to logcat
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

        // Build ccmod asset index before loading the game
        webViewClient.buildCcmodAssetIndex()

        for (feature in features) {
            feature.onPreGamePageLoad()
        }

        // Load the game files, and also set a flag for the modloader to recognize that
        // it's being executed in CrossAndroid
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

        // Fix font rendering on Android WebView by forcing CPU-backed 2D canvases.
        // GPU-backed canvases in Android WebView cause font issues: getImageData returns
        // corrupted alpha values (breaking _loadMetrics character boundary detection), and
        // drawImage with non-integer source coordinates triggers bilinear filtering even with
        // imageSmoothingEnabled=false (breaking _drawChar font rendering).
        // CPU canvases handle both correctly via exact pixel readback and nearest-neighbor
        // sampling.
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

        // Nearest-neighbor scaling: use pixelated rendering to avoid bilinear blur
        // on the pixel art when the canvas is CSS-stretched to fill the screen.
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
