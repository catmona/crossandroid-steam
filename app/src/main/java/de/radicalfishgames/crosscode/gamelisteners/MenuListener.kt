package de.radicalfishgames.crosscode.gamelisteners

import android.webkit.JavascriptInterface


class MenuEvent(eventManager: GameEventManager) : GameEventListener(eventManager) {

    override val interfaceName: String = "CAMenuListenerFullEnter"

    override val requiredModuleName: String = "game.feature.model.game-model"
    override val eventObjectName: String = "model"
    override val eventType: String = "GAME_MODEL_MSG.SUB_STATE_CHANGED"

    override val onEventJS: String =
        """
            if(origin.isMenu() || origin.isOnMapMenu()){
                $interfaceName.reportMenuOpen();
            }else{
                $interfaceName.reportMenuClosed();
            }
        """

    private var menuIsOpen = false

    @JavascriptInterface
    fun reportMenuOpen(){
        eventManager.onMenuEnter()
        menuIsOpen = true
    }

    @JavascriptInterface
    fun reportMenuClosed(){
        if(menuIsOpen){
            eventManager.onMenuExit()
            menuIsOpen = false
        }
    }
}