package de.radicalfishgames.crosscode

import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale

class GameWebViewClient(
    private val gameWrapper: GameWrapper,
) : WebViewClient() {
    private val gameDir = gameWrapper.gameFolderManager.getGameDir()
    private val canonicalGameDir: String? =
        try {
            gameDir?.canonicalPath
        } catch (_: IOException) {
            null
        }
    private val ccmodManager = CcmodManager()

    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        gameWrapper.onPageLoaded()
    }

    // Serving the game from a virtual HTTPS origin instead of file:// is what makes it work:
    // - ES6 imports require a correct MIME type, which file:// responses lack, so we set our own.
    // - A real-looking HTTPS origin enables fetch(), the normal same-origin policy and service workers.
    // No server exists; every request to the virtual domain is answered here. See:
    // https://developer.android.com/guide/webapps/load-local-content
    // https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        if (view == null || request == null) return null
        return handleRequest(request)
    }

    internal fun handleRequest(request: WebResourceRequest): WebResourceResponse? {
        if (gameDir == null) {
            Log.e("CrossCode", "gameDir is null, cannot serve files!")
            return null
        }

        val url = request.url
        val urlPath = url.path
        // CCLoader3's service worker fetches mod assets via https://mods/<mod>.ccmod/... URLs.
        // Intercept those in addition to the normal virtual origin so we can serve them from ZIPs.
        val isVirtualOrigin = url.scheme == VIRTUAL_ORIGIN_SCHEME && url.authority == VIRTUAL_ORIGIN_DOMAIN
        val isModsScheme = url.scheme == VIRTUAL_ORIGIN_SCHEME && url.authority == "mods"
        if ((!isVirtualOrigin && !isModsScheme) || urlPath == null) {
            return null
        }

        // For https://mods/<mod>.ccmod/... prepend "mods/" so the existing assets/ fallback
        // resolves it to assets/mods/<mod>.ccmod/... where the ZIP file actually lives.
        val normalizedPath = if (isModsScheme) "mods/${urlPath.trimStart('/')}" else urlPath.trimStart('/')
        val initialCcmodPath = CcmodManager.parseCcmodPath(File(gameDir, normalizedPath).canonicalPath)
        if (initialCcmodPath != null) {
            // If the .ccmod isn't at the resolved path, try:
            // 1. Dropping "ccloader/": CCLoader resolves paths from ccloader/, but mods live in assets/mods/.
            // 2. Prepending "assets/": the service worker may omit it.
            var resolved: Pair<String, String> = initialCcmodPath
            if (!File(resolved.first).exists() && normalizedPath.startsWith("ccloader/")) {
                tryCcmodFallback(normalizedPath.removePrefix("ccloader/"))?.let { resolved = it }
            }
            if (!File(resolved.first).exists() && !normalizedPath.startsWith("assets/")) {
                tryCcmodFallback("assets/$normalizedPath")?.let { resolved = it }
            }
            val canonicalCcmod = resolved.first
            if (canonicalGameDir == null || !canonicalCcmod.startsWith("$canonicalGameDir/")) {
                Log.w("CrossCode", "Path traversal rejected: $canonicalCcmod")
                return WebResourceResponse(null, null, null)
            }
            return handleCcmodRequest(request, resolved.first, resolved.second, normalizedPath)
        }

        // Check mod assets first so mods can override game assets. This also covers service worker
        // pass-through requests that lack the "assets/" prefix (e.g. /media/face/x.png).
        val assetPath = normalizedPath.removePrefix("assets/")
        val ccmodStream = ccmodManager.getAssetStream(assetPath)
        if (ccmodStream != null) {
            Log.d("CrossCode", "Serving asset from ccmod: $assetPath")
            val extension = assetPath.substringAfterLast('.', "")
            return WebResourceResponse(FallbackMimeTypeMap.resolveMimeType(extension), null, ccmodStream)
        }

        // CCLoader3's service worker passes through requests like "/data/changelog.json" whose files
        // live under "assets/", so retry with that prefix when the direct path doesn't exist.
        val (requestedFile, effectiveNormalizedPath) =
            try {
                val directFile = File(gameDir, normalizedPath).canonicalFile
                if (!directFile.exists() && !normalizedPath.startsWith("assets/")) {
                    val assetsFile = File(gameDir, "assets/$normalizedPath").canonicalFile
                    if (assetsFile.exists()) {
                        Log.d("CrossCode", "Remapping /$normalizedPath to /assets/$normalizedPath")
                        Pair(assetsFile, "assets/$normalizedPath")
                    } else {
                        Pair(directFile, normalizedPath)
                    }
                } else {
                    Pair(directFile, normalizedPath)
                }
            } catch (e: IOException) {
                Log.w("CrossCode", "Failed to resolve path $urlPath!", e)
                return WebResourceResponse(null, null, null)
            }

        if (canonicalGameDir == null || !requestedFile.canonicalPath.startsWith("$canonicalGameDir/")) {
            Log.w("CrossCode", "Path traversal rejected: ${requestedFile.absolutePath}")
            return WebResourceResponse(null, null, null)
        }

        if (!requestedFile.exists()) {
            // CCLoader2 fetches mods.json but doesn't need it on Android; avoid a parse error.
            if (normalizedPath == "mods.json") {
                return WebResourceResponse("application/json", "UTF-8", ByteArrayInputStream("[]".toByteArray()))
            }
            Log.w("CrossCode", "Path $urlPath not found! (resolved to ${requestedFile.absolutePath})")
            return WebResourceResponse(null, null, null)
        }

        if (!requestedFile.canRead()) {
            Log.w("CrossCode", "Path $urlPath not readable! (resolved to ${requestedFile.absolutePath})")
            return WebResourceResponse(null, null, null)
        }

        if (requestedFile.isDirectory) {
            // CCLoader checks directory existence by expecting an empty 200 response.
            return WebResourceResponse(null, null, ByteArrayInputStream(byteArrayOf()))
        }

        if (effectiveNormalizedPath.startsWith("assets/") && effectiveNormalizedPath.endsWith(".json")) {
            val assetPath = effectiveNormalizedPath.removePrefix("assets/")
            if (ccmodManager.hasPatches(assetPath)) {
                return servePatchedJson(requestedFile, assetPath)
            }
        }

        val stream = FileInputStream(requestedFile)
        return WebResourceResponse(FallbackMimeTypeMap.resolveMimeType(requestedFile.extension), null, stream)
    }

    private fun servePatchedJson(
        originalFile: File,
        assetPath: String,
    ): WebResourceResponse =
        try {
            val originalJson = originalFile.readText(Charsets.UTF_8)
            val patchedJson = ccmodManager.applyPatches(assetPath, originalJson, ::readGameAsset)
            Log.d("CrossCode", "Serving patched JSON: $assetPath")
            WebResourceResponse(
                "application/json",
                "UTF-8",
                ByteArrayInputStream(patchedJson.toByteArray(Charsets.UTF_8)),
            )
        } catch (e: Exception) {
            Log.e("CrossCode", "Failed to apply patches to $assetPath", e)
            // Serve the unpatched file rather than failing the request.
            WebResourceResponse(
                "application/json",
                "UTF-8",
                FileInputStream(originalFile),
            )
        }

    /** Reads a `game:` patch path, which like CCLoader is relative to (and jailed in) `assets/`. */
    private fun readGameAsset(path: String): String? {
        val assetsDir = File(gameDir ?: return null, "assets").canonicalFile
        val file = File(assetsDir, path).canonicalFile
        if (!file.path.startsWith(assetsDir.path + File.separator) || !file.isFile) return null
        return file.readText(Charsets.UTF_8)
    }

    /**
     * Serves paths inside .ccmod archives. CCLoader queries archive contents via the X-Cmd header:
     * `getFiles`, `isDirectory` or `isFile`; with no header, the file content is returned.
     */
    private fun handleCcmodRequest(
        request: WebResourceRequest,
        ccmodFilePath: String,
        internalPath: String,
        urlPath: String,
    ): WebResourceResponse {
        val ccmodFile = File(ccmodFilePath)
        if (!ccmodFile.exists()) {
            Log.w("CrossCode", "Ccmod file not found: $ccmodFilePath")
            return WebResourceResponse(null, null, null)
        }

        val xCmd = request.requestHeaders["X-Cmd"]
        Log.d("CrossCode", "Ccmod request: $urlPath, X-Cmd: $xCmd, internal: $internalPath")

        return when (xCmd) {
            "getFiles" -> {
                val json = ccmodManager.listFilesAsJson(ccmodFilePath, internalPath)
                Log.d("CrossCode", "getFiles result: $json")
                WebResourceResponse(
                    "application/json",
                    "UTF-8",
                    ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)),
                )
            }

            "isDirectory" -> {
                val isDir = ccmodManager.isDirectory(ccmodFilePath, internalPath)
                Log.d("CrossCode", "isDirectory result: $isDir")
                WebResourceResponse(
                    "application/json",
                    "UTF-8",
                    ByteArrayInputStream(isDir.toString().toByteArray(Charsets.UTF_8)),
                )
            }

            "isFile" -> {
                val isFile = ccmodManager.isFile(ccmodFilePath, internalPath)
                Log.d("CrossCode", "isFile result: $isFile")
                WebResourceResponse(
                    "application/json",
                    "UTF-8",
                    ByteArrayInputStream(isFile.toString().toByteArray(Charsets.UTF_8)),
                )
            }

            else -> {
                if (internalPath.isEmpty()) {
                    // Archive root: answer like a directory (empty 200).
                    return WebResourceResponse(null, null, ByteArrayInputStream(byteArrayOf()))
                }

                val stream = ccmodManager.getFileStream(ccmodFilePath, internalPath)
                if (stream == null) {
                    // May be a directory inside the archive.
                    if (ccmodManager.isDirectory(ccmodFilePath, internalPath)) {
                        return WebResourceResponse(null, null, ByteArrayInputStream(byteArrayOf()))
                    }
                    Log.w("CrossCode", "File not found in ccmod: $internalPath")
                    return WebResourceResponse(null, null, null)
                }

                val extension = internalPath.substringAfterLast('.', "")
                WebResourceResponse(FallbackMimeTypeMap.resolveMimeType(extension), null, stream)
            }
        }
    }

    /** Must be called before loading the game so mod asset overrides and patches are known. */
    fun buildCcmodAssetIndex() {
        val modsDir = gameDir?.let { File(it, "assets/mods") }
        if (modsDir != null && modsDir.exists()) {
            ccmodManager.buildAssetIndex(modsDir)
        }
    }

    /** Returns the parsed ccmod path for [altNormalizedPath] if that .ccmod exists, else null. */
    private fun tryCcmodFallback(altNormalizedPath: String): Pair<String, String>? {
        val altCcmodPath = CcmodManager.parseCcmodPath(File(gameDir!!, altNormalizedPath).canonicalPath)
        return altCcmodPath?.takeIf { File(it.first).exists() }
    }

    /** Releases open .ccmod ZIP handles; call when the activity is destroyed. */
    fun closeCcmodManager() {
        ccmodManager.closeAll()
    }

    fun buildVirtualUrl(): Uri.Builder =
        Uri
            .Builder()
            .scheme(VIRTUAL_ORIGIN_SCHEME)
            .authority(VIRTUAL_ORIGIN_DOMAIN)

    companion object {
        const val VIRTUAL_ORIGIN_SCHEME = "https"
        const val VIRTUAL_ORIGIN_DOMAIN = "appassets.androidplatform.net"
    }
}

