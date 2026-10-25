package de.radicalfishgames.crosscode.moddb

import android.util.Log
import de.radicalfishgames.crosscode.GameFolderManager
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersionOrNull
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/** A mod already present in `assets/mods/`, read directly from its manifest on disk. */
data class LocalMod(
    val id: String,
    /** The highest version among [files]. */
    val version: Version,
    /**
     * Every installed copy: the mod's directory, or its `.ccmod` file for packed mods. Usually one,
     * but a mod can be installed twice under different names, and all copies must go on uninstall.
     */
    val files: List<File>,
)

/**
 * Reads the manifest of every mod already installed in `assets/mods/`, to know what's installed and
 * detect available updates. Mirrors CCLoader3's own manifest fallback (`ccmod.json` then legacy
 * `package.json`) in `packages/core/src/modloader.ts:loadModMetadata`.
 */
class LocalModScanner(
    private val gameFolderManager: GameFolderManager,
) {
    /** Installed mods by id. */
    fun scan(): Map<String, LocalMod> = scan(gameFolderManager.listFiles("assets/mods").orEmpty())

    /** The mods among [entries] (mod directories and `.ccmod` files) by id. */
    fun scan(entries: List<File>): Map<String, LocalMod> =
        entries
            .mapNotNull { entry -> readMod(entry) }
            .groupBy { it.id }
            .mapValues { (id, copies) -> LocalMod(id, copies.maxOf { it.version }, copies.map { it.file }) }

    private class ModCopy(
        val id: String,
        val version: Version,
        val file: File,
    )

    private fun readMod(entry: File): ModCopy? =
        try {
            val manifest =
                when {
                    entry.isDirectory -> readManifest { name -> File(entry, name).takeIf { it.exists() }?.readText() }
                    entry.name.endsWith(".ccmod") -> readPackedManifest(entry)
                    else -> null
                }
            manifest?.let { (json, isLegacy) ->
                val id = if (isLegacy) json.optString("name") else json.optString("id")
                val version = json.optString("version").toVersionOrNull(strict = false)
                if (id.isNullOrEmpty() || version == null) null else ModCopy(id, version, entry)
            }
        } catch (e: Exception) {
            Log.w("CrossCode", "Failed to parse manifest for mod ${entry.name}: ${e.message}")
            null
        }

    // Opened fresh on every scan (not via CcmodManager's cache) so an updated .ccmod is re-read.
    private fun readPackedManifest(ccmodFile: File): Pair<JSONObject, Boolean>? =
        ZipFile(ccmodFile).use { zip ->
            readManifest { name ->
                zip.getEntry(name)?.let { entry -> zip.getInputStream(entry).bufferedReader().use { it.readText() } }
            }
        }

    /** Returns (parsed manifest, isLegacyPackageJson) or null if neither manifest file exists. */
    private fun readManifest(readFile: (String) -> String?): Pair<JSONObject, Boolean>? {
        readFile("ccmod.json")?.let { return JSONObject(it) to false }
        readFile("package.json")?.let { return JSONObject(it) to true }
        return null
    }
}
