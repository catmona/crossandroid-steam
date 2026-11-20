package de.radicalfishgames.crosscode

import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import de.radicalfishgames.crosscode.features.BuiltInMods
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
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

        BuiltInMods.serve(normalizedPath, gameWrapper.builtInMods)?.let { body ->
            Log.d("CrossCode", "Serving built-in mod file /$normalizedPath")
            return if (body.isEmpty()) emptyOk() else fileResponse(normalizedPath, ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)))
        }

        return serveCcmodPath(gameDir, request, normalizedPath)
            ?: serveModAsset(normalizedPath)
            ?: serveGameFile(gameDir, normalizedPath)
    }

    /** Serves a path inside a .ccmod archive, or returns null if [normalizedPath] isn't one. */
    private fun serveCcmodPath(
        gameDir: File,
        request: WebResourceRequest,
        normalizedPath: String,
    ): WebResourceResponse? {
        val initialCcmodPath = CcmodManager.parseCcmodPath(File(gameDir, normalizedPath).canonicalPath) ?: return null

        // If the .ccmod isn't at the resolved path, try:
        // 1. Dropping "ccloader/": CCLoader resolves paths from ccloader/, but mods live in assets/mods/.
        // 2. Prepending "assets/": the service worker may omit it.
        val resolved =
            initialCcmodPath.takeIf { File(it.first).exists() }
                ?: listOfNotNull(
                    normalizedPath.removePrefix("ccloader/").takeIf { normalizedPath.startsWith("ccloader/") },
                    "assets/$normalizedPath".takeIf { !normalizedPath.startsWith("assets/") },
                ).firstNotNullOfOrNull(::tryCcmodFallback)
                ?: initialCcmodPath

        val canonicalCcmod = resolved.first
        if (canonicalGameDir == null || !isPathInside(canonicalCcmod, canonicalGameDir)) {
            Log.w("CrossCode", "Path traversal rejected: $canonicalCcmod")
            return notFound()
        }
        return handleCcmodRequest(request, resolved.first, resolved.second, normalizedPath)
    }

    /**
     * Serves a mod's override of a game asset, or returns null if no mod provides one. This also
     * covers service worker pass-through requests that lack the "assets/" prefix (e.g. /media/face/x.png).
     */
    private fun serveModAsset(normalizedPath: String): WebResourceResponse? {
        val assetPath = normalizedPath.removePrefix("assets/")
        val ccmodStream = ccmodManager.getAssetStream(assetPath) ?: return null

        // Overridden JSON still gets every mod's patches applied on top
        if (assetPath.endsWith(".json") && ccmodManager.hasPatches(assetPath)) {
            ccmodStream.close()
            return servePatchedJson(assetPath) { ccmodManager.getAssetStream(assetPath) }
        }
        Log.d("CrossCode", "Serving asset from ccmod: $assetPath")
        return fileResponse(assetPath, ccmodStream)
    }

    private fun serveGameFile(
        gameDir: File,
        normalizedPath: String,
    ): WebResourceResponse {
        // CCLoader3's service worker passes through requests like "/data/changelog.json" whose files
        // live under "assets/", so retry with that prefix when the direct path doesn't exist.
        val (requestedFile, effectiveNormalizedPath) =
            try {
                val directFile = File(gameDir, normalizedPath).canonicalFile
                val assetsFile =
                    if (!directFile.exists() && !normalizedPath.startsWith("assets/")) {
                        File(gameDir, "assets/$normalizedPath").canonicalFile.takeIf { it.exists() }
                    } else {
                        null
                    }
                if (assetsFile != null) {
                    Log.d("CrossCode", "Remapping /$normalizedPath to /assets/$normalizedPath")
                    assetsFile to "assets/$normalizedPath"
                } else {
                    directFile to normalizedPath
                }
            } catch (e: IOException) {
                Log.w("CrossCode", "Failed to resolve path /$normalizedPath!", e)
                return notFound()
            }

        if (canonicalGameDir == null || !isPathInside(requestedFile.path, canonicalGameDir)) {
            Log.w("CrossCode", "Path traversal rejected: ${requestedFile.absolutePath}")
            return notFound()
        }

        if (!requestedFile.exists()) {
            // CCLoader2 fetches mods.json but doesn't need it on Android; avoid a parse error.
            if (normalizedPath == "mods.json") {
                return jsonResponse("[]")
            }
            Log.w("CrossCode", "Path /$normalizedPath not found! (resolved to ${requestedFile.absolutePath})")
            return notFound()
        }

        if (!requestedFile.canRead()) {
            Log.w("CrossCode", "Path /$normalizedPath not readable! (resolved to ${requestedFile.absolutePath})")
            return notFound()
        }

        if (requestedFile.isDirectory) {
            return emptyOk()
        }

        if (effectiveNormalizedPath.startsWith("assets/") && effectiveNormalizedPath.endsWith(".json")) {
            val assetPath = effectiveNormalizedPath.removePrefix("assets/")
            if (ccmodManager.hasPatches(assetPath)) {
                return servePatchedJson(assetPath) { FileInputStream(requestedFile) }
            }
        }

        return fileResponse(requestedFile.name, FileInputStream(requestedFile))
    }

    /** Serves [assetPath] with its patches applied; [openOriginal] opens the unpatched JSON. */
    private fun servePatchedJson(
        assetPath: String,
        openOriginal: () -> InputStream?,
    ): WebResourceResponse =
        try {
            val originalJson =
                openOriginal()?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw FileNotFoundException(assetPath)
            val patchedJson = ccmodManager.applyPatches(assetPath, originalJson, ::readGameAsset)
            Log.d("CrossCode", "Serving patched JSON: $assetPath")
            jsonResponse(patchedJson)
        } catch (e: Exception) {
            Log.e("CrossCode", "Failed to apply patches to $assetPath", e)
            // Serve the unpatched file rather than failing the request.
            WebResourceResponse(JSON_MIME_TYPE, "UTF-8", openOriginal())
        }

    /** Reads a `game:` patch path, which like CCLoader is relative to (and jailed in) `assets/`. */
    private fun readGameAsset(path: String): String? {
        val assetsDir = File(gameDir ?: return null, "assets").canonicalFile
        val file = File(assetsDir, path).canonicalFile
        if (!isPathInside(file.path, assetsDir.path) || !file.isFile) return null
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
            return notFound()
        }

        val xCmd = request.requestHeaders["X-Cmd"]
        Log.d("CrossCode", "Ccmod request: $urlPath, X-Cmd: $xCmd, internal: $internalPath")

        return when (xCmd) {
            "getFiles" -> {
                val json = ccmodManager.listFilesAsJson(ccmodFilePath, internalPath)
                Log.d("CrossCode", "getFiles result: $json")
                jsonResponse(json)
            }

            "isDirectory" -> {
                val isDir = ccmodManager.isDirectory(ccmodFilePath, internalPath)
                Log.d("CrossCode", "isDirectory result: $isDir")
                jsonResponse(isDir.toString())
            }

            "isFile" -> {
                val isFile = ccmodManager.isFile(ccmodFilePath, internalPath)
                Log.d("CrossCode", "isFile result: $isFile")
                jsonResponse(isFile.toString())
            }

            else -> {
                if (internalPath.isEmpty()) {
                    // Archive root: answer like a directory (empty 200).
                    return emptyOk()
                }

                val stream = ccmodManager.getFileStream(ccmodFilePath, internalPath)
                if (stream == null) {
                    // May be a directory inside the archive.
                    if (ccmodManager.isDirectory(ccmodFilePath, internalPath)) {
                        return emptyOk()
                    }
                    Log.w("CrossCode", "File not found in ccmod: $internalPath")
                    return notFound()
                }

                fileResponse(internalPath, stream)
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

        private const val JSON_MIME_TYPE = "application/json"

        private fun jsonResponse(body: String) =
            WebResourceResponse(JSON_MIME_TYPE, "UTF-8", ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)))

        /** Picks the MIME type from [path]'s extension. */
        private fun fileResponse(
            path: String,
            stream: InputStream,
        ) = WebResourceResponse(FallbackMimeTypeMap.resolveMimeType(path.substringAfterLast('.', "")), null, stream)

        // A response without data makes the WebView fail the request
        private fun notFound() = WebResourceResponse(null, null, null)

        // CCLoader checks directory existence by expecting an empty 200 response
        private fun emptyOk() = WebResourceResponse(null, null, ByteArrayInputStream(byteArrayOf()))
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
