package de.radicalfishgames.crosscode.features

import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager


class CutsceneLayoutDisableFeature(gameWrapper: GameWrapper,
                                   hostActivity: GameActivity
) : Feature(gameWrapper, hostActivity) {

    override val enabled = hostActivity.preferences.getBoolean("disable_overlay_cutscenes", true)

    override fun onPostGamePageLoad() {

        val virtualControllerFeature = gameWrapper.getFeature(VirtualControllerFeature::class)

        if(!virtualControllerFeature.enabled || !enabled){
            return
        }

        gameWrapper.getFeature(GameEventManager::class).apply {

            onCutsceneEnter.add {
                virtualControllerFeature.currentController.disableOverlay()
            }

            onCutsceneExit.add {
                virtualControllerFeature.currentController.enableOverlay()
            }

        }

    }
}