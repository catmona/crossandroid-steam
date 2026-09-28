package de.radicalfishgames.crosscode.overlay

import android.content.Context
import android.util.AttributeSet

class CircleDirectionalPad(
    context: Context,
    attrs: AttributeSet,
) : CircleButton(context, attrs) {
    private val onDirectionUpdate: MutableSet<(xDirection: Double, yDirection: Double) -> Unit> = HashSet()

    init {
        onMove { xTouch: Float, yTouch: Float -> updateDirection(xTouch, yTouch) }
    }

    fun onDirectionUpdate(run: (xDirection: Double, yDirection: Double) -> Unit) = onDirectionUpdate.add(run)

    private fun updateDirection(
        xTouch: Float,
        yTouch: Float,
    ) {
        val xDiff = xTouch - centerX
        val yDiff = yTouch - centerY

        // Normalize to -1.0..1.0 relative to the pad radius
        val xDir = xDiff / radius
        val yDir = yDiff / radius

        onDirectionUpdate.forEach { it.invoke(xDir.toDouble(), yDir.toDouble()) }
    }
}
