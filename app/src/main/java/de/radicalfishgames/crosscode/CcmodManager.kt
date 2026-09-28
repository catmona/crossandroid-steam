package de.radicalfishgames.crosscode

import android.util.Log
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Reads files from .ccmod archives (ZIPs) without extracting them, and indexes mod assets and
 * patches (from both packed and unpacked mods) so overrides can be looked up per request.
 */
class CcmodManager {
    private val zipCache = ConcurrentHashMap<String, ZipFile>()

    // Asset path (e.g. "media/gui/CCModManager.png") -> mod path (.ccmod file or unpacked folder)
    private val assetIndex = ConcurrentHashMap<String, String>()

    // Target JSON path (e.g. "data/lang/sc/gui.en_US.json") -> (modPath, internalPath) of each patch
    private val patchIndex = ConcurrentHashMap<String, MutableList<Pair<String, String>>>()

    private fun getZipFile(ccmodPath: String): ZipFile? {
        return zipCache.getOrPut(ccmodPath) {
            try {
                ZipFile(ccmodPath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open ccmod: $ccmodPath", e)
                return null
            }
        }
    }

    /**
     * Opens [internalPath] (e.g. "assets/data/foo.json") inside the archive at [ccmodPath].
     * Returns null if it is missing or a directory.
     */
    fun getFileStream(
        ccmodPath: String,
        internalPath: String,
    ): InputStream? {
        val zipFile = getZipFile(ccmodPath) ?: return null
        val normalizedPath = internalPath.trimStart('/')

        val entry = zipFile.getEntry(normalizedPath)
        if (entry == null) {
            Log.d(TAG, "Entry not found in $ccmodPath: $normalizedPath")
            return null
        }

        if (entry.isDirectory) {
            Log.d(TAG, "Entry is a directory, not a file: $normalizedPath")
            return null
        }

        return try {
            zipFile.getInputStream(entry)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get stream for $normalizedPath in $ccmodPath", e)
            null
        }
    }

    /** True if [internalPath] is a file or directory in the archive. */
    fun exists(
        ccmodPath: String,
        internalPath: String,
    ): Boolean {
        val zipFile = getZipFile(ccmodPath) ?: return false
        val normalizedPath = internalPath.trimStart('/')

        if (zipFile.getEntry(normalizedPath) != null) {
            return true
        }

        val dirPath = if (normalizedPath.endsWith("/")) normalizedPath else "$normalizedPath/"
        if (zipFile.getEntry(dirPath) != null) {
            return true
        }

        // Some zips omit directory entries, so fall back to a prefix scan.
        return zipFile.entries().asSequence().any { entry ->
            entry.name.startsWith(dirPath) || entry.name == normalizedPath
        }
    }

    fun isDirectory(
        ccmodPath: String,
        internalPath: String,
    ): Boolean {
        val zipFile = getZipFile(ccmodPath) ?: return false
        val normalizedPath = internalPath.trimStart('/')

        // Empty path is the archive root.
        if (normalizedPath.isEmpty()) {
            return true
        }

        val dirPath = if (normalizedPath.endsWith("/")) normalizedPath else "$normalizedPath/"
        val entry = zipFile.getEntry(dirPath)
        if (entry != null && entry.isDirectory) {
            return true
        }

        val fileEntry = zipFile.getEntry(normalizedPath)
        if (fileEntry != null && !fileEntry.isDirectory) {
            return false
        }

        // Some zips omit directory entries, so fall back to a prefix scan.
        return zipFile.entries().asSequence().any { e ->
            e.name.startsWith(dirPath) && e.name != dirPath
        }
    }

    fun isFile(
        ccmodPath: String,
        internalPath: String,
    ): Boolean {
        val zipFile = getZipFile(ccmodPath) ?: return false
        val normalizedPath = internalPath.trimStart('/')

        if (normalizedPath.isEmpty()) {
            return false
        }

        val entry = zipFile.getEntry(normalizedPath) ?: return false
        return !entry.isDirectory
    }

    /** Lists the direct children of a directory in the archive; directory names end with "/". */
    fun listFiles(
        ccmodPath: String,
        internalPath: String,
    ): List<String> {
        val zipFile = getZipFile(ccmodPath) ?: return emptyList()
        val normalizedPath =
            internalPath.trimStart('/').let {
                if (it.isEmpty() || it.endsWith("/")) it else "$it/"
            }

        val results = mutableSetOf<String>()

        for (entry in zipFile.entries()) {
            val entryName = entry.name

            if (!entryName.startsWith(normalizedPath) && normalizedPath.isNotEmpty()) {
                continue
            }

            if (entryName == normalizedPath) {
                continue
            }

            val relativePath =
                if (normalizedPath.isEmpty()) {
                    entryName
                } else {
                    entryName.removePrefix(normalizedPath)
                }

            if (relativePath.isEmpty()) {
                continue
            }

            // Collapse deeper entries to their first path component so only direct children remain.
            val firstSlash = relativePath.indexOf('/')
            val childName =
                if (firstSlash == -1) {
                    relativePath
                } else {
                    relativePath.substring(0, firstSlash + 1) // Include trailing slash for dirs
                }

            if (childName.isNotEmpty()) {
                results.add(childName)
            }
        }

        return results.toList().sorted()
    }

    /** Response body for CCLoader's `X-Cmd: getFiles` request. */
    fun listFilesAsJson(
        ccmodPath: String,
        internalPath: String,
    ): String {
        val files = listFiles(ccmodPath, internalPath)
        return JSONArray(files).toString()
    }

    fun closeAll() {
        for ((path, zipFile) in zipCache) {
            try {
                zipFile.close()
                Log.d(TAG, "Closed ccmod: $path")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing ccmod: $path", e)
            }
        }
        zipCache.clear()
    }

    fun close(ccmodPath: String) {
        zipCache.remove(ccmodPath)?.let { zipFile ->
            try {
                zipFile.close()
                Log.d(TAG, "Closed ccmod: $ccmodPath")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing ccmod: $ccmodPath", e)
            }
        }
    }

    /**
     * Rebuilds the indices from every packed (.ccmod) and unpacked mod in [modsDir]. Files under a
     * mod's "assets/" are keyed by their path relative to it; `foo.json.patch` is keyed as `foo.json`.
     */
    fun buildAssetIndex(modsDir: File) {
        assetIndex.clear()
        patchIndex.clear()

        val modEntries = modsDir.listFiles() ?: return
        var packedCount = 0
        var unpackedCount = 0

        for (entry in modEntries) {
            try {
                if (entry.isFile && entry.name.endsWith(".ccmod")) {
                    indexCcmodAssets(entry.absolutePath)
                    packedCount++
                } else if (entry.isDirectory && isUnpackedMod(entry)) {
                    indexUnpackedModAssets(entry)
                    unpackedCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to index mod: ${entry.name}", e)
            }
        }
        Log.i(
            TAG,
            "Asset index built with ${assetIndex.size} entries, ${patchIndex.size} patchable files " +
                "from $packedCount packed + $unpackedCount unpacked mods",
        )
    }

    private fun isUnpackedMod(dir: File): Boolean = File(dir, "package.json").exists() || File(dir, "ccmod.json").exists()

    private fun indexUnpackedModAssets(modDir: File) {
        val assetsDir = File(modDir, "assets")
        if (!assetsDir.exists() || !assetsDir.isDirectory) {
            return
        }

        indexUnpackedDirectory(modDir.absolutePath, assetsDir, "")
    }

    private fun indexUnpackedDirectory(
        modPath: String,
        dir: File,
        relativePath: String,
    ) {
        val files = dir.listFiles() ?: return

        for (file in files) {
            val assetPath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"

            if (file.isDirectory) {
                indexUnpackedDirectory(modPath, file, assetPath)
            } else {
                if (assetPath.endsWith(".json.patch")) {
                    val targetPath = assetPath.removeSuffix(".patch")
                    patchIndex
                        .getOrPut(targetPath) { mutableListOf() }
                        .add(Pair(modPath, "assets/$assetPath"))
                } else {
                    assetIndex[assetPath] = modPath
                }
            }
        }
    }

    private fun indexCcmodAssets(ccmodPath: String) {
        val zipFile = getZipFile(ccmodPath) ?: return

        for (entry in zipFile.entries()) {
            val name = entry.name
            if (name.startsWith("assets/") && !entry.isDirectory) {
                val assetPath = name.removePrefix("assets/")
                if (assetPath.isNotEmpty()) {
                    if (assetPath.endsWith(".json.patch")) {
                        val targetPath = assetPath.removeSuffix(".patch")
                        patchIndex
                            .getOrPut(targetPath) { mutableListOf() }
                            .add(Pair(ccmodPath, "assets/$assetPath"))
                    } else {
                        assetIndex[assetPath] = ccmodPath
                    }
                }
            }
        }
    }

    /** Returns the mod (.ccmod file or unpacked folder) providing [assetPath], or null. */
    fun lookupAsset(assetPath: String): String? = assetIndex[assetPath]

    /** Opens a mod-provided asset by its path relative to "assets/", or null if no mod has it. */
    fun getAssetStream(assetPath: String): InputStream? {
        val modPath = assetIndex[assetPath] ?: return null
        return getModFileStream(modPath, "assets/$assetPath")
    }

    /** Reads [internalPath] from [modPath], which may be a .ccmod file or an unpacked folder. */
    private fun getModFileStream(
        modPath: String,
        internalPath: String,
    ): InputStream? {
        val modFile = File(modPath)
        return if (modFile.isDirectory) {
            val file = File(modFile, internalPath)
            if (file.exists() && file.isFile) {
                try {
                    file.inputStream()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read file: ${file.absolutePath}", e)
                    null
                }
            } else {
                null
            }
        } else {
            getFileStream(modPath, internalPath)
        }
    }

    fun hasPatches(assetPath: String): Boolean = patchIndex.containsKey(assetPath)

    /**
     * Applies every indexed patch for [assetPath] in turn; a patch that fails is skipped rather
     * than aborting the rest. [gameFileResolver] resolves `game:` paths for IMPORT/INCLUDE.
     */
    fun applyPatches(
        assetPath: String,
        originalJson: String,
        gameFileResolver: ((String) -> String?)? = null,
    ): String {
        val patches = patchIndex[assetPath] ?: return originalJson

        var result = originalJson
        for ((modPath, internalPath) in patches) {
            try {
                val patchStream = getModFileStream(modPath, internalPath)
                if (patchStream != null) {
                    val patchJson = patchStream.bufferedReader().use { it.readText() }
                    val fileResolver = createFileResolver(modPath, gameFileResolver)
                    val patched = PatchStepsApplier.apply(result, patchJson, fileResolver)
                    if (patched != null) {
                        result = patched
                        Log.d(TAG, "Applied patch $internalPath from $modPath to $assetPath")
                    } else {
                        Log.w(TAG, "Failed to apply patch $internalPath from $modPath")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying patch from $modPath: $internalPath", e)
            }
        }
        return result
    }

    /** Resolves IMPORT/INCLUDE paths: `mod:` relative to the mod root, `game:` via [gameFileResolver]. */
    private fun createFileResolver(
        modPath: String,
        gameFileResolver: ((String) -> String?)?,
    ): (String) -> String? =
        { path ->
            val (protocol, relativePath) = parseProtocol(path)
            when (protocol) {
                "mod" -> {
                    readModFile(modPath, relativePath)
                }

                "game" -> {
                    gameFileResolver?.invoke(relativePath)
                }

                else -> {
                    Log.w(TAG, "Unknown protocol '$protocol' in path: $path")
                    null
                }
            }
        }

    /**
     * Splits "proto:path" into (proto, path). A colon only counts as a protocol separator if it
     * comes before the first "/"; otherwise returns (null, path).
     */
    private fun parseProtocol(path: String): Pair<String?, String> {
        val colonIndex = path.indexOf(':')
        return if (colonIndex > 0 && colonIndex < path.indexOf('/').let { if (it == -1) path.length else it }) {
            Pair(path.substring(0, colonIndex), path.substring(colonIndex + 1).trimStart('/'))
        } else {
            Pair(null, path)
        }
    }

    private fun readModFile(
        modPath: String,
        relativePath: String,
    ): String? {
        val stream = getModFileStream(modPath, relativePath) ?: return null
        return try {
            stream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read mod file: $relativePath from $modPath", e)
            null
        }
    }

    companion object {
        private const val TAG = "CcmodManager"

        /**
         * Splits a path into (.ccmod file path, path inside the archive), or returns null if the
         * path doesn't point into a .ccmod.
         */
        fun parseCcmodPath(fullPath: String): Pair<String, String>? {
            val ccmodIndex = fullPath.indexOf(".ccmod/")
            if (ccmodIndex == -1) {
                // A bare ".ccmod" path refers to the archive root.
                if (fullPath.endsWith(".ccmod")) {
                    return Pair(fullPath, "")
                }
                return null
            }

            val ccmodPath = fullPath.substring(0, ccmodIndex + ".ccmod".length)
            val internalPath = fullPath.substring(ccmodIndex + ".ccmod/".length)
            return Pair(ccmodPath, internalPath)
        }
    }
}
