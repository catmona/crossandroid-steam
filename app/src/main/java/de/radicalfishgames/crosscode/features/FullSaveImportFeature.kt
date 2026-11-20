package de.radicalfishgames.crosscode.features

import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.webkit.WebViewFeature
import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.steam.SteamBridge
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Replaces the whole save (every slot plus global data: trophies, settings, unlocks) with a
 * desktop `cc.save` file placed at `<app files>/import/cc.save`, e.g. copied from a PC or from
 * another launcher. The browser build keeps its save as the same string in localStorage["cc.save"],
 * so the file goes in unchanged before the game's scripts run. The old save is kept next to it.
 */
class FullSaveImportFeature(
    gameWrapper: GameWrapper,
    hostActivity: GameActivity,
) : Feature(gameWrapper, hostActivity) {
    private val importDir = File(hostActivity.getExternalFilesDir(null), "import")
    private val importFile = File(importDir, "cc.save")

    @Volatile private var pendingSave: String? = null

    override fun onPreGamePageLoad() {
        if (!importFile.isFile) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Log.w(TAG, "Full save import needs document-start scripts, which this WebView lacks")
            return
        }

        val save = importFile.readText().trim()
        if (!looksLikeSave(save)) {
            Log.w(TAG, "Ignoring ${importFile.path}: not a CrossCode save")
            Toast.makeText(hostActivity, R.string.import_full_save_invalid, Toast.LENGTH_LONG).show()
            return
        }
        pendingSave = save

        gameWrapper.exposeJSInterface(JsInterface(), "CrossAndroidSaveImport")
        gameWrapper.addDocumentStartScript(
            """(function(){
                if(window!==window.top||!window.CrossAndroidSaveImport)return;
                var save=CrossAndroidSaveImport.take();
                if(!save)return;
                var old=localStorage.getItem('cc.save');
                if(old)CrossAndroidSaveImport.backup(old);
                localStorage.setItem('cc.save',save);
                console.log('CrossAndroid: full save imported');
            })();""",
        )
    }

    private inner class JsInterface {
        /** Hands the save over once and marks the file as imported. */
        @JavascriptInterface
        fun take(): String? {
            val save = pendingSave ?: return null
            pendingSave = null
            // The imported save replays its trophies on load; ask before they go to Steam
            SteamBridge.holdReplaysForImport()
            val done = File(importDir, "cc.save.imported-${stamp()}")
            if (!importFile.renameTo(done)) importFile.delete()
            hostActivity.runOnUiThread {
                Toast.makeText(hostActivity, R.string.import_full_save_done, Toast.LENGTH_LONG).show()
            }
            Log.i(TAG, "Full save imported; file moved to ${done.name}")
            return save
        }

        @JavascriptInterface
        fun backup(old: String) {
            File(importDir, "cc.save.before-import-${stamp()}").writeText(old)
        }
    }

    /** A desktop cc.save is a JSON object of encrypted slots and globals; a bare encrypted string also loads. */
    private fun looksLikeSave(save: String): Boolean {
        if (save.startsWith(SAVE_PREFIX)) return true
        return try {
            val json = JSONObject(save)
            json.has("slots") || json.has("globals")
        } catch (e: JSONException) {
            false
        }
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    companion object {
        private const val TAG = "CrossCode"
        private const val SAVE_PREFIX = "[-!_0_!-]"
    }
}
