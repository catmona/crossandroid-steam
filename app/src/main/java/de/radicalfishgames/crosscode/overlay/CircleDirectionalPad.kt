package de.radicalfishgames.crosscode.overlay

import android.content.Context
import android.util.AttributeSet
import kotlin.math.abs

class CircleDirectionalPad(
    context: Context,
    attrs: AttributeSet,
) : CircleButton(context, attrs) {
    private val onDirectionUpdate: MutableSet<(xDirection: Double, yDirection: Double) -> Unit> = HashSet()

    init {
        onMove { xTouch: Float, yTouch: Float -> updateDirection(xTouch, yTouch) }
    }

    // Keep the bounding square so a finger dragged diagonally past the rim still holds the pad
    // at full tilt instead of dropping it
    override fun isWithinView(
        x: Float,
        y: Float,
    ) = abs(x - centerX) <= radius && abs(y - centerY) <= radius

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
