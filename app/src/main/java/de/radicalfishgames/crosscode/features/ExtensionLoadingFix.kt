package de.radicalfishgames.crosscode.features

import android.webkit.JavascriptInterface
import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import org.json.JSONArray
import org.json.JSONObject

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
        extensionForwardingMap = ExtensionScanner.scan(gameWrapper.gameFolderManager)
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
