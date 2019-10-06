package de.radicalfishgames.crosscode.features

import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper
import de.radicalfishgames.crosscode.gamelisteners.GameEventManager
import de.radicalfishgames.crosscode.mapping.CombatLayoutMapper
import de.radicalfishgames.crosscode.mapping.MenuLayoutMapper


class AutoLayoutSwitchFeature(gameWrapper: GameWrapper,
                              hostActivity: GameActivity
) : Feature(gameWrapper, hostActivity) {

    override val enabled = hostActivity.preferences.getBoolean("switch_layout_automatically", true)

    override fun onPostGamePageLoad() {

        val virtualControllerFeature = gameWrapper.getFeature(VirtualControllerFeature::class)

        if(!virtualControllerFeature.enabled || !enabled){
            return
        }

        gameWrapper.getFeature(GameEventManager::class).apply {

            onMenuEnter.add {
                virtualControllerFeature.switchToLayout(MenuLayoutMapper::class)
            }

            onMenuExit.add {
                virtualControllerFeature.switchToLayout(CombatLayoutMapper::class)
            }
        }

    }
}