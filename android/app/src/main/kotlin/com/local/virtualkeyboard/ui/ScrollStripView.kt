package com.local.virtualkeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.local.virtualkeyboard.R
import kotlin.math.abs

class ScrollStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    fun interface Listener {
        fun onWheel(delta: Int)
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.icon_default)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f * density
    }
    private val indicatorPath = Path()
    private var downY = 0f
    private var lastY = 0f
    private var scrolling = false

    init {
        isClickable = true
        isFocusable = false
        contentDescription = context.getString(R.string.scroll_strip)
    }

    fun setIndicatorColor(color: Int) {
        indicatorPaint.color = color
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                lastY = event.y
                scrolling = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scrolling && abs(event.y - downY) > touchSlop) scrolling = true
                if (scrolling) {
                    val delta = ScrollDeltaResolver.resolve(
                        previousY = lastY,
                        currentY = event.y,
                        multiplier = ScrollDeltaResolver.DEFAULT_MULTIPLIER,
                    )
                    if (delta != 0) listener?.onWheel(delta)
                    lastY = event.y
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                scrolling = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val arrowSize = 5f * density
        val top = paddingTop + arrowSize
        val bottom = height - paddingBottom - arrowSize

        canvas.drawLine(centerX, top + arrowSize, centerX, bottom - arrowSize, indicatorPaint)
        indicatorPath.reset()
        indicatorPath.moveTo(centerX - arrowSize, top + arrowSize)
        indicatorPath.lineTo(centerX, top)
        indicatorPath.lineTo(centerX + arrowSize, top + arrowSize)
        indicatorPath.moveTo(centerX - arrowSize, bottom - arrowSize)
        indicatorPath.lineTo(centerX, bottom)
        indicatorPath.lineTo(centerX + arrowSize, bottom - arrowSize)
        canvas.drawPath(indicatorPath, indicatorPaint)
    }
}
