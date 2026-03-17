package de.radicalfishgames.crosscode.features

import android.util.Log
import android.webkit.JavascriptInterface
import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ExtensionLoadingFix(
    gameWrapper: GameWrapper,
    hostActivity: GameActivity,
) : Feature(gameWrapper, hostActivity) {
    // Maps extensionName -> list of files from manifest's "files" key
    lateinit var extensionForwardingMap: Map<String, List<String>>

    override fun onPreGamePageLoad() {
        loadExtensionList()
        gameWrapper.exposeJSInterface(this, "CrossAndroidExtensionListProvider")
    }

    override fun onPostGamePageLoad() {
        runJS(
            """
                ig.ExtensionList.inject({
                    loadInternal() {
                        setTimeout(this.loadExtensionsAndroid.bind(this), 0);
                    },
                    loadExtensionsAndroid() {
                        this.onExtensionListLoaded(${getExtensionListAsJson()});
                    }
                });
            """,
        )
        val forwardingEntries = buildFileForwardingEntries()
        if (forwardingEntries.length() > 0) {
            runJS(
                """
                    (function() {
                        var entries = $forwardingEntries;
                        Object.keys(entries).forEach(function(ext_file) {
                            ig.fileForwarding[ext_file] = entries[ext_file];
                        });
                    })();
                """,
            )
        }
    }

    private fun loadExtensionList() {
        val gameFolderManager = gameWrapper.gameFolderManager
        val extensionDirFiles = gameFolderManager.listFiles("assets/extension")

        if (extensionDirFiles == null) {
            Log.w("CrossCode", "No extension directory found!")
            extensionForwardingMap = emptyMap()
            return
        }

        val discovered = mutableMapOf<String, List<String>>()

        for (singleExtDir in extensionDirFiles) {
            if (!singleExtDir.isDirectory) {
                continue
            }

            val extensionName = singleExtDir.name

            // Check if manifest file exists
            val manifestFile = File(singleExtDir, "$extensionName.json")
            if (!manifestFile.exists()) {
                Log.w("CrossCode", "Found extension directory $extensionName, but not $extensionName.json!")
                continue
            }

            Log.d("CrossCode", "Found extension $extensionName")

            val files = parseExtensionManifestFiles(extensionName, manifestFile) ?: continue
            discovered[extensionName] = files
        }

        extensionForwardingMap = discovered
    }

    private fun parseExtensionManifestFiles(
        extensionName: String,
        manifestFile: File,
    ): List<String>? {
        return try {
            val json = JSONObject(manifestFile.readText())
            if (!json.has("files")) {
                Log.w("CrossCode", "Extension $extensionName manifest has no 'files' key, skipping")
                return null
            }
            val filesArray = json.getJSONArray("files")
            if (filesArray.length() == 0) {
                Log.w("CrossCode", "Extension $extensionName manifest has empty 'files' array, skipping")
                return null
            }
            (0 until filesArray.length()).map { filesArray.getString(it) }
        } catch (e: Exception) {
            Log.w("CrossCode", "Failed to parse manifest for extension $extensionName: ${e.message}")
            emptyList()
        }
    }

    private fun buildFileForwardingEntries(): JSONObject {
        val result = JSONObject()
        for ((extensionName, files) in extensionForwardingMap) {
            for (extFile in files) {
                result.put(extFile, "extension/$extensionName/$extFile")
            }
        }
        return result
    }

    @JavascriptInterface
    fun getExtensionListAsJson(): String {
        val json = JSONArray()
        for (name in extensionForwardingMap.keys) {
            json.put(name)
        }
        return json.toString()
    }
}
