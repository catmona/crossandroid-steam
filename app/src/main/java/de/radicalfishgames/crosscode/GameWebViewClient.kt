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
    private val ccmodManager = CcmodManager()

    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        gameWrapper.onPageLoaded()
    }

    // A request interceptor is used for two purposes. Firstly, ES6 imports refuse to work if the imported
    // file is not returned with the appropriate MIME type (such as text/javascript), and files returned
    // from file:// don't get content types at all, so we perform our own MIME type detection. Secondly, the
    // game is "served" from a virtual domain with HTTPS, so the browser thinks that this is a real website
    // and not some random page loaded from file://, and thus allows using APIs such as fetch() and enables
    // the regular same-origin policy, while in reality no HTTP server is running and the requests to that
    // domain are handled here by us. Additionally, the HTTPS scheme is used to enable installation of
    // service workers. This technique (and where I found the domain name reserved for this purpose) is
    // described in more detail here:
    // https://developer.android.com/guide/webapps/load-local-content
    // https://developer.android.com/reference/androidx/webkit/WebViewAssetLoader
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        if (view == null || request == null) {
            return null
        }

        if (gameDir == null) {
            Log.e("CrossCode", "gameDir is null, cannot serve files!")
            return null
        }

        val url = request.url
        val urlPath = url.path
        if (!(url.scheme == VIRTUAL_ORIGIN_SCHEME && url.authority == VIRTUAL_ORIGIN_DOMAIN && urlPath != null)) {
            return null
        }

        val normalizedPath = urlPath.trimStart('/')
        var fullPath = File(gameDir, normalizedPath).canonicalPath

        // Check if this is a request for a file inside a .ccmod archive
        var ccmodPath = CcmodManager.parseCcmodPath(fullPath)
        if (ccmodPath != null) {
            // CCLoader runs from ccloader/ directory, but ccmod files are in assets/mods/
            // If the ccmod file doesn't exist at the resolved path, try without the ccloader/ prefix
            if (!File(ccmodPath.first).exists() && normalizedPath.startsWith("ccloader/")) {
                val pathWithoutCcloader = normalizedPath.removePrefix("ccloader/")
                val altFullPath = File(gameDir, pathWithoutCcloader).canonicalPath
                val altCcmodPath = CcmodManager.parseCcmodPath(altFullPath)
                if (altCcmodPath != null && File(altCcmodPath.first).exists()) {
                    fullPath = altFullPath
                    ccmodPath = altCcmodPath
                }
            }
            return handleCcmodRequest(request, ccmodPath.first, ccmodPath.second, normalizedPath)
        }

        // Check ccmod assets FIRST (allows mods to override existing game assets)
        if (normalizedPath.startsWith("assets/")) {
            val assetPath = normalizedPath.removePrefix("assets/")
            val stream = ccmodManager.getAssetStream(assetPath)
            if (stream != null) {
                Log.d("CrossCode", "Serving asset from ccmod: $assetPath")
                val extension = assetPath.substringAfterLast('.', "")
                val mimeType =
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                        ?: FallbackMimeTypeMap.getMimeTypeFromExtension(extension)
                return WebResourceResponse(mimeType, null, stream)
            }
        }

        // Fall back to regular file handling
        val requestedFile =
            try {
                File(gameDir, normalizedPath).canonicalFile
            } catch (e: IOException) {
                Log.w("CrossCode", "Failed to resolve path $urlPath!", e)
                return WebResourceResponse(null, null, null)
            }

        if (!requestedFile.exists()) {
            Log.w("CrossCode", "Path $urlPath not found! (resolved to ${requestedFile.absolutePath})")
            return WebResourceResponse(null, null, null)
        }

        if (!requestedFile.canRead()) {
            Log.w("CrossCode", "Path $urlPath not readable! (resolved to ${requestedFile.absolutePath})")
            return WebResourceResponse(null, null, null)
        }

        if (requestedFile.isDirectory) {
            // Return an empty successful response for directories, CCLoader depends on that for
            // checking whether they exist.
            return WebResourceResponse(null, null, ByteArrayInputStream(byteArrayOf()))
        }

        // Check if this is a JSON file with patches
        if (normalizedPath.startsWith("assets/") && normalizedPath.endsWith(".json")) {
            val assetPath = normalizedPath.removePrefix("assets/")
            if (ccmodManager.hasPatches(assetPath)) {
                return servePatchedJson(requestedFile, assetPath)
            }
        }

        val stream = FileInputStream(requestedFile)

        val mimeType =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(requestedFile.extension)
                ?: FallbackMimeTypeMap.getMimeTypeFromExtension(requestedFile.extension)

        return WebResourceResponse(mimeType, null, stream)
    }

    /**
     * Serve a JSON file with patches applied.
     */
    private fun servePatchedJson(originalFile: File, assetPath: String): WebResourceResponse {
        return try {
            val originalJson = originalFile.readText(Charsets.UTF_8)
            val patchedJson = ccmodManager.applyPatches(assetPath, originalJson)
            Log.d("CrossCode", "Serving patched JSON: $assetPath")
            WebResourceResponse(
                "application/json",
                "UTF-8",
                ByteArrayInputStream(patchedJson.toByteArray(Charsets.UTF_8)),
            )
        } catch (e: Exception) {
            Log.e("CrossCode", "Failed to apply patches to $assetPath", e)
            // Fall back to original file
            WebResourceResponse(
                "application/json",
                "UTF-8",
                FileInputStream(originalFile),
            )
        }
    }

    /**
     * Handle requests for files inside .ccmod archives.
     * CCLoader uses X-Cmd headers to query ccmod contents:
     * - X-Cmd: getFiles - list files in a directory
     * - X-Cmd: isDirectory - check if path is a directory
     * - X-Cmd: isFile - check if path is a file
     * - (no header) - get file content
     */
    private fun handleCcmodRequest(
        request: WebResourceRequest,
        ccmodFilePath: String,
        internalPath: String,
        urlPath: String,
    ): WebResourceResponse {
        // Check if the .ccmod file itself exists
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
                // No X-Cmd header - serve the actual file content
                if (internalPath.isEmpty()) {
                    // Request for the ccmod root - treat as directory
                    return WebResourceResponse(null, null, ByteArrayInputStream(byteArrayOf()))
                }

                val stream = ccmodManager.getFileStream(ccmodFilePath, internalPath)
                if (stream == null) {
                    // Could be a directory inside the ccmod
                    if (ccmodManager.isDirectory(ccmodFilePath, internalPath)) {
                        return WebResourceResponse(null, null, ByteArrayInputStream(byteArrayOf()))
                    }
                    Log.w("CrossCode", "File not found in ccmod: $internalPath")
                    return WebResourceResponse(null, null, null)
                }

                // Determine MIME type from the internal file extension
                val extension = internalPath.substringAfterLast('.', "")
                val mimeType =
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                        ?: FallbackMimeTypeMap.getMimeTypeFromExtension(extension)

                WebResourceResponse(mimeType, null, stream)
            }
        }
    }

    /**
     * Build the ccmod asset index. Should be called before loading the game.
     */
    fun buildCcmodAssetIndex() {
        val modsDir = gameDir?.let { File(it, "assets/mods") }
        if (modsDir != null && modsDir.exists()) {
            ccmodManager.buildAssetIndex(modsDir)
        }
    }

    /**
     * Close ccmod manager resources. Should be called when activity is destroyed.
     */
    fun closeCcmodManager() {
        ccmodManager.closeAll()
    }

    fun buildVirtualUrl(): Uri.Builder =
        Uri
            .Builder()
            .scheme(VIRTUAL_ORIGIN_SCHEME)
            .authority(VIRTUAL_ORIGIN_DOMAIN)
            .appendQueryParameter("crossandroid", "true")

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
        add("video/webm", "webm") // Must come before audio/webm
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
}
