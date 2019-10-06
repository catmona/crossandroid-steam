package de.radicalfishgames.crosscode.features

import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager
import de.radicalfishgames.crosscode.mapping.CombatLayoutMapper
import de.radicalfishgames.crosscode.mapping.MenuLayoutMapper


class AutoLayoutSwitchFeature(gameWrapper: GameWrapper,
                              hostActivity: GameActivity
) : Feature(gameWrapper, hostActivity) {

    override fun onPostGamePageLoad() {

        val virtualControllerFeature = gameWrapper.getFeature(VirtualControllerFeature::class)

        if(!virtualControllerFeature.enabled){
            return
        }

        gameWrapper.getFeature(GameEventManager::class).apply {

            onMenuEnter.add {
                if(enabled){
                    virtualControllerFeature.switchToLayout(MenuLayoutMapper::class)
                }
            }

            onMenuExit.add {
                if(enabled){
                    virtualControllerFeature.switchToLayout(CombatLayoutMapper::class)
                }
            }
        }

    }
}