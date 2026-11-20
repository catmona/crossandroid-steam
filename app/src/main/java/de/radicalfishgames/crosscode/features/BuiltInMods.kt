package de.radicalfishgames.crosscode.features

import de.radicalfishgames.crosscode.GameFolderManager
import de.radicalfishgames.crosscode.ModLoaderFlavor
import de.radicalfishgames.crosscode.moddb.LocalModScanner

/**
 * Small library mods the app provides itself, served from memory as if they sat in `assets/mods/`.
 *
 * `extension-asset-preloader` fills `ig.fileForwarding` with node `fs`, which a WebView doesn't have,
 * so the real mod can't run here. Mods such as Azure's Adjustments still declare it as a dependency.
 * CCLoader3 lists it as a built-in package; CCLoader2 doesn't, so with CCLoader2 we provide a stand-in
 * with the same id and version that takes the same forwarding entries from [ExtensionLoadingFix].
 */
object BuiltInMods {
    const val EXTENSION_ASSET_PRELOADER = "extension-asset-preloader"

    private val FILES: Map<String, Map<String, String>> =
        mapOf(
            EXTENSION_ASSET_PRELOADER to
                mapOf(
                    "ccmod.json" to
                        """
                        {
                            "id": "extension-asset-preloader",
                            "version": "1.0.0",
                            "title": "Extension Asset Preloader (CrossAndroid)",
                            "description": "Built into CrossAndroid: fills ig.fileForwarding for the installed DLC extensions.",
                            "tags": ["library"],
                            "postload": "postload.js"
                        }
                        """.trimIndent(),
                    // CCLoader2 also probes the legacy manifest; give it the same answer
                    "package.json" to
                        """
                        {
                            "id": "extension-asset-preloader",
                            "version": "1.0.0",
                            "title": "Extension Asset Preloader (CrossAndroid)",
                            "postload": "postload.js"
                        }
                        """.trimIndent(),
                    "postload.js" to
                        """
                        (function () {
                            var provider = window.CrossAndroidExtensionListProvider;
                            if (!provider || !provider.getFileForwardingAsJson) return;
                            var entries = JSON.parse(provider.getFileForwardingAsJson());
                            for (var extFile in entries) ig.fileForwarding[extFile] = entries[extFile];
                            console.log("extension-asset-preloader (built in): " + Object.keys(entries).length + " forwarded files");
                        })();
                        """.trimIndent(),
                ),
        )

    /** The built-in mods to offer: only with CCLoader2, and only those no installed mod already provides. */
    fun needed(gameFolderManager: GameFolderManager): Set<String> {
        if (gameFolderManager.findModLoader()?.flavor != ModLoaderFlavor.CCLOADER2) return emptySet()
        val installed = LocalModScanner(gameFolderManager).scan().keys
        val folders = gameFolderManager.listFiles("assets/mods").orEmpty().map { it.name }.toSet()
        return FILES.keys.filterTo(HashSet()) { it !in installed && it !in folders }
    }

    /**
     * What to serve for [normalizedPath] (relative to the game folder) when it points into one of
     * the [active] built-in mods: the file's text, "" for the mod folder itself, or null otherwise.
     */
    fun serve(
        normalizedPath: String,
        active: Set<String>,
    ): String? {
        val underAssets = normalizedPath.removePrefix("ccloader/").removePrefix("assets/")
        if (!underAssets.startsWith("mods/")) return null
        val rest = underAssets.removePrefix("mods/")
        val modId = rest.substringBefore('/')
        if (modId !in active) return null
        val file = rest.substringAfter('/', "").trim('/')
        if (file.isEmpty()) return ""
        return FILES[modId]?.get(file)
    }
}
