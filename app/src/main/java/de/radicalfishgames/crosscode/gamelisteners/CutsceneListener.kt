package de.radicalfishgames.crosscode.gamelisteners

import android.webkit.JavascriptInterface


class GameStateChange(eventManager: GameEventManager) : GameEventListener(eventManager) {

    override val interfaceName: String = "CACutsceneListener"

    override val requiredModuleName: String = "game.feature.model.game-model"
    override val eventObjectName: String = "model"
    override val eventType: String = "GAME_MODEL_MSG.STATE_CHANGED"

    override val jsToExecute: String =
        """
            $interfaceName.reportGameState(origin.isCutscene());
        """

    @JavascriptInterface
    fun reportGameState(isCutscene: Boolean){
        eventManager.onGameStateChange(isCutscene)
    }

}

class MessageRing(eventManager: GameEventManager) : GameEventListener(eventManager) {

    override val interfaceName: String = "CAMessageRingListener"

    override val requiredModuleName: String = "game.feature.msg.message-model"
    override val eventObjectName: String = "message"
    override val eventType: String = "MESSAGE_EVENT.RING_PRIVATE"

    override val jsToExecute: String =
        """
            $interfaceName.reportRing();
        """

    @JavascriptInterface
    fun reportRing(){
        // Report a message ring as cutscene, since conversations are also cutscenes
        // and direct links are basically the same thing
        eventManager.onGameStateChange(true)
    }

}

class MessageEnd(eventManager: GameEventManager) : GameEventListener(eventManager) {

    override val interfaceName: String = "CAMessageEndListener"

    override val requiredModuleName: String = "game.feature.msg.message-model"
    override val eventObjectName: String = "message"
    override val eventType: String = "MESSAGE_EVENT.END_PRIVATE"

    override val jsToExecute: String =
        """
            $interfaceName.reportMessageEnd();
        """

    @JavascriptInterface
    fun reportMessageEnd(){
        // Report a message ring as cutscene, since conversations are also cutscenes
        // and direct links are basically the same thing
        eventManager.onGameStateChange(false)
    }

}