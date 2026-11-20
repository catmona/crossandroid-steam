package de.radicalfishgames.crosscode.features

import android.util.Log
import android.webkit.JavascriptInterface
import androidx.appcompat.app.AlertDialog
import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.R
import de.radicalfishgames.crosscode.steam.SteamBridge
import org.json.JSONArray
import org.json.JSONException

/**
 * Sends the game's achievement unlocks to Steam.
 *
 * The desktop build talks to Steam through greenworks, a native node module; the game only calls
 * it when `ig.greenworks.greenworks` is set (`ig.greenworks.isActive()` is just `!!greenworks`).
 * We set it to a small JS object that forwards to [SteamBridge]. The trophy manager is the global
 * `sc.trophies`. It calls activateAchievement on each new trophy, and its global-save load
 * (`onStorageGlobalLoad`) replays every trophy already earned, so unlocks from before sign-in catch
 * up too. Replays are flagged as not live, so only trophies earned right now get a pop-up.
 *
 * Before the hook goes in, the page sends the steamIDs of all the game's trophies; until the Steam
 * schema is known, [SteamBridge] refuses any other id (after that, the schema decides).
 * After a full save import, the replays are held until the user answers a
 * "sync these to Steam?" dialog.
 */
class SteamFeature(
    gameWrapper: GameWrapper,
    hostActivity: GameActivity,
) : Feature(gameWrapper, hostActivity) {
    private var importDialog: AlertDialog? = null

    override fun onPreGamePageLoad() {
        gameWrapper.exposeJSInterface(JsInterface(), "CrossAndroidSteam")
    }

    override fun onPostGamePageLoad() {
        runJS(
            """(function(){
                var iv=setInterval(function(){
                    if(typeof ig==='undefined'||!ig.greenworks||typeof sc==='undefined'||!sc.trophies||!sc.trophies.trophies)return;
                    clearInterval(iv);
                    var tm=sc.trophies, replaying=false;
                    var ids=[];
                    for(var k in tm.trophies){if(tm.trophies[k]&&tm.trophies[k].steamID)ids.push(String(tm.trophies[k].steamID));}
                    CrossAndroidSteam.setTrophyIds(JSON.stringify(ids));
                    var origLoad=tm.onStorageGlobalLoad;
                    tm.onStorageGlobalLoad=function(){replaying=true;try{return origLoad.apply(this,arguments);}finally{replaying=false;CrossAndroidSteam.replayDone();}};
                    function nameOf(steamID){
                        try{for(var k in tm.trophies){if(tm.trophies[k].steamID==steamID)return String(tm.getTrophyName(k)).replace(/\\[a-z]\[[^\]]*\]/gi,'');}}catch(e){}
                        return '';
                    }
                    ig.greenworks.greenworks={
                        activateAchievement:function(id,cb){try{CrossAndroidSteam.unlock(String(id),!replaying,nameOf(id));}catch(e){}if(cb)cb();},
                        clearAchievement:function(id,cb){},
                        initAPI:function(){return true;},
                        init:function(){return true;}
                    };
                    var t=tm.trophies,n=0;
                    for(var k in t){if(t[k]&&t[k].triggered&&t[k].steamID){CrossAndroidSteam.unlock(String(t[k].steamID),false,'');n++;}}
                    CrossAndroidSteam.replayDone();
                    console.log('CrossAndroidSteam: achievement hook installed, '+n+' earned trophies sent');
                },250);
            })();""",
        )
    }

    override fun onDestroy() {
        // The dialog would otherwise leak the activity's window; the held replays stay held and
        // the question comes back on the next replay
        importDialog?.dismiss()
        importDialog = null
    }

    /** Asks whether the achievements of an imported save go to Steam. Main thread only. */
    private fun askAboutImport(count: Int) {
        if (hostActivity.isFinishing || hostActivity.isDestroyed || importDialog?.isShowing == true) return
        importDialog =
            AlertDialog
                .Builder(hostActivity)
                .setTitle(R.string.steam_import_sync_title)
                .setMessage(hostActivity.resources.getQuantityString(R.plurals.steam_import_sync_message, count, count))
                .setPositiveButton(R.string.steam_import_sync_yes) { _, _ -> SteamBridge.resolveImportedReplays(true) }
                .setNegativeButton(R.string.steam_import_sync_no) { _, _ -> SteamBridge.resolveImportedReplays(false) }
                .setCancelable(false)
                .show()
    }

    /** One per page load, so the trophy list can only be set once by that page. */
    private inner class JsInterface {
        private var trophiesSet = false

        /** [live] is false for trophies the game replays from the save; [gameName] is the game's own title for it. */
        @JavascriptInterface
        fun unlock(
            apiName: String,
            live: Boolean,
            gameName: String,
        ) {
            SteamBridge.unlock(apiName, live, gameName.ifBlank { null })
        }

        /** [json] is a JSON array of the steamIDs of the game's trophies. */
        @JavascriptInterface
        fun setTrophyIds(json: String) {
            if (trophiesSet) return
            trophiesSet = true
            try {
                val array = JSONArray(json)
                SteamBridge.setTrophyIds((0 until array.length()).map { array.getString(it) }.toSet())
            } catch (e: JSONException) {
                Log.w(TAG, "Trophy id list from the page isn't valid JSON")
            }
        }

        /** The game finished replaying the save's trophies. */
        @JavascriptInterface
        fun replayDone() {
            SteamBridge.afterReplay { count -> hostActivity.runOnUiThread { askAboutImport(count) } }
        }

        @JavascriptInterface
        fun isSignedIn(): Boolean = SteamBridge.isSignedIn
    }

    companion object {
        private const val TAG = "CrossAndroidSteam"
    }
}
