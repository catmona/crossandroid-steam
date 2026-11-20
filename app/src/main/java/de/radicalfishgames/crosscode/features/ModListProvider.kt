package de.radicalfishgames.crosscode.features

import android.util.Log
import android.webkit.JavascriptInterface
import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import org.json.JSONArray

class ModListProvider(
    gameWrapper: GameWrapper,
    hostActivity: GameActivity,
) : Feature(gameWrapper, hostActivity) {
    lateinit var modList: List<String>

    override fun onPreGamePageLoad() {
        loadModList()
        gameWrapper.exposeJSInterface(this, "CrossAndroidModListProvider")
    }

    private fun loadModList() {
        if (!gameWrapper.modLoaderPresent) {
            modList = emptyList()
            return
        }

        val gameFolderManager = gameWrapper.gameFolderManager
        val modsDirFiles = gameFolderManager.listFiles("assets/mods")

        if (modsDirFiles == null) {
            Log.w("CrossCode", "No mods directory found!")
            modList = emptyList()
            return
        }

        val installedMods = mutableListOf<String>()

        for (modFile in modsDirFiles) {
            var modFileName = modFile.name
            if (modFile.isDirectory || modFile.name.endsWith(".ccmod")) {
                modFileName += "/"
            }

            Log.d("CrossCode", "Found mod $modFileName")

            installedMods.add(modFileName)
        }

        gameWrapper.builtInMods = BuiltInMods.needed(gameFolderManager)
        for (id in gameWrapper.builtInMods) {
            Log.d("CrossCode", "Providing built-in mod $id/")
            installedMods.add("$id/")
        }

        modList = installedMods
    }

    @JavascriptInterface
    fun getModListAsJson(): String {
        val json = JSONArray()
        for (name in modList) {
            json.put(name)
        }
        return json.toString()
    }
}
