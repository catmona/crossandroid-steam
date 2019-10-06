package de.radicalfishgames.crosscode.features

import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper


class CutsceneLayoutDisableFeature(gameWrapper: GameWrapper,
                                   hostActivity: GameActivity
) : Feature(gameWrapper, hostActivity) {

    override fun initialize() {

        val virtualControllerFeature = gameWrapper.getFeature(VirtualControllerFeature::class)

        if(!virtualControllerFeature.enabled){
            return
        }

        gameWrapper.events.apply {

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