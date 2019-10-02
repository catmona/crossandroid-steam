package de.radicalfishgames.crosscode.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import androidx.core.util.contains
import androidx.core.util.containsKey
import androidx.core.util.keyIterator
import androidx.core.util.set
import de.radicalfishgames.crosscode.R


open class ControlView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    var onTouch: MutableSet<() -> Unit> = HashSet()
    var onMove: MutableSet<(x: Float, y: Float) -> Unit> = HashSet()
    var onRelease: MutableSet<() -> Unit> = HashSet()

    private val imagePaint = Paint().apply {
        isDither = false
        isAntiAlias = false
    }


    private lateinit var fullSizeRect: RectF
    var imageBitmap: Bitmap

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.ControlView, 0, 0).apply {

            try {
                val resId = getResourceId(R.styleable.ControlView_image, -1)
                imageBitmap = BitmapFactory.decodeResource(context.resources, resId)
            } finally {
                recycle()
            }
        }

    }

    private val activePointers = SparseArray<PointF>()
    private var mainPointerId: Int? = null

    override fun onDraw(canvas: Canvas) {

        canvas.apply {

            drawBitmap(
                imageBitmap,
                null,
                fullSizeRect,
                imagePaint
            )
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        fullSizeRect = RectF(0F, 0F, width.toFloat(), height.toFloat())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        var eventPointersAreInCircle = true

        // Update pointer list
        when(event.action){
            // New finger placed on screen
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointer = PointF()

                pointer.x = event.getX(pointerIndex)
                pointer.y = event.getY(pointerIndex)

                activePointers[pointerId] = pointer

                if(!isWithinView(pointer.x, pointer.y)){
                    eventPointersAreInCircle = false
                }
            }

            // Finger(s) moved
            MotionEvent.ACTION_MOVE -> {
                for(index in 0 until event.pointerCount){
                    val movedId = event.getPointerId(index)

                    // A pointer moved into our View from another View
                    if(!activePointers.contains(movedId)){
                        activePointers[movedId] = PointF()
                    }

                    val pointer = activePointers[movedId]

                    pointer.x = event.getX(index)
                    pointer.y = event.getY(index)

                    if(!isWithinView(pointer.x, pointer.y)){
                        eventPointersAreInCircle = false
                    }

                }
            }

            // Finger lifted (or another View has taken over control of the event)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                activePointers.remove(pointerId)

                if(!isWithinView(event.getX(pointerIndex), event.getY(pointerIndex))){
                    eventPointersAreInCircle = false
                }
            }
        }

        // Check if main pointer still exists, is inside the view, etc.
        val hadOldMainPointer = mainPointerId != null

        val mainPointerStillValid = hadOldMainPointer
                && activePointers.containsKey(mainPointerId!!)
                && isWithinView(
            activePointers[mainPointerId!!].x,
            activePointers[mainPointerId!!].y
        )

        if(!mainPointerStillValid){
            mainPointerId = null

            //Search for a new pointer within the view
            for(id in activePointers.keyIterator()){
                val pointer = activePointers[id]

                if(isWithinView(pointer.x, pointer.y)){
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

                // Last pointer moved out of the view, release
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

    protected open fun isWithinView(x: Float, y: Float): Boolean {
        return true
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