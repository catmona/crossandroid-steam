package de.radicalfishgames.crosscode.overlay

import android.content.Context
import android.util.AttributeSet
import kotlin.math.abs
import kotlin.math.min

open class CircleButton(
    context: Context,
    attrs: AttributeSet,
) : ControlView(context, attrs) {
    protected var radius = 0F

    protected var centerX = 0F
    protected var centerY = 0F

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        val xPadding = paddingLeft + paddingRight
        val yPadding = paddingTop + paddingBottom

        val availableWidth = width - xPadding
        val availableHeight = height - yPadding

        radius = (min(availableWidth, availableHeight) / 2).toFloat()

        centerX = (width / 2).toFloat()
        centerY = (height / 2).toFloat()
    }

    override fun isWithinView(
        x: Float,
        y: Float,
    ) = isInsideCircle(x, y)

    protected open fun isInsideCircle(
        xTouch: Float,
        yTouch: Float,
    ): Boolean {
        val xDiff = xTouch - centerX
        val yDiff = yTouch - centerY

        return abs(xDiff) <= radius && abs(yDiff) < radius
    }
}
