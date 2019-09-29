package de.radicalfishgames.crosscode.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.roundToInt


class CircleDirectionalPad(context: Context, attrs: AttributeSet) : CircleButton(context, attrs) {

    var onDirectionUpdate: MutableSet<(xDirection: Double, yDirection: Double, insideInner: Boolean) -> Unit> = HashSet()

    //TODO Make this an attribute
    var innerRadiusPercent = 0.35

    private var innerRadius = 0F

    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }

    init {
        bodyPaint.apply {
            color = Color.YELLOW
        }

        onMove.add { xTouch: Float, yTouch: Float -> updateDirection(xTouch, yTouch) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.apply {
            drawCircle(
                centerX,
                centerY,
                innerRadius,
                innerPaint
            )
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        innerRadius = (radius * innerRadiusPercent).toFloat()
    }

    private fun updateDirection(xTouch: Float, yTouch: Float){
        val xDiff = xTouch - centerX
        val yDiff = yTouch - centerY

        // Return the values as values ranging from -1.0 to 1.0
        val xDir = xDiff / radius
        val yDir = yDiff / radius

        onDirectionUpdate.forEach { it.invoke(xDir.toDouble(), yDir.toDouble(), isInsideInnerCircle(xTouch, yTouch)) }
    }

    private fun isInsideInnerCircle(xTouch: Float, yTouch: Float): Boolean{
        val xDiff = xTouch - centerX
        val yDiff = yTouch - centerY

        return abs(xDiff) <= innerRadius && abs(yDiff) <= innerRadius
    }

    override fun setBodyAlphaPercent(alpha: Double) {
        super.setBodyAlphaPercent(alpha)
        // Actual alpha values are from 0-255
        innerPaint.alpha = (255 * alpha).roundToInt()
    }
}