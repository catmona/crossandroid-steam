package de.radicalfishgames.crosscode.gamelisteners

import android.webkit.JavascriptInterface

class GameStart(eventManager: GameEventManager) : GameEventListener(eventManager) {

    override val interfaceName: String = "CARunningListener"

    override val requiredModuleName: String = "game.feature.model.game-model"
    override val eventObjectName: String = "model"
    override val eventType: String = "GAME_MODEL_MSG.STATE_CHANGED"

    override val onEventJS: String =
        """
            if(origin.isGame()){
                $interfaceName.reportGameState();
            }
        """

    @JavascriptInterface
    fun reportGameState(){
        eventManager.onGameStart()
    }
}