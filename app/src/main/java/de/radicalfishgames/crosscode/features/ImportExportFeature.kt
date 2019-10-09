package de.radicalfishgames.crosscode.features

import android.R.attr.host
import android.content.ClipboardManager
import android.content.DialogInterface
import android.webkit.JavascriptInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import android.R.attr.label
import android.content.ClipData
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import androidx.core.content.ContextCompat.getSystemService




class ImportExportFeature(
    gameWrapper: GameWrapper,
    hostActivity: GameActivity
) : Feature(gameWrapper, hostActivity) {

    private val saveStringToImport = hostActivity.preferences.getString("save_to_import", "")
    private val exportSaveEnabled = hostActivity.preferences.getBoolean("export_save", false)

    override fun onPreGamePageLoad() {

        gameWrapper.exposeJSInterface(this, "CAImportExport")

    }

    override fun onPostGamePageLoad() {

        val importJS: String
        if(saveStringToImport != null && saveStringToImport.isNotEmpty()){
            importJS = "ig.storage.pushSlotData(\"${saveStringToImport.trim()}\");"

            hostActivity.preferences.edit()
                .putString("save_to_import", "")
                .apply()

        }else{
            importJS = ""
        }

        val exportJS: String
        if(exportSaveEnabled){
            exportJS = "CAImportExport.showExportedSave(ig.storage.getLastSlotData());"
        }else{
            exportJS = ""
        }

        runJS("""
            ig.module("crossandroid.importexport").requires("impact.feature.storage.storage").defines(function(){
                sc.CrossAndroidImportExport = ig.GameAddon.extend({
                    init: function() {
                        $exportJS
                        $importJS
                    }
                });
                ig.addGameAddon(function() {
                    return sc.crossAndroidImportExport = new sc.CrossAndroidImportExport
                })
            })
        """)

    }

    @JavascriptInterface
    fun showExportedSave(saveString: String){
        try {

            MaterialAlertDialogBuilder(hostActivity)
                .setTitle("Exported Save String")
                .setMessage("Your exported Save String is ready.")
                .setPositiveButton("Copy to Clipboard and Close") { dialog: DialogInterface, which: Int ->
                    val clipboard = hostActivity.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Save String", saveString)
                    clipboard.primaryClip = clip
                    hostActivity.goIntoFullScreen()
                }
                .setOnDismissListener {
                    hostActivity.goIntoFullScreen()
                }
                .show()

        }catch (ex: Exception){
            hostActivity.errorHandler.uncaughtException(Thread.currentThread(), ex)
            throw ex
        }
    }
}