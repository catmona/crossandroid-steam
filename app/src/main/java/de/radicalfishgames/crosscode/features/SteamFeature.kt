package de.radicalfishgames.crosscode.features

import android.webkit.JavascriptInterface
import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.steam.SteamBridge

/**
 * Sends the game's achievement unlocks to Steam.
 *
 * The desktop build talks to Steam through greenworks, a native node module; the game only calls
 * it when `ig.greenworks.greenworks` is set (`ig.greenworks.isActive()` is just `!!greenworks`).
 * We set it to a small JS object that forwards to [SteamBridge]. The trophy manager is the global
 * `sc.trophies`. It calls activateAchievement on each new trophy, and its global-save load
 * (`onStorageGlobalLoad`) replays every trophy already earned, so unlocks from before sign-in catch
 * up too. Replays are flagged as not live, so only trophies earned right now get a pop-up.
 */
class SteamFeature(
    gameWrapper: GameWrapper,
    hostActivity: GameActivity,
) : Feature(gameWrapper, hostActivity) {
    override fun onPreGamePageLoad() {
        gameWrapper.exposeJSInterface(JsInterface, "CrossAndroidSteam")
    }

    override fun onPostGamePageLoad() {
        runJS(
            """(function(){
                var iv=setInterval(function(){
                    if(typeof ig==='undefined'||!ig.greenworks||typeof sc==='undefined'||!sc.trophies||!sc.trophies.trophies)return;
                    clearInterval(iv);
                    var tm=sc.trophies, replaying=false;
                    var origLoad=tm.onStorageGlobalLoad;
                    tm.onStorageGlobalLoad=function(){replaying=true;try{return origLoad.apply(this,arguments);}finally{replaying=false;}};
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
                    console.log('CrossAndroidSteam: achievement hook installed, '+n+' earned trophies sent');
                },250);
            })();""",
        )
    }

    private object JsInterface {
        /** [live] is false for trophies the game replays from the save; [gameName] is the game's own title for it. */
        @JavascriptInterface
        fun unlock(
            apiName: String,
            live: Boolean,
            gameName: String,
        ) {
            SteamBridge.unlock(apiName, live, gameName.ifBlank { null })
        }

        @JavascriptInterface
        fun isSignedIn(): Boolean = SteamBridge.isSignedIn
    }
}
