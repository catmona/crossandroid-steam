package de.radicalfishgames.crosscode.moddb

import android.util.Log
import de.radicalfishgames.crosscode.GameFolderManager
import de.radicalfishgames.crosscode.features.ExtensionScanner
import io.github.z4kn4fein.semver.Version
import io.github.z4kn4fein.semver.toVersionOrNull
import org.json.JSONObject

/**
 * Computes versions for dependency ids that aren't downloadable mods (the game, the modloader, DLC
 * extensions, and a few CCLoader3-bundled legacy mods), mirroring `virtualPackages` in CCLoader3's
 * `packages/core/src/modloader.ts`. Reads only files on disk, so no running game session is needed.
 */
object VirtualPackages {
    // CCLoader3 hardcodes these regardless of platform (modloader.ts:57-64). `nw` is desktop-only.
    private val FIXED_VERSIONS =
        mapOf(
            "Simplify" to "2.14.2",
            "input-api" to "1.0.2",
            "extension-asset-preloader" to "1.0.0",
            "nax-module-cache" to "1.0.2",
        )

    private val CCLOADER2_VERSION_REGEX = Regex("""CCLOADER_VERSION\s*=\s*['"]([\d.]+)['"]""")

    fun compute(
        gameFolderManager: GameFolderManager,
        loader: ModLoaderFlavor,
    ): Map<String, Version> {
        val result = mutableMapOf<String, Version>()

        val crossCodeVersion = readCrossCodeVersion(gameFolderManager)
        if (crossCodeVersion != null) result["crosscode"] = crossCodeVersion

        readCcloaderVersion(gameFolderManager, loader)?.let { result["ccloader"] = it }

        // DLC extensions share the crosscode version, matching CCLoader3's `defaultExtVersion`.
        if (crossCodeVersion != null) {
            for (extensionId in ExtensionScanner.scan(gameFolderManager).keys) {
                result[extensionId] = crossCodeVersion
            }
        }

        for ((id, version) in FIXED_VERSIONS) {
            version.toVersionOrNull(strict = false)?.let { result[id] = it }
        }

        return result
    }

    private fun readCrossCodeVersion(gameFolderManager: GameFolderManager): Version? {
        val changelogFile = gameFolderManager.getFile("assets/data/changelog.json")
        if (changelogFile == null || !changelogFile.exists()) return null
        return try {
            val changelog = JSONObject(changelogFile.readText()).getJSONArray("changelog")
            if (changelog.length() == 0) return null
            changelog.getJSONObject(0).optString("version").toVersionOrNull(strict = false)
        } catch (e: Exception) {
            Log.w("CrossCode", "Failed to read crosscode version from changelog.json: ${e.message}")
            null
        }
    }

    private fun readCcloaderVersion(
        gameFolderManager: GameFolderManager,
        loader: ModLoaderFlavor,
    ): Version? =
        when (loader) {
            ModLoaderFlavor.CCLOADER3 -> readCcloader3Version(gameFolderManager)
            ModLoaderFlavor.CCLOADER2 -> readCcloader2Version(gameFolderManager)
        }

    private fun readCcloader3Version(gameFolderManager: GameFolderManager): Version? {
        val metadataFile = gameFolderManager.getFile("ccloader3/metadata.json")
        if (metadataFile == null || !metadataFile.exists()) return null
        return try {
            JSONObject(metadataFile.readText()).optString("version").toVersionOrNull(strict = false)
        } catch (e: Exception) {
            Log.w("CrossCode", "Failed to read ccloader3/metadata.json: ${e.message}")
            null
        }
    }

    private fun readCcloader2Version(gameFolderManager: GameFolderManager): Version? {
        val ccloaderJs = gameFolderManager.getFile("ccloader/js/ccloader.js")
        if (ccloaderJs == null || !ccloaderJs.exists()) return null
        return try {
            CCLOADER2_VERSION_REGEX.find(ccloaderJs.readText())
                ?.groupValues
                ?.get(1)
                ?.toVersionOrNull(strict = false)
        } catch (e: Exception) {
            Log.w("CrossCode", "Failed to read ccloader/js/ccloader.js: ${e.message}")
            null
        }
    }
}
