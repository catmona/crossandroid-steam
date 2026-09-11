package de.radicalfishgames.crosscode

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
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
     * Scans all external storage volumes (internal + SD card) and returns the first
     * volume where a CrossCode folder already exists. Falls back to the primary volume
     * when none are found, so the UI can still show a sensible "put files here" path.
     */
    fun getGameDir(): File? {
        val dirs = context.getExternalFilesDirs(null)
        if (dirs.isEmpty()) return null
        for (dir in dirs) {
            if (dir == null) continue
            val candidate = File(dir, "CrossCode")
            if (candidate.exists() && candidate.isDirectory && candidate.canRead()) {
                return candidate
            }
        }
        return File(dirs[0] ?: return null, "CrossCode")
    }

    /**
     * Returns the possible game directories, one per mounted external volume (internal first),
     * i.e. the places an in-app import can be written to.
     */
    fun getCandidateGameDirs(): List<File> = context.getExternalFilesDirs(null).filterNotNull().map { File(it, "CrossCode") }

    /**
     * Human-readable name of the volume that [dir] lives on, e.g. "Internal shared storage" or "SD card".
     */
    fun getVolumeLabel(dir: File): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val label =
                context
                    .getSystemService(StorageManager::class.java)
                    ?.getStorageVolume(dir)
                    ?.getDescription(context)
            if (!label.isNullOrBlank()) return label
        }
        return if (dir.absolutePath.startsWith("/storage/emulated/")) "Internal storage" else "SD card"
    }

    /**
     * Returns a CrossCode folder that exists but cannot be read by this app, or null.
     * This happens when the folder was created by another uid (adb shell, Shizuku-based file
     * managers): the files end up owned by `shell` with mode 700, which the app cannot repair.
     */
    fun findUnreadableGameFolder(): File? {
        for (dir in context.getExternalFilesDirs(null)) {
            if (dir == null) continue
            val candidate = File(dir, "CrossCode")
            // exists() only needs search permission on the app-owned parent, so it works even
            // when the folder itself is mode 700 and owned by another uid.
            if (candidate.exists() && !candidate.canRead()) {
                return candidate
            }
        }
        return null
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
