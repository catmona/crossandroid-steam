package de.radicalfishgames.crosscode.ui

import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Pads this view by the system bar insets so its content is not drawn under the
 * status/navigation bars when the activity is edge-to-edge.
 */
fun View.applySystemBarInsetsAsPadding(
    top: Boolean = true,
    bottom: Boolean = true,
) {
    val initialTop = paddingTop
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        view.setPadding(
            view.paddingLeft,
            if (top) initialTop + bars.top else view.paddingTop,
            view.paddingRight,
            if (bottom) initialBottom + bars.bottom else view.paddingBottom,
        )
        windowInsets
    }
}

/** Edge-to-edge with light system bar icons, since the app is always dark regardless of the system theme. */
fun ComponentActivity.enableDarkEdgeToEdge() =
    enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
