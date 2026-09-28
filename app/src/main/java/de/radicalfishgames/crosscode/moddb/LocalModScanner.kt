package de.radicalfishgames.crosscode.moddb

import android.util.Log
import de.radicalfishgames.crosscode.CcmodManager
import de.radicalfishgames.crosscode.GameFolderManager
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersionOrNull
import org.json.JSONObject
import java.io.File

/** A mod already present in `assets/mods/`, read directly from its manifest on disk. */
data class LocalMod(
    val id: String,
    val version: Version,
    /** The mod's directory, or its `.ccmod` file for packed mods. */
    val file: File,
)

/**
 * Reads the manifest of every mod already installed in `assets/mods/`, to know what's installed and
 * detect available updates. Mirrors CCLoader3's own manifest fallback (`ccmod.json` then legacy
 * `package.json`) in `packages/core/src/modloader.ts:loadModMetadata`.
 */
class LocalModScanner(
    private val gameFolderManager: GameFolderManager,
    private val ccmodManager: CcmodManager,
) {
    fun scan(): List<LocalMod> {
        val modsDirFiles = gameFolderManager.listFiles("assets/mods") ?: return emptyList()
        return modsDirFiles.mapNotNull { entry -> readMod(entry) }
    }

    private fun readMod(entry: File): LocalMod? {
        val manifest: Pair<JSONObject, Boolean> =
            when {
                entry.isDirectory -> readUnpackedManifest(entry)
                entry.name.endsWith(".ccmod") -> readPackedManifest(entry)
                else -> null
            } ?: return null

        return try {
            val (json, isLegacy) = manifest
            val id = if (isLegacy) json.optString("name") else json.optString("id")
            val version = json.optString("version").toVersionOrNull(strict = false)
            if (id.isNullOrEmpty() || version == null) return null
            LocalMod(id, version, entry)
        } catch (e: Exception) {
            Log.w("CrossCode", "Failed to parse manifest for mod ${entry.name}: ${e.message}")
            null
        }
    }

    /** Returns (parsed manifest, isLegacyPackageJson) or null if neither manifest file exists. */
    private fun readUnpackedManifest(dir: File): Pair<JSONObject, Boolean>? {
        val ccmodJson = File(dir, "ccmod.json")
        if (ccmodJson.exists()) return JSONObject(ccmodJson.readText()) to false

        val packageJson = File(dir, "package.json")
        if (packageJson.exists()) return JSONObject(packageJson.readText()) to true

        return null
    }

    private fun readPackedManifest(ccmodFile: File): Pair<JSONObject, Boolean>? {
        val ccmodPath = ccmodFile.absolutePath

        ccmodManager.getFileStream(ccmodPath, "ccmod.json")?.let { stream ->
            return stream.bufferedReader().use { it.readText() }.let { JSONObject(it) to false }
        }
        ccmodManager.getFileStream(ccmodPath, "package.json")?.let { stream ->
            return stream.bufferedReader().use { it.readText() }.let { JSONObject(it) to true }
        }
        return null
    }
}
