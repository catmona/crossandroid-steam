package de.radicalfishgames.crosscode.features

import android.util.Log
import de.radicalfishgames.crosscode.GameFolderManager
import org.json.JSONObject
import java.io.File

/**
 * Scans `assets/extension/` for installed DLC extensions (post-game, manlea, ninja-skin, ...).
 * An extension is considered present when its directory has a matching `<name>.json` manifest with
 * a non-empty `files` array — the same check CCLoader itself performs. This is the app's own source
 * of truth for "is this DLC installed", read directly from disk with no running game required.
 */
object ExtensionScanner {
    /** Maps extensionName -> list of files from the manifest's "files" key. */
    fun scan(gameFolderManager: GameFolderManager): Map<String, List<String>> {
        val extensionDirFiles = gameFolderManager.listFiles("assets/extension") ?: run {
            Log.w("CrossCode", "No extension directory found!")
            return emptyMap()
        }

        val discovered = mutableMapOf<String, List<String>>()

        for (singleExtDir in extensionDirFiles) {
            if (!singleExtDir.isDirectory) continue

            val extensionName = singleExtDir.name
            val manifestFile = File(singleExtDir, "$extensionName.json")
            if (!manifestFile.exists()) {
                Log.w("CrossCode", "Found extension directory $extensionName, but not $extensionName.json!")
                continue
            }

            val files = parseManifestFiles(extensionName, manifestFile) ?: continue
            discovered[extensionName] = files
        }

        return discovered
    }

    private fun parseManifestFiles(
        extensionName: String,
        manifestFile: File,
    ): List<String>? =
        try {
            val json = JSONObject(manifestFile.readText())
            if (!json.has("files")) {
                Log.w("CrossCode", "Extension $extensionName manifest has no 'files' key, skipping")
                null
            } else {
                val filesArray = json.getJSONArray("files")
                if (filesArray.length() == 0) {
                    Log.w("CrossCode", "Extension $extensionName manifest has empty 'files' array, skipping")
                    null
                } else {
                    (0 until filesArray.length()).map { filesArray.getString(it) }
                }
            }
        } catch (e: Exception) {
            Log.w("CrossCode", "Failed to parse manifest for extension $extensionName: ${e.message}")
            null
        }
}
