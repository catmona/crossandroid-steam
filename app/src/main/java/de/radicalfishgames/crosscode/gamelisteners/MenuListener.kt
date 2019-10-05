package de.radicalfishgames.crosscode.gamelisteners

import android.webkit.JavascriptInterface


class MenuFullEnter(eventManager: GameEventManager) : GameEventListener(eventManager) {

    override val interfaceName: String = "CAMenuListenerFullEnter"

    override val requiredModuleName: String = "game.feature.menu.menu-model"
    override val eventObjectName: String = "menu"
    override val eventType: String = "MENU_EVENT.FULL_MENU_ENTER"

    override val jsToExecute: String =
        """
            $interfaceName.reportFullMenuOpen();
        """

    @JavascriptInterface
    fun reportFullMenuOpen(){
        eventManager.onMenuEnter()
    }
}

class MenuExit(eventManager: GameEventManager) : GameEventListener(eventManager) {

    override val interfaceName: String = "CAMenuListenerExit"

    override val requiredModuleName: String = "game.feature.menu.menu-model"
    override val eventObjectName: String = "menu"
    override val eventType: String = "MENU_EVENT.EXIT_MENU"

    override val jsToExecute: String =
        """
            $interfaceName.reportMenuExit();
        """

    @JavascriptInterface
    fun reportMenuExit(){
        eventManager.onMenuExit()
    }
}