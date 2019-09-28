package de.radicalfishgames.crosscode.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import androidx.core.util.contains
import androidx.core.util.containsKey
import androidx.core.util.keyIterator
import androidx.core.util.set


class OverlayToggle(context: Context, attrs: AttributeSet) : View(context, attrs) {

    var onTouch: MutableSet<() -> Unit> = HashSet()
    var onRelease: MutableSet<() -> Unit> = HashSet()

    private val activePointers = SparseArray<PointF>()
    private var mainPointerId: Int? = null

    override fun onDraw(canvas: Canvas) {

        canvas.apply {

            drawColor(Color.RED)
        }
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {

        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        // Update pointer list
        when(event.action){
            // New finger placed on screen
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointer = PointF()

                pointer.x = event.getX(pointerIndex)
                pointer.y = event.getY(pointerIndex)

                activePointers[pointerId] = pointer
            }

            // Finger(s) moved
            MotionEvent.ACTION_MOVE -> {
                for(index in 0 until event.pointerCount){
                    val movedId = event.getPointerId(index)
                    if(activePointers.contains(movedId)){
                        val pointer = activePointers[movedId]
                        pointer.x = event.getX(index)
                        pointer.y = event.getY(index)
                    }

                }
            }

            // Finger lifted (or another View has taken over control of the event)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                activePointers.remove(pointerId)
            }
        }

        // Check if main pointer still exists, is inside the circle, etc.
        val hadOldMainPointer = mainPointerId != null

        val mainPointerStillValid = hadOldMainPointer
                && activePointers.containsKey(mainPointerId!!)

        if(!mainPointerStillValid){
            mainPointerId = null

            //Search for a new pointer within the circle
            for(id in activePointers.keyIterator()){
                mainPointerId = id
                break
            }

            // A fresh pointer has been selected
            if(mainPointerId != null && !hadOldMainPointer) {
                onTouch()

            // Last pointer moved out of the circle, release
            }else if(hadOldMainPointer){
                onRelease()

                // This event isn't relevant to the view since no pointer is inside and no pointer
                // left the view.
            }else{

                return false
            }

        // All is fine with our main pointer, do nothing
        }

        return true
    }

    private fun onTouch(){
        onTouch.forEach { it.invoke() }
    }

    private fun onRelease(){
        onRelease.forEach { it.invoke() }
    }

    fun setBodyAlphaPercent(alpha: Double){
        setAlpha(alpha.toFloat())
    }
}