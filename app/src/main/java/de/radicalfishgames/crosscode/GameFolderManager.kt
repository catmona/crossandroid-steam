package de.radicalfishgames.crosscode

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manages access to the game folder in the app's external files directory.
 * Games files should be placed in: Android/data/de.radicalfishgames.crosscode/files/CrossCode/
 */
class GameFolderManager(
    private val context: Context,
) {
    /**
     * Returns the game directory, or null if external storage is not available.
     */
    fun getGameDir(): File? {
        val externalFilesDir = context.getExternalFilesDir(null) ?: return null
        return File(externalFilesDir, "CrossCode")
    }

    /**
     * Returns a human-readable path for display purposes.
     */
    fun getDisplayPath(): String? = getGameDir()?.absolutePath

    /**
     * Checks if the game folder exists and is readable.
     */
    fun hasValidGameFolder(): Boolean {
        val gameDir = getGameDir() ?: return false
        return gameDir.exists() && gameDir.isDirectory && gameDir.canRead()
    }

    /**
     * Checks if the assets folder exists in the game folder.
     */
    fun hasAssetsFolder(): Boolean {
        val gameDir = getGameDir() ?: return false
        val assetsDir = File(gameDir, "assets")
        return assetsDir.exists() && assetsDir.isDirectory
    }

    /**
     * Checks if a modloader is present and returns its entry file.
     */
    fun findModLoaderEntry(): String? {
        val gameDir = getGameDir() ?: return null

        // Check for CCLoader3
        val ccloader3Main = File(gameDir, "ccloader3/main.html")
        if (ccloader3Main.exists()) {
            return "ccloader3/main.html"
        }

        // Check for CCLoader (legacy)
        val ccloaderMain = File(gameDir, "ccloader/main.html")
        if (ccloaderMain.exists()) {
            return "ccloader/main.html"
        }

        val ccloaderIndex = File(gameDir, "ccloader/index.html")
        if (ccloaderIndex.exists()) {
            return "ccloader/index.html"
        }

        return null
    }

    /**
     * Returns a File for the given relative path, or null if the game folder isn't available.
     */
    fun getFile(relativePath: String): File? {
        val gameDir = getGameDir() ?: return null
        return File(gameDir, relativePath)
    }

    /**
     * Checks if a file or directory exists at the given path.
     */
    fun exists(relativePath: String): Boolean {
        val file = getFile(relativePath) ?: return false
        return file.exists()
    }

    /**
     * Checks if the path points to a directory.
     */
    fun isDirectory(relativePath: String): Boolean {
        val file = getFile(relativePath) ?: return false
        return file.isDirectory
    }

    /**
     * Lists files in a directory at the given path.
     */
    fun listFiles(relativePath: String): List<File>? {
        val file = getFile(relativePath) ?: return null
        if (!file.isDirectory) return null
        return file.listFiles()?.toList()
    }
}
