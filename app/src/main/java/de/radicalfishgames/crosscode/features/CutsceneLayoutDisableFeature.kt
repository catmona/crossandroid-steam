package de.radicalfishgames.crosscode.features

import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager


class CutsceneLayoutDisableFeature(gameWrapper: GameWrapper,
                                   hostActivity: GameActivity
) : Feature(gameWrapper, hostActivity) {

    override fun onPostGamePageLoad() {

        val virtualControllerFeature = gameWrapper.getFeature(VirtualControllerFeature::class)

        if(!virtualControllerFeature.enabled){
            return
        }

        gameWrapper.getFeature(GameEventManager::class).apply {

            onCutsceneEnter.add {
                if(enabled){
                    virtualControllerFeature.currentController.disableOverlay()
                }
            }

            onCutsceneExit.add {
                if(enabled){
                    virtualControllerFeature.currentController.enableOverlay()
                }
            }

        }

    }
}