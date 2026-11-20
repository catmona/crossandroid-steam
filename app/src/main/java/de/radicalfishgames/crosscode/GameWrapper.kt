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
    private val gaplessMusic: Boolean = true,
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
        //
        // Gapless music (preference, on by default): the engine's platform check never detects Android, so it treats us as a
        // browser and loops music with two <audio> elements, restarting the idle one 0.2s before
        // the loop point. Starting an <audio> element takes ~0.7s on Android (seconds on some
        // devices), which is silence on tracks that end at their loop point. Force the Web Audio
        // track (as on desktop), which schedules loops sample-accurately. Every launcher sets
        // IG_WEB_AUDIO_BGM=false before the game runs, so pin it with a setter that ignores writes.
        // Its loop math must also use the real audio clock: the engine pads getCurrentTime() while
        // the clock stalls (e.g. the audio output restarts), but schedules loops on the real one.
        // Decoded tracks take ~23MB per minute, and the engine keeps unreferenced ones for 5 cache
        // cleanups (map loads and some menu exits), which can reach several hundred MB. Free them
        // at the first cleanup instead, unless they are still on the music stack (e.g. area music
        // paused under a cutscene track), where freeing would silence them for good.
        //
        // Injected at document start, before the game defines its classes. The assignments are
        // trapped through Object.prototype rather than window.ig, because loaders like CCLoader3
        // redefine window.ig themselves and would replace our trap.
        val gaplessMusicJs =
            if (gaplessMusic) {
                """
                    Object.defineProperty(window,'IG_WEB_AUDIO_BGM',{get:function(){return true;},set:function(){}});
                    function onMusicStack(track){
                        var m=ig.music,on=function(e){return !!e&&e.track===track;};
                        return !!m&&(on(m.currentTrack)||on(m.inBetweenTrack)||m.trackStack.some(on));
                    }
                    trap('TrackWebAudio','_initAudioBuffers',function(proto){
                        var initAudioBuffers=proto._initAudioBuffers;
                        proto._initAudioBuffers=function(){
                            initAudioBuffers.apply(this,arguments);
                            var shared=this._context;
                            if(shared&&!shared.rawClock){
                                this._context=Object.create(shared,{
                                    rawClock:{value:true},
                                    getCurrentTime:{value:function(){return shared.getCurrentTimeRaw();}},
                                });
                            }
                        };
                        // Only increaseRef (0) and ig.cleanCache (count+1, freed above 5) write this
                        Object.defineProperty(proto,'emptyMapChangeCount',{
                            get:function(){return this._emptyCleanups||0;},
                            set:function(v){this._emptyCleanups=v>0&&!onMusicStack(this)?6:v;},
                        });
                    },'gapless music');
                """
            } else {
                ""
            }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                """(function(){
                    // The game runs in the top frame; keep the traps off Object.prototype in iframes,
                    // where they'd never be removed. (Removing them on load is not an option: CCLoader3
                    // only loads the game after the load event.)
                    if(window!==window.top) return;
                    // Any obj[name]=v without an own property lands here; store it as the plain assignment would
                    function trap(name,method,patch,log){
                        Object.defineProperty(Object.prototype,name,{configurable:true,set:function(v){
                            Object.defineProperty(this,name,{value:v,writable:true,enumerable:true,configurable:true});
                            if(v&&v.prototype&&typeof v.prototype[method]==='function'){
                                delete Object.prototype[name];
                                patch(v.prototype);
                                console.log('CrossAndroid: '+log+' applied');
                            }
                        }});
                    }
                    trap('Font','_loadMetrics',function(proto){
                        var loadMetrics=proto._loadMetrics;
                        proto._loadMetrics=function(){
                            var canvas=HTMLCanvasElement.prototype,getContext=canvas.getContext;
                            canvas.getContext=function(type,attrs){
                                if(type==='2d') attrs=Object.assign({willReadFrequently:true},attrs);
                                return getContext.call(this,type,attrs);
                            };
                            try{return loadMetrics.apply(this,arguments);}finally{canvas.getContext=getContext;}
                        };
                    },'font fix');
                    $gaplessMusicJs
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

    /** Runs [js] in every page before its own scripts; needs WebViewFeature.DOCUMENT_START_SCRIPT. */
    fun addDocumentStartScript(js: String) {
        WebViewCompat.addDocumentStartJavaScript(webView, js, setOf("*"))
    }

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
