package de.radicalfishgames.crosscode.overlay

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent


class DirectionalPad(context: Context, attrs: AttributeSet) : CircleButton(context, attrs) {

    var onDirectionUpdate: (xDirection: Double, yDirection: Double) -> Unit = { _: Double, _: Double -> }

    init {
        bodyPaint.apply {
            color = Color.YELLOW
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {

        if(!super.onTouchEvent(event)){
            return false
        }

        if(event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE){
            val xTouch = event.x
            val yTouch = event.y

            val xDiff = xTouch - centerX
            val yDiff = yTouch - centerY

            // Return the values as values ranging from -1.0 to 1.0
            val xDir = xDiff / innerRadius
            val yDir = yDiff / innerRadius

            onDirectionUpdate(xDir.toDouble(), yDir.toDouble())
        }

        return true
    }
}