package de.radicalfishgames.crosscode.overlay

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent


class DirectionalPad(context: Context, attrs: AttributeSet) : CircleButton(context, attrs) {

    var onDirectionUpdate: MutableSet<(xDirection: Double, yDirection: Double) -> Unit> = HashSet()

    init {
        bodyPaint.apply {
            color = Color.YELLOW
        }

        onMove.add { xTouch: Float, yTouch: Float -> updateDirection(xTouch, yTouch) }
    }

    private fun updateDirection(xTouch: Float, yTouch: Float){
        val xDiff = xTouch - centerX
        val yDiff = yTouch - centerY

        // Return the values as values ranging from -1.0 to 1.0
        val xDir = xDiff / radius
        val yDir = yDiff / radius

        onDirectionUpdate.forEach { it.invoke(xDir.toDouble(), yDir.toDouble()) }
    }
}