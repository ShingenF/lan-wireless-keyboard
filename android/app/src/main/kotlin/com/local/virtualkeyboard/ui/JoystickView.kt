package com.local.virtualkeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.local.virtualkeyboard.R
import kotlin.math.hypot
import kotlin.math.min

/** A compact directional joystick whose knob follows the finger and returns to center. */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    fun interface Listener {
        fun onDirectionsChanged(directions: Set<JoystickDirection>)
    }

    var listener: Listener? = null
    var allowDiagonals: Boolean = false

    private val knobRadius = dp(8f)
    private val cardinalResolver = JoystickDirectionResolver(deadZone = dp(7f))
    private val gameResolver = GameJoystickDirectionResolver(deadZone = dp(7f))
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.icon_default)
        style = Paint.Style.FILL
    }
    private var offsetX = 0f
    private var offsetY = 0f
    private var directions = emptySet<JoystickDirection>()
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    init {
        isClickable = true
        isFocusable = false
        contentDescription = context.getString(R.string.joystick)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(width / 2f + offsetX, height / 2f + offsetY, knobRadius, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activePointerId = event.getPointerId(event.actionIndex)
                updateKnob(
                    event.getX(event.actionIndex) - width / 2f,
                    event.getY(event.actionIndex) - height / 2f,
                )
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) {
                    resetKnob()
                    return true
                }
                updateKnob(event.getX(pointerIndex) - width / 2f, event.getY(pointerIndex) - height / 2f)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) resetKnob()
                return true
            }
            MotionEvent.ACTION_UP -> {
                resetKnob()
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                resetKnob()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun cancelGesture() {
        resetKnob()
    }

    fun setKnobColor(color: Int) {
        paint.color = color
        invalidate()
    }

    override fun onDetachedFromWindow() {
        resetKnob()
        super.onDetachedFromWindow()
    }

    private fun updateKnob(rawDx: Float, rawDy: Float) {
        val maxTravel = (min(width, height) / 2f - knobRadius - dp(3f)).coerceAtLeast(0f)
        val distance = hypot(rawDx, rawDy)
        val scale = if (distance > maxTravel && distance > 0f) maxTravel / distance else 1f
        offsetX = rawDx * scale
        offsetY = rawDy * scale
        val updatedDirections = if (allowDiagonals) {
            gameResolver.resolve(offsetX, offsetY)
        } else {
            cardinalResolver.resolve(offsetX, offsetY)?.let(::setOf).orEmpty()
        }
        updateDirections(updatedDirections)
        invalidate()
    }

    private fun resetKnob() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        offsetX = 0f
        offsetY = 0f
        updateDirections(emptySet())
        invalidate()
    }

    private fun updateDirections(updated: Set<JoystickDirection>) {
        if (updated == directions) return
        directions = updated
        if (updated.isNotEmpty()) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        listener?.onDirectionsChanged(updated)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