object FallbackMimeTypeMap {
    private val extensionsToMimeTypesMap = mutableMapOf<String, String>()

    init {
        // https://github.com/nginx/nginx/blob/release-1.22.0/conf/mime.types
        // https://pagure.io/mailcap/blob/4a12cc7caeb4a74626e8e6aedf38e7223b28e982/f/mime.types
        // https://android.googlesource.com/platform/libcore/+/refs/tags/android-9.0.0_r58/luni/src/main/java/libcore/net/MimeUtils.java
        // https://github.com/nwjs/chromium.src/blob/f65a261f2999a769ac2d718630a8fd5f4b5bb9ac/net/base/mime_util.cc#L146-L230

        // Web stuff
        add("text/html", "html", "htm")
        add("text/css", "css")
        add("text/javascript", "js", "mjs")
        add("application/json", "json", "map")
        add("text/xml", "xml", "xslt", "xsl", "xsd")
        add("application/xhtml+xml", "xhtml", "xht")
        add("application/wasm", "wasm")
        add("font/woff", "woff")
        add("font/woff2", "woff2")
        add("font/ttf", "ttf")
        add("font/otf", "otf")
        add("text/plain", "txt", "text")
        add("text/markdown", "md")

        // Video
        add("video/webm", "webm") // First mapping wins, so this takes precedence over audio/webm
        add("video/ogg", "ogv")
        add("video/mp4", "mp4")

        // Audio
        add("audio/ogg", "ogg", "oga", "opus")
        add("audio/webm", "webm")
        add("audio/mpeg", "mp3")
        add("audio/flac", "flac")
        add("audio/wav", "wav")

        // Images
        add("image/png", "png")
        add("image/jpeg", "jpeg", "jpg")
        add("image/svg+xml", "svg", "svgz")
        add("image/webp", "webp")
        add("image/gif", "gif")
        add("image/bmp", "bmp")
        add("image/vnd.microsoft.icon", "ico")

        // Archives and compression algorithms
        add("application/zip", "zip")
        add("application/gzip", "gz", "tgz")
        add("application/x-tar", "tar")

        // CrossCode-specific
        add("application/zip", "ccmod")
    }

    fun add(
        type: String,
        vararg exts: String,
    ) {
        for (ext in exts) {
            if (ext !in extensionsToMimeTypesMap) {
                extensionsToMimeTypesMap[ext] = type
            }
        }
    }

    fun getMimeTypeFromExtension(extension: String): String? =
        if (extension.isNotEmpty()) extensionsToMimeTypesMap[extension.lowercase(Locale.ROOT)] else null

    /** Prefers Android's built-in MIME map, falling back to ours for types it lacks. */
    fun resolveMimeType(extension: String): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: getMimeTypeFromExtension(extension)
}
