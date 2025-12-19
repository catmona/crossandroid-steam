package de.radicalfishgames.crosscode

import android.util.Log
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Manages reading files from .ccmod archives (ZIP files) without extraction.
 * Caches ZipFile handles for performance.
 * Also maintains an asset index for fast lookup of mod assets.
 */
class CcmodManager {
    private val zipCache = ConcurrentHashMap<String, ZipFile>()

    // Maps asset paths (e.g., "media/gui/CCModManager.png") to ccmod file paths
    private val assetIndex = ConcurrentHashMap<String, String>()

    // Maps JSON file paths (e.g., "data/lang/sc/gui.en_US.json") to list of patch locations
    // Each patch location is a Pair of (ccmodPath, internalPath)
    private val patchIndex = ConcurrentHashMap<String, MutableList<Pair<String, String>>>()

    /**
     * Get or open a ZipFile for the given ccmod path.
     */
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
     * Get a file's InputStream from inside a ccmod archive.
     * @param ccmodPath Absolute path to the .ccmod file
     * @param internalPath Path inside the archive (e.g., "package.json" or "assets/data/foo.json")
     * @return InputStream for the file, or null if not found
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

    /**
     * Check if a path exists inside a ccmod archive (file or directory).
     */
    fun exists(
        ccmodPath: String,
        internalPath: String,
    ): Boolean {
        val zipFile = getZipFile(ccmodPath) ?: return false
        val normalizedPath = internalPath.trimStart('/')

        // Check exact entry
        if (zipFile.getEntry(normalizedPath) != null) {
            return true
        }

        // Check as directory (some zips don't have explicit directory entries)
        val dirPath = if (normalizedPath.endsWith("/")) normalizedPath else "$normalizedPath/"
        if (zipFile.getEntry(dirPath) != null) {
            return true
        }

        // Check if any entry starts with this path (implicit directory)
        return zipFile.entries().asSequence().any { entry ->
            entry.name.startsWith(dirPath) || entry.name == normalizedPath
        }
    }

    /**
     * Check if a path is a directory inside a ccmod archive.
     */
    fun isDirectory(
        ccmodPath: String,
        internalPath: String,
    ): Boolean {
        val zipFile = getZipFile(ccmodPath) ?: return false
        val normalizedPath = internalPath.trimStart('/')

        // Empty path = root of archive = directory
        if (normalizedPath.isEmpty()) {
            return true
        }

        // Check explicit directory entry
        val dirPath = if (normalizedPath.endsWith("/")) normalizedPath else "$normalizedPath/"
        val entry = zipFile.getEntry(dirPath)
        if (entry != null && entry.isDirectory) {
            return true
        }

        // Check if it's a file
        val fileEntry = zipFile.getEntry(normalizedPath)
        if (fileEntry != null && !fileEntry.isDirectory) {
            return false
        }

        // Check for implicit directory (entries that start with this path)
        return zipFile.entries().asSequence().any { e ->
            e.name.startsWith(dirPath) && e.name != dirPath
        }
    }

    /**
     * Check if a path is a file inside a ccmod archive.
     */
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

    /**
     * List files in a directory inside a ccmod archive.
     * @return List of filenames (directories end with "/")
     */
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

            // Check if entry is inside the target directory
            if (!entryName.startsWith(normalizedPath) && normalizedPath.isNotEmpty()) {
                continue
            }

            // Skip the directory itself
            if (entryName == normalizedPath) {
                continue
            }

            // Get the relative path from the directory
            val relativePath =
                if (normalizedPath.isEmpty()) {
                    entryName
                } else {
                    entryName.removePrefix(normalizedPath)
                }

            if (relativePath.isEmpty()) {
                continue
            }

            // Get only direct children (first component of relative path)
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

    /**
     * List files as JSON array (for X-Cmd: getFiles response).
     */
    fun listFilesAsJson(
        ccmodPath: String,
        internalPath: String,
    ): String {
        val files = listFiles(ccmodPath, internalPath)
        return JSONArray(files).toString()
    }

    /**
     * Close all cached ZipFile handles.
     */
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

