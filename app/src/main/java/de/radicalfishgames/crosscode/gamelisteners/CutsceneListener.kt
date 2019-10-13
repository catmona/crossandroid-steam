package de.radicalfishgames.crosscode.gamelisteners

import android.webkit.JavascriptInterface


class GameStateChangeCutscene(eventManager: GameEventManager) : GameEventListener(eventManager) {

    override val interfaceName: String = "CACutsceneListener"

    override val requiredModuleName: String = "game.feature.model.game-model"
    override val eventObjectName: String = "model"
    override val eventType: String = "GAME_MODEL_MSG.STATE_CHANGED"

    override val onEventJS: String =
        """
            $interfaceName.reportGameState(origin.isCutscene());
        """

    @JavascriptInterface
    fun reportGameState(isCutscene: Boolean){
        eventManager.onCutsceneStateChange(isCutscene)
    }

}

class MessageRing(eventManager: GameEventManager) : GameEventListener(eventManager) {

    override val interfaceName: String = "CAMessageRingListener"

    override val requiredModuleName: String = "game.feature.msg.message-model"
    override val eventObjectName: String = "message"
    override val eventType: String = "MESSAGE_EVENT.RING_PRIVATE"

    override val onEventJS: String =
        """
            $interfaceName.reportRing();
        """

    @JavascriptInterface
    fun reportRing(){
        // Report a message ring
        eventManager.onDirectLinkStateChange(true)
    }

}

class MessageEnd(eventManager: GameEventManager) : GameEventListener(eventManager) {

    override val interfaceName: String = "CAMessageEndListener"

    override val requiredModuleName: String = "game.feature.msg.message-model"
    override val eventObjectName: String = "message"
    override val eventType: String = "MESSAGE_EVENT.END_PRIVATE"

    override val onEventJS: String =
        """
            $interfaceName.reportMessageEnd();
        """

    @JavascriptInterface
    fun reportMessageEnd(){
        // Report a message end
        eventManager.onDirectLinkStateChange(false)
    }

}