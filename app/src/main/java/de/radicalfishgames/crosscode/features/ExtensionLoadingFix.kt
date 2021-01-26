package de.radicalfishgames.crosscode.features

import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper

class ExtensionLoadingFix(gameWrapper: GameWrapper,
                          hostActivity: GameActivity
) : Feature(gameWrapper, hostActivity) {

    private lateinit var extensionList: List<String>

    override fun onPreGamePageLoad() {
        loadExtensionList()
    }

    override fun onPostGamePageLoad() {
        val extensionString = extensionList.joinToString(prefix = "['", separator = "','", postfix = "']")
        runJS(
            """
                ig.ExtensionList.inject({
                    loadInternal() {
                        this.loadExtensionsAndroid();
                    },
                    loadExtensionsAndroid() {
                        this.onExtensionListLoaded($extensionString);
                    }
                });
            """
        )
    }

    private fun loadExtensionList() {
        val extensionDirUri = Uri.parse("file:///${gameWrapper.gameDir}/extension/")
        val extensionDir = extensionDirUri.toFile()

        if(!extensionDir.exists() || !extensionDir.isDirectory) {
            Log.w("CrossCode", "No extension directory found! Searched at ${extensionDirUri.path}")
            return
        }

        val discoveredExtensions = mutableListOf<String>()

        for(singleExtDir in extensionDir.listFiles()) {
            if(!singleExtDir.isDirectory) {
                continue
            }

            val extensionName = singleExtDir.name

            if(!singleExtDir.list().contains("$extensionName.json")) {
                Log.w("CrossCode", "Found extension directory $extensionName, but not $extensionName/$extensionName.json!")
                continue
            }

            Log.d("CrossCode", "Found extension $extensionName")

            discoveredExtensions.add(extensionName)
        }

        extensionList = discoveredExtensions
    }

}