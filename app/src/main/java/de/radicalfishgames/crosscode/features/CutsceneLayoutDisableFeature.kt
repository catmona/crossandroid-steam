package de.radicalfishgames.crosscode.features

import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager

class CutsceneLayoutDisableFeature(
    gameWrapper: GameWrapper,
    hostActivity: GameActivity,
) : Feature(gameWrapper, hostActivity) {
    private val enabled = hostActivity.preferences.getBoolean("disable_overlay_cutscenes", true)

    override fun onPostGamePageLoad() {
        val virtualControllerFeature = gameWrapper.getFeature<VirtualControllerFeature>()

        if (!virtualControllerFeature.enabled || !enabled) {
            return
        }

        gameWrapper.getFeature<GameEventManager>().apply {
            var inCutscene = false

            onCutsceneEnter {
                virtualControllerFeature.enterDirect()

                inCutscene = true
            }

            onCutsceneExit {
                virtualControllerFeature.leaveDirect()

                inCutscene = false
            }

            onDirectLinkRing {
                if (!inCutscene) {
                    virtualControllerFeature.enterDirect()
                }
            }

            onDirectLinkEnd {
                if (!inCutscene) {
                    virtualControllerFeature.leaveDirect()
                }
            }
        }
    }
}
