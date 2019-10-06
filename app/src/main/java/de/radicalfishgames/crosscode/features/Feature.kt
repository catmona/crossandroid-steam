package de.radicalfishgames.crosscode.features

import de.radicalfishgames.crosscode.GameActivity
import de.radicalfishgames.crosscode.GameWrapper


abstract class Feature(protected val gameWrapper: GameWrapper, protected val hostActivity: GameActivity) {

    var enabled = true

    open fun onPreGamePageLoad() {}
    open fun onPostGamePageLoad() {}

    protected fun runJS(js: String) = gameWrapper.runJS(js)
}