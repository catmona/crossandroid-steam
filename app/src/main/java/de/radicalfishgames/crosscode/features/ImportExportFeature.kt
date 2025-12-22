package de.radicalfishgames.crosscode.features

import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.preferences.SaveImportManager

class ImportExportFeature(
    gameWrapper: GameWrapper,
    hostActivity: GameActivity,
) : Feature(gameWrapper, hostActivity) {
    private val saveImportManager = SaveImportManager(hostActivity)
    private val savesToImport = saveImportManager.consumeAll()

    override fun onPostGamePageLoad() {
        val importJS =
            if (savesToImport.isNotEmpty()) {
                savesToImport.joinToString("\n") { save ->
                    "ig.storage.pushSlotData(\"${save.trim()}\");"
                }
            } else {
                ""
            }

        runJS(
            """
            ig.module("crossandroid.importexport").requires("impact.feature.storage.storage").defines(function(){
                sc.CrossAndroidImportExport = ig.GameAddon.extend({
                    init: function() {
                        $importJS
                    }
                });
                ig.addGameAddon(function() {
                    return sc.crossAndroidImportExport = new sc.CrossAndroidImportExport
                })
            })
        """,
        )
    }
}
