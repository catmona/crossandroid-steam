package de.radicalfishgames.crosscode.gamelisteners

import android.os.Handler
import android.os.Looper
import android.util.Log
import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.features.Feature

class GameEventManager(
    gameWrapper: GameWrapper,
    hostActivity: GameActivity,
) : Feature(gameWrapper, hostActivity) {
    private val listeners =
        listOf(
            MenuFullEnter(this),
            MenuExit(this),
            TradeMenu(this),
            GameStateChangeCutscene(this),
            MessageRing(this),
            MessageEnd(this),
            ElementModeChange(this),
            ElementLoad(this),
        )

    private val onMenuEnter: MutableSet<() -> Unit> = HashSet()
    private val onMenuExit: MutableSet<() -> Unit> = HashSet()

    private val onCutsceneEnter: MutableSet<() -> Unit> = HashSet()
    private val onCutsceneExit: MutableSet<() -> Unit> = HashSet()

    private val onDirectLinkRing: MutableSet<() -> Unit> = HashSet()
    private val onDirectLinkEnd: MutableSet<() -> Unit> = HashSet()

    private val onElementChange: MutableSet<(element: ElementMode) -> Unit> = HashSet()

    // Post events to the original thread instead of staying in the JS one, so
    // the event handlers can modify the View
    private val handler = Handler(Looper.getMainLooper())

    override fun onPreGamePageLoad() {
        for (listener in listeners) {
            gameWrapper.exposeJSInterface(listener, listener.interfaceName)
        }
    }

    override fun onPostGamePageLoad() {
        Log.d("CrossCode", "Injecting JS listeners...")

        val modulesAddedAlready = mutableSetOf<String>()
        val observerObjectsAddedAlready = mutableSetOf<String>()

        val requiredModulesJS =
            buildString {
                for (listener in listeners) {
                    if (modulesAddedAlready.add(listener.requiredModuleName)) {
                        append(".requires(\"${listener.requiredModuleName}\")")
                    }
                }
            }

        val observerRegistrationJS =
            buildString {
                for (listener in listeners) {
                    if (observerObjectsAddedAlready.add(listener.eventObjectName)) {
                        append("sc.Model.addObserver(sc.${listener.eventObjectName}, this);")
                    }
                }
            }

        val eventIfsJS =
            buildString {
                for (listener in listeners) {
                    append(
                        "if(origin === sc.${listener.eventObjectName} && eventNumber === sc.${listener.eventType}) { ${listener.onEventJS} }",
                    )
                }
            }

        val jsForAllListeners =
            """
            ig.module("crossandroid.events.listeners")$requiredModulesJS.defines(function(){
                sc.CrossAndroidEventListener = ig.GameAddon.extend({
                    init: function() {
                        $observerRegistrationJS
                    },

                    modelChanged(origin, eventNumber, optionalArgs){
                        $eventIfsJS
                    }
                });
                ig.addGameAddon(function() {
                    return sc.crossAndroidEventListener = new sc.CrossAndroidEventListener
                })
            })
            """.trimIndent().replace("\n", "")

        runJS(jsForAllListeners)
    }

    fun onMenuEnter(run: () -> Unit) = onMenuEnter.add(run)
    internal fun onMenuEnter() = fireEvent(onMenuEnter) { it() }

    fun onMenuExit(run: () -> Unit) = onMenuExit.add(run)
    internal fun onMenuExit() = fireEvent(onMenuExit) { it() }

    fun onCutsceneEnter(run: () -> Unit) = onCutsceneEnter.add(run)
    internal fun onCutsceneEnter() = fireEvent(onCutsceneEnter) { it() }

    fun onCutsceneExit(run: () -> Unit) = onCutsceneExit.add(run)
    internal fun onCutsceneExit() = fireEvent(onCutsceneExit) { it() }

    fun onDirectLinkRing(run: () -> Unit) = onDirectLinkRing.add(run)
    internal fun onDirectLinkRing() = fireEvent(onDirectLinkRing) { it() }

    fun onDirectLinkEnd(run: () -> Unit) = onDirectLinkEnd.add(run)
    internal fun onDirectLinkEnd() = fireEvent(onDirectLinkEnd) { it() }

    fun onElementChange(run: (element: ElementMode) -> Unit) = onElementChange.add(run)
    internal fun onElementModeChange(elementMode: ElementMode) = fireEvent(onElementChange) { it(elementMode) }

    private fun <T> fireEvent(listeners: Set<T>, invoke: (T) -> Unit) {
        handler.post { listeners.forEach(invoke) }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        listOf(onMenuEnter, onMenuExit, onCutsceneEnter, onCutsceneExit, onDirectLinkRing, onDirectLinkEnd, onElementChange)
            .forEach { it.clear() }
    }
}
