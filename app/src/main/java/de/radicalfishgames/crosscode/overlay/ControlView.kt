package de.radicalfishgames.crosscode.overlay

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.util.LruCache
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import androidx.core.util.contains
import androidx.core.util.containsKey
import androidx.core.util.keyIterator
import androidx.core.util.set
import de.radicalfishgames.crosscode.R

open class ControlView(
    context: Context,
    attrs: AttributeSet,
) : View(context, attrs) {
    companion object {
        private val bitmapCache = LruCache<Int, Bitmap>(16)

        fun getCachedBitmap(
            resId: Int,
            resources: Resources,
        ): Bitmap =
            bitmapCache.get(resId) ?: run {
                val options = BitmapFactory.Options().apply { inScaled = false }
                BitmapFactory.decodeResource(resources, resId, options).also {
                    bitmapCache.put(resId, it)
                }
            }
    }

    private val onTouch: MutableSet<() -> Unit> = HashSet()
    private val onMove: MutableSet<(x: Float, y: Float) -> Unit> = HashSet()
    private val onRelease: MutableSet<() -> Unit> = HashSet()

    private val imagePaint =
        Paint().apply {
            isDither = false
            isAntiAlias = false
            isFilterBitmap = false
        }

    private lateinit var fullSizeRect: RectF
    var imageBitmap: Bitmap

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.ControlView, 0, 0).apply {
            try {
                val resId = getResourceId(R.styleable.ControlView_image, -1)
                imageBitmap = getCachedBitmap(resId, context.resources)
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
                imagePaint,
            )
        }
    }

    override fun onSizeChanged(
        width: Int,
        height: Int,
        oldWidth: Int,
        oldHeight: Int,
    ) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)

        fullSizeRect =
            RectF(
                paddingLeft.toFloat(),
                paddingTop.toFloat(),
                width.toFloat() - paddingRight,
                height.toFloat() - paddingBottom,
            )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            -> {
                val pointer = PointF()

                pointer.x = event.getX(pointerIndex)
                pointer.y = event.getY(pointerIndex)

                activePointers[pointerId] = pointer
            }

            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.pointerCount) {
                    val movedId = event.getPointerId(index)

                    // A pointer moved into our View from another View
                    if (activePointers.indexOfKey(movedId) < 0) {
                        activePointers[movedId] = PointF()
                    }

                    val pointer = activePointers[movedId]

                    pointer.x = event.getX(index)
                    pointer.y = event.getY(index)
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            -> {
                activePointers.remove(pointerId)
            }

            // Another View has taken over the gesture, so every pointer is gone
            MotionEvent.ACTION_CANCEL -> {
                activePointers.clear()
            }
        }

        // The main pointer drives this control; once it leaves the view, hand off to another pointer inside it
        val currentMainId = mainPointerId
        val hadOldMainPointer = currentMainId != null

        val mainPointerStillValid =
            currentMainId != null &&
                activePointers.containsKey(currentMainId) &&
                isWithinView(
                    activePointers[currentMainId].x,
                    activePointers[currentMainId].y,
                )

        if (!mainPointerStillValid) {
            mainPointerId = null

            for (id in activePointers.keyIterator()) {
                val pointer = activePointers[id]

                if (isWithinView(pointer.x, pointer.y)) {
                    mainPointerId = id
                    break
                }
            }

            // Fresh press
            if (mainPointerId != null && !hadOldMainPointer) {
                onTouch()

                onMove()

                // Handed off to another pointer while already pressed: move, don't re-press
            } else if (mainPointerId != null && hadOldMainPointer) {
                onMove()

                // No pointer left inside the view: release
            } else if (hadOldMainPointer) {
                onRelease()
            }

        } else {
            onMove()
        }

        // Return true in any case so we get notified when the pointer enters our view
        return true
    }

    protected open fun isWithinView(
        x: Float,
        y: Float,
    ): Boolean = true

    fun onTouch(run: () -> Unit) = onTouch.add(run)

    private fun onTouch() {
        onTouch.forEach { it.invoke() }
    }

    fun onMove(run: (x: Float, y: Float) -> Unit) = onMove.add(run)

    private fun onMove() {
        val id = mainPointerId ?: return
        val pointer = activePointers[id]
        onMove.forEach {
            it.invoke(pointer.x, pointer.y)
        }
    }

    fun onRelease(run: () -> Unit) = onRelease.add(run)

    private fun onRelease() {
        onRelease.forEach { it.invoke() }
    }
}
