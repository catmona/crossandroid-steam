package de.radicalfishgames.crosscode

import android.content.Context
import android.os.storage.StorageManager
import java.io.File

/**
 * Locates the game folder: Android/data/de.radicalfishgames.crosscode/files/CrossCode/ on any
 * external volume (internal storage or SD card).
 */
class GameFolderManager(
    private val context: Context,
) {
    /**
     * Returns the first readable CrossCode folder across all volumes, else the primary volume's
     * (possibly nonexistent) path so the UI can show where to put files. Null if no external storage.
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

    /** Human-readable name of the volume [dir] lives on, e.g. "Internal shared storage" or "SD card". */
    fun getVolumeLabel(dir: File): String {
        val label =
            context
                .getSystemService(StorageManager::class.java)
                ?.getStorageVolume(dir)
                ?.getDescription(context)
        if (!label.isNullOrBlank()) return label
        return if (dir.absolutePath.startsWith("/storage/emulated/")) "Internal storage" else "SD card"
    }

    /**
     * Returns a CrossCode folder that exists but is unreadable, or null. Happens when another uid
     * (adb shell, Shizuku-based file managers) created it: owned by `shell`, mode 700, unfixable by us.
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

    fun getDisplayPath(): String? = getGameDir()?.absolutePath

    fun hasValidGameFolder(): Boolean {
        val gameDir = getGameDir() ?: return false
        return gameDir.exists() && gameDir.isDirectory && gameDir.canRead()
    }

    fun hasAssetsFolder(): Boolean {
        val gameDir = getGameDir() ?: return false
        val assetsDir = File(gameDir, "assets")
        return assetsDir.exists() && assetsDir.isDirectory
    }

    /**
     * Returns the installed modloader, or null. CCLoader3 is identified by main.html and CCLoader2 by
     * index.html, whichever folder they're in: CCLoader3 releases are extracted into ccloader/ too.
     */
    fun findModLoader(): ModLoader? {
        val gameDir = getGameDir() ?: return null
        for (dir in listOf("ccloader3", "ccloader")) {
            if (File(gameDir, "$dir/main.html").exists()) return ModLoader(ModLoaderFlavor.CCLOADER3, dir, "$dir/main.html")
            if (File(gameDir, "$dir/index.html").exists()) return ModLoader(ModLoaderFlavor.CCLOADER2, dir, "$dir/index.html")
        }
        return null
    }

    fun getFile(relativePath: String): File? {
        val gameDir = getGameDir() ?: return null
        return File(gameDir, relativePath)
    }

    fun exists(relativePath: String): Boolean {
        val file = getFile(relativePath) ?: return false
        return file.exists()
    }

    fun listFiles(relativePath: String): List<File>? {
        val file = getFile(relativePath) ?: return null
        if (!file.isDirectory) return null
        return file.listFiles()?.toList()
    }
}
