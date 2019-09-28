package de.radicalfishgames.crosscode.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import kotlin.math.abs


class RingDirectionalPad(context: Context, attrs: AttributeSet) : CircleButton(context, attrs) {

    var onDirectionUpdate: MutableSet<(xDirection: Double, yDirection: Double) -> Unit> = HashSet()

    //TODO Make this an attribute
    private val holeRadiusPercent = 0.35

    private var holeRadius = 0F
    private var ringMidRadius = 0F

    init {
        bodyPaint.apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
        }

        onMove.add { xTouch: Float, yTouch: Float -> updateDirection(xTouch, yTouch) }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.apply {

            //Draw ring for body
            drawCircle(
                centerX,
                centerY,
                ringMidRadius,
                bodyPaint
            )

            //Draw line for the outline
            drawCircle(
                centerX,
                centerY,
                radius,
                outlinePaint
            )
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        holeRadius = (radius * holeRadiusPercent).toFloat()

        // Do this here because the values are 0 during initialization
        val ringWidth = radius - holeRadius
        bodyPaint.strokeWidth = ringWidth
        ringMidRadius = radius - ringWidth / 2
    }

    private fun updateDirection(xTouch: Float, yTouch: Float){

        val xDiff = xTouch - centerX
        val yDiff = yTouch - centerY

        // Return the values as values ranging from -1.0 to 1.0
        val xDir = xDiff / radius
        val yDir = yDiff / radius

        onDirectionUpdate.forEach { it.invoke(xDir.toDouble(), yDir.toDouble()) }
    }

    override fun isInsideCircle(xTouch: Float, yTouch: Float): Boolean{
        val xDiff = xTouch - centerX
        val yDiff = yTouch - centerY

        val insideHole = abs(xDiff) <= holeRadius && abs(yDiff) < holeRadius

        return super.isInsideCircle(xTouch, yTouch) && !insideHole
    }
}