package de.radicalfishgames.crosscode.features

import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager
import de.radicalfishgames.crosscode.layouts.DirectLayout


class CutsceneLayoutDisableFeature(gameWrapper: GameWrapper,
                                   hostActivity: GameActivity
) : Feature(gameWrapper, hostActivity) {

    private val enabled = hostActivity.preferences.getBoolean("disable_overlay_cutscenes", true)

    override fun onPostGamePageLoad() {

        val virtualControllerFeature = gameWrapper.getFeature(VirtualControllerFeature::class)

        if(!virtualControllerFeature.enabled || !enabled){
            return
        }

        gameWrapper.getFeature(GameEventManager::class).apply {

            onCutsceneEnter.add {
                DirectLayout.layoutToEnable = virtualControllerFeature.currentLayout
                virtualControllerFeature.switchToLayout(DirectLayout)
            }

            onCutsceneExit.add {
                virtualControllerFeature.switchToLayout(DirectLayout.layoutToEnable)
            }

        }

    }
}