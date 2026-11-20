package de.radicalfishgames.crosscode.features

import android.webkit.JavascriptInterface
import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.steam.SteamBridge

/**
 * Sends the game's achievement unlocks to Steam.
 *
 * The desktop build talks to Steam through greenworks, a native node module; the game only calls
 * it when ig.greenworks.greenworks is set. We set it to a small JS object that forwards to
 * [SteamBridge]. The game calls activateAchievement on each new trophy, and its global-save load
 * replays every trophy already earned, so unlocks from before sign-in catch up too.
 */
class SteamFeature(
    gameWrapper: GameWrapper,
    hostActivity: GameActivity,
) : Feature(gameWrapper, hostActivity) {
    override fun onPreGamePageLoad() {
        gameWrapper.exposeJSInterface(JsInterface, "CrossAndroidSteam")
    }

    override fun onPostGamePageLoad() {
        // Install once ig.greenworks and the trophy manager exist, then replay trophies that were
        // already triggered in case the save loaded before the stub was in place.
        runJS(
            """(function(){
                var iv=setInterval(function(){
                    if(typeof ig==='undefined'||!ig.greenworks||typeof sc==='undefined'||!sc.trophyManager||!sc.trophyManager.trophies)return;
                    clearInterval(iv);
                    ig.greenworks.greenworks={
                        activateAchievement:function(id,cb){try{CrossAndroidSteam.unlock(String(id));}catch(e){}if(cb)cb();},
                        clearAchievement:function(id,cb){},
                        initAPI:function(){return true;}
                    };
                    var t=sc.trophyManager.trophies;
                    for(var k in t){if(t[k]&&t[k].triggered&&t[k].steamID)CrossAndroidSteam.unlock(String(t[k].steamID));}
                },500);
            })();""",
        )
    }

    private object JsInterface {
        @JavascriptInterface
        fun unlock(apiName: String) {
            SteamBridge.unlock(apiName)
        }

        @JavascriptInterface
        fun isSignedIn(): Boolean = SteamBridge.isSignedIn
    }
}
