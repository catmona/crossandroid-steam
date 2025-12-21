package de.radicalfishgames.crosscode.features

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.preferences.SaveImportManager
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class ImportExportFeature(
    gameWrapper: GameWrapper,
    hostActivity: GameActivity,
) : Feature(gameWrapper, hostActivity) {
    private val saveImportManager = SaveImportManager(hostActivity)
    private val savesToImport = saveImportManager.consumeAll()
    private val exportSaveEnabled = hostActivity.preferences.getBoolean("export_save", false)

    private var saveStringToExport: String? = null

    override fun onPreGamePageLoad() {
        gameWrapper.exposeJSInterface(this, "CAImportExport")
    }

    override fun onPostGamePageLoad() {
        val importJS =
            if (savesToImport.isNotEmpty()) {
                savesToImport.joinToString("\n") { save ->
                    "ig.storage.pushSlotData(\"${save.trim()}\");"
                }
            } else {
                ""
            }

        val exportJS: String
        if (exportSaveEnabled) {
            exportJS = "CAImportExport.showExportedSave(ig.storage.getLastSlotData());"
        } else {
            exportJS = ""
        }

        runJS(
            """
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
        """,
        )
    }

    @JavascriptInterface
    fun showExportedSave(saveString: String) {
        try {
            MaterialAlertDialogBuilder(hostActivity)
                .setTitle("Exported Save String")
                .setMessage("Your save string is ready! You can save it as a text file now.")
                .setPositiveButton("Save") { _: DialogInterface, _: Int ->
                    saveStringToExport = saveString
                    hostActivity.createDocumentLauncher.launch("crosscode_savestring.txt")
                }.setNegativeButton("Cancel") { _: DialogInterface, _: Int ->
                    hostActivity.goIntoFullScreen()
                }.setOnDismissListener {
                    hostActivity.goIntoFullScreen()
                }.show()
        } catch (ex: Exception) {
            hostActivity.errorHandler.uncaughtException(Thread.currentThread(), ex)
            throw ex
        }
    }

    fun onCreateDocumentResult(uri: Uri?) {
        if (uri != null) {
            try {
                hostActivity.contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).use { stream ->
                        stream.write(saveStringToExport?.toByteArray())
                    }
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        saveStringToExport = null
        hostActivity.goIntoFullScreen()
    }
}