    /**
     * Close a specific ccmod file.
     */
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
     * Build the asset and patch indices by scanning all .ccmod files in the mods directory.
     * Assets inside ccmod's "assets/" folder are indexed by their relative path.
     * Patches (.json.patch files) are indexed by the target JSON file they patch.
     */
    fun buildAssetIndex(modsDir: File) {
        assetIndex.clear()
        patchIndex.clear()
        val ccmodFiles = modsDir.listFiles { file -> file.name.endsWith(".ccmod") } ?: return

        for (ccmodFile in ccmodFiles) {
            try {
                indexCcmodAssets(ccmodFile.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to index ccmod: ${ccmodFile.name}", e)
            }
        }
        Log.i(
            TAG,
            "Asset index built with ${assetIndex.size} entries, ${patchIndex.size} patchable files from ${ccmodFiles.size} ccmod files",
        )
    }

    /**
     * Index all assets and patches from a single ccmod file.
     */
    private fun indexCcmodAssets(ccmodPath: String) {
        val zipFile = getZipFile(ccmodPath) ?: return

        for (entry in zipFile.entries()) {
            val name = entry.name
            // Only index files inside the "assets/" directory
            if (name.startsWith("assets/") && !entry.isDirectory) {
                // Remove "assets/" prefix to get the relative asset path
                val assetPath = name.removePrefix("assets/")
                if (assetPath.isNotEmpty()) {
                    // Check if this is a patch file
                    if (assetPath.endsWith(".json.patch")) {
                        // Map the target JSON file to this patch
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

    /**
     * Look up an asset path in the index.
     * @param assetPath The relative asset path (e.g., "media/gui/CCModManager.png")
     * @return The ccmod file path containing this asset, or null if not found
     */
    fun lookupAsset(assetPath: String): String? = assetIndex[assetPath]

    /**
     * Get a file stream for an indexed asset.
     * @param assetPath The relative asset path (e.g., "media/gui/CCModManager.png")
     * @return InputStream for the asset, or null if not found in any ccmod
     */
    fun getAssetStream(assetPath: String): InputStream? {
        val ccmodPath = assetIndex[assetPath] ?: return null
        // Assets are stored with "assets/" prefix inside the ccmod
        return getFileStream(ccmodPath, "assets/$assetPath")
    }

    /**
     * Check if a JSON file has patches available.
     * @param assetPath The relative asset path (e.g., "data/lang/sc/gui.en_US.json")
     * @return true if there are patches for this file
     */
    fun hasPatches(assetPath: String): Boolean = patchIndex.containsKey(assetPath)

    /**
     * Apply all patches to a JSON file.
     * @param assetPath The relative asset path (e.g., "data/lang/sc/gui.en_US.json")
     * @param originalJson The original JSON content
     * @return The patched JSON content, or original if no patches or patching failed
     */
    fun applyPatches(
        assetPath: String,
        originalJson: String,
    ): String {
        val patches = patchIndex[assetPath] ?: return originalJson

        var result = originalJson
        for ((ccmodPath, internalPath) in patches) {
            try {
                val patchStream = getFileStream(ccmodPath, internalPath)
                if (patchStream != null) {
                    val patchJson = patchStream.bufferedReader().use { it.readText() }
                    val patched = PatchStepsApplier.apply(result, patchJson)
                    if (patched != null) {
                        result = patched
                        Log.d(TAG, "Applied patch $internalPath from $ccmodPath to $assetPath")
                    } else {
                        Log.w(TAG, "Failed to apply patch $internalPath from $ccmodPath")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying patch from $ccmodPath: $internalPath", e)
            }
        }
        return result
    }

    companion object {
        private const val TAG = "CcmodManager"

        /**
         * Check if a path refers to a location inside a .ccmod file.
         * @return Pair of (ccmod file path, internal path) or null if not a ccmod path
         */
        fun parseCcmodPath(fullPath: String): Pair<String, String>? {
            val ccmodIndex = fullPath.indexOf(".ccmod/")
            if (ccmodIndex == -1) {
                // Check if it ends with .ccmod (root of archive)
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
