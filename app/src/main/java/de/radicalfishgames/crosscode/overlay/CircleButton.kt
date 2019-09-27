package de.radicalfishgames.crosscode.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt


open class CircleButton(context: Context, attrs: AttributeSet) : View(context, attrs) {

    var onTouch: () -> Unit = {}
    var onRelease: () -> Unit = {}

    protected var totalRadius = 0F
    protected var innerRadius = 0F

    protected var centerX = 0F
    protected var centerY = 0F

    protected val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        style = Paint.Style.FILL
    }

    protected val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        style = Paint.Style.FILL
    }

    private var outlinePx: Float

    init {
        outlinePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            OUTLINE_THICKNESS_DP.toFloat(),
            resources.displayMetrics
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.apply {

            //Draw circle for outline
            drawCircle(
                centerX,
                centerY,
                totalRadius,
                outlinePaint
            )

            //Draw circle for body
            drawCircle(
                centerX,
                centerY,
                innerRadius,
                bodyPaint
            )
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        val xPadding = paddingLeft + paddingRight
        val yPadding = paddingTop + paddingBottom

        val availableWidth = width - xPadding
        val availableHeight = height - yPadding

        totalRadius = (min(availableWidth, availableHeight) / 2).toFloat()
        innerRadius = totalRadius - outlinePx

        centerX = (width / 2).toFloat()
        centerY = (height / 2).toFloat()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        if(!isInsideCircle(event.x, event.y)){
            onRelease()
            return false
        }

        if(event.action == MotionEvent.ACTION_DOWN){
            onTouch()

        }else if(event.action == MotionEvent.ACTION_UP){
            onRelease()
        }

        return true
    }

    protected fun isInsideCircle(xTouch: Float, yTouch: Float): Boolean {

        val xDiff = xTouch - centerX
        val yDiff = yTouch - centerY

        return abs(xDiff) <= totalRadius && abs(yDiff) < totalRadius
    }

    fun setOutlineAlphaPercent(alpha: Double){
        // Actual alpha values are from 0-255
        outlinePaint.alpha = (255 * alpha).roundToInt()
    }

    fun setBodyAlphaPercent(alpha: Double){
        // Actual alpha values are from 0-255
        bodyPaint.alpha = (255 * alpha).roundToInt()
    }

    companion object {
        const val OUTLINE_THICKNESS_DP = 2
    }
}