package de.radicalfishgames.crosscode.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import android.util.SparseArray
import android.util.TypedValue
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.View
import androidx.core.util.contains
import androidx.core.util.containsKey
import androidx.core.util.keyIterator
import androidx.core.util.set
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt


open class CircleButton(context: Context, attrs: AttributeSet) : View(context, attrs) {

    var onTouch: MutableSet<() -> Unit> = HashSet()
    var onMove: MutableSet<(x: Float, y: Float) -> Unit> = HashSet()
    var onRelease: MutableSet<() -> Unit> = HashSet()

    //TODO Make this an attribute
    private val outLineThicknessDp = 4

    protected var radius = 0F

    protected var centerX = 0F
    protected var centerY = 0F

    protected val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        style = Paint.Style.FILL
    }

    protected val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        strokeWidth = outlinePx
    }

    private var outlinePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        outLineThicknessDp.toFloat(),
        resources.displayMetrics
    )

    private val activePointers = SparseArray<PointF>()
    private var mainPointerId: Int? = null

    override fun onDraw(canvas: Canvas) {

        canvas.apply {

            //Draw circle for body
            drawCircle(
                centerX,
                centerY,
                radius,
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

        val xPadding = paddingLeft + paddingRight
        val yPadding = paddingTop + paddingBottom

        val availableWidth = width - xPadding
        val availableHeight = height - yPadding

        radius = (min(availableWidth, availableHeight) / 2).toFloat()

        centerX = (width / 2).toFloat()
        centerY = (height / 2).toFloat()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        var eventPointersAreInCircle = true

        // Update pointer list
        when(event.action){
            // New finger placed on screen
            ACTION_DOWN,
            ACTION_POINTER_DOWN -> {
                val pointer = PointF()

                pointer.x = event.getX(pointerIndex)
                pointer.y = event.getY(pointerIndex)

                activePointers[pointerId] = pointer

                if(!isInsideCircle(pointer.x, pointer.y)){
                    eventPointersAreInCircle = false
                }
            }

            // Finger(s) moved
            ACTION_MOVE -> {
                for(index in 0 until event.pointerCount){
                    val movedId = event.getPointerId(index)

                    // A pointer moved into our View from another View
                    if(!activePointers.contains(movedId)){
                        activePointers[movedId] = PointF()
                    }

                    val pointer = activePointers[movedId]

                    pointer.x = event.getX(index)
                    pointer.y = event.getY(index)

                    if(!isInsideCircle(pointer.x, pointer.y)){
                        eventPointersAreInCircle = false
                    }

                }
            }

            // Finger lifted (or another View has taken over control of the event)
            ACTION_UP,
            ACTION_POINTER_UP,
            ACTION_CANCEL -> {
                activePointers.remove(pointerId)

                if(!isInsideCircle(event.getX(pointerIndex), event.getY(pointerIndex))){
                    eventPointersAreInCircle = false
                }
            }
        }

        // Check if main pointer still exists, is inside the circle, etc.
        val hadOldMainPointer = mainPointerId != null

        val mainPointerStillValid = hadOldMainPointer
                    && activePointers.containsKey(mainPointerId!!)
                    && isInsideCircle(
                        activePointers[mainPointerId!!].x,
                        activePointers[mainPointerId!!].y
                    )

        if(!mainPointerStillValid){
            mainPointerId = null

            //Search for a new pointer within the circle
            for(id in activePointers.keyIterator()){
                val pointer = activePointers[id]

                if(isInsideCircle(pointer.x, pointer.y)){
                    mainPointerId = id
                    break
                }
            }

            // A fresh pointer has been selected
            if(mainPointerId != null && !hadOldMainPointer) {
                onTouch()

                onMove()

            // A new pointer has been selected, but this is not an unpressed button - move
            }else if(mainPointerId != null && hadOldMainPointer){
                onMove()

            // Last pointer moved out of the circle, release
            }else if(hadOldMainPointer){
                onRelease()

            // This event isn't relevant to the view since no pointer is inside and no pointer
            // left the view.
            }else{

                return false
            }

        // All is fine with our main pointer, update its position
        }else{
            onMove()

        }

        return eventPointersAreInCircle
    }

    protected open fun isInsideCircle(xTouch: Float, yTouch: Float): Boolean {

        val xDiff = xTouch - centerX
        val yDiff = yTouch - centerY

        return abs(xDiff) <= radius && abs(yDiff) < radius
    }

    fun setOutlineAlphaPercent(alpha: Double){
        // Actual alpha values are from 0-255
        outlinePaint.alpha = (255 * alpha).roundToInt()
    }

    open fun setBodyAlphaPercent(alpha: Double){
        // Actual alpha values are from 0-255
        bodyPaint.alpha = (255 * alpha).roundToInt()
    }

    private fun onTouch(){
        onTouch.forEach { it.invoke() }
    }

    private fun onMove(){
        onMove.forEach { it.invoke(
            activePointers[mainPointerId!!].x,
            activePointers[mainPointerId!!].y
        ) }
    }

    private fun onRelease(){
        onRelease.forEach { it.invoke() }
    }
}