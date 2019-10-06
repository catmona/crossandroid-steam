package de.radicalfishgames.crosscode.gamelisteners

import android.os.Handler


class GameEventManager {

    val listeners = listOf(
        MenuFullEnter(this),
        MenuExit(this),
        GameStateChangeCutscene(this),
        MessageRing(this),
        MessageEnd(this),
        ElementModeChange(this),
        ElementLoad(this)
    )

    val onMenuEnter: MutableSet<() -> Unit> = HashSet()
    val onMenuExit: MutableSet<() -> Unit> = HashSet()

    val onCutsceneEnter: MutableSet<() -> Unit> = HashSet()
    val onCutsceneExit: MutableSet<() -> Unit> = HashSet()

    val onElementChange: MutableSet<(element: ElementMode) -> Unit> = HashSet()

    private var wasLastGameStateCutscene = false
    // Post events to the original thread instead of staying in the JS one, so
    // the event handlers can modify the View
    private val handler = Handler()

    fun onMenuEnter(){
        handler.post {
            onMenuEnter.forEach {
                it.invoke()
            }
        }
    }

    fun onMenuExit(){
        handler.post {
            onMenuExit.forEach {
                it.invoke()
            }
        }
    }

    fun onCutsceneStateChange(isCutscene: Boolean){
        handler.post {
            if(isCutscene != wasLastGameStateCutscene){
                wasLastGameStateCutscene = isCutscene

                if(isCutscene){
                    onCutsceneEnter.forEach {
                        it.invoke()
                    }
                }else if(!isCutscene){
                    onCutsceneExit.forEach {
                        it.invoke()
                    }
                }
            }
        }
    }

    fun onElementModeChange(elementMode: ElementMode){
        handler.post {
            onElementChange.forEach {
                it.invoke(elementMode)
            }
        }
    }

    fun clearListeners(){
        onMenuEnter.clear()
        onMenuExit.clear()
        onCutsceneEnter.clear()
        onCutsceneExit.clear()
    }
}