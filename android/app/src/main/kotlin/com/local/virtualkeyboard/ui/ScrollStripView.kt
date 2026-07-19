package com.local.virtualkeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import com.local.virtualkeyboard.R
import com.local.virtualkeyboard.settings.ScrollHapticProfile
import com.local.virtualkeyboard.settings.ScrollWheelTuning
import kotlin.math.abs
import kotlin.math.roundToInt

class ScrollStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    fun interface Listener {
        fun onWheel(delta: Int)
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private val viewConfiguration = ViewConfiguration.get(context)
    private val touchSlop = viewConfiguration.scaledTouchSlop.toFloat()
    private val minimumFlingVelocity = viewConfiguration.scaledMinimumFlingVelocity
    private val maximumFlingVelocity = viewConfiguration.scaledMaximumFlingVelocity
    private val scroller = OverScroller(context)
    private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.icon_default)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = GROOVE_STROKE_DP * density
    }
    private val detentAccumulator = ScrollDetentAccumulator(
        ScrollWheelTuning.DEFAULT_DETENT_SPACING_DP * density,
    )

    private var detentSpacingDp = ScrollWheelTuning.DEFAULT_DETENT_SPACING_DP
    private var inertiaScale = ScrollWheelTuning.DEFAULT_INERTIA_SCALE
    private var hapticProfile = ScrollHapticProfile.STANDARD
    private var velocityTracker: VelocityTracker? = null
    private var downY = 0f
    private var lastY = 0f
    private var lastScrollerY = 0
    private var visualOffsetPx = 0f
    private var scrolling = false

    init {
        isClickable = true
        isFocusable = false
        contentDescription = context.getString(R.string.scroll_strip)
    }

    fun setIndicatorColor(color: Int) {
        groovePaint.color = color
        invalidate()
    }

    fun configure(
        detentSpacingDp: Float,
        inertiaScale: Float,
        hapticProfile: ScrollHapticProfile,
    ) {
        cancelMotion()
        this.detentSpacingDp = detentSpacingDp.coerceIn(
            ScrollWheelTuning.MIN_DETENT_SPACING_DP,
            ScrollWheelTuning.MAX_DETENT_SPACING_DP,
        )
        this.inertiaScale = inertiaScale.coerceIn(
            ScrollWheelTuning.MIN_INERTIA_SCALE,
            ScrollWheelTuning.MAX_INERTIA_SCALE,
        )
        this.hapticProfile = hapticProfile
        detentAccumulator.spacingPx = this.detentSpacingDp * density
        invalidate()
    }

    fun cancelMotion() {
        if (!scroller.isFinished) scroller.forceFinished(true)
        velocityTracker?.recycle()
        velocityTracker = null
        scrolling = false
        detentAccumulator.reset()
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || visibility != VISIBLE) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelMotion()
                downY = event.y
                lastY = event.y
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (!scrolling && abs(event.y - downY) > touchSlop) {
                    scrolling = true
                    processScrollDelta(event.y - downY)
                } else if (scrolling) {
                    processScrollDelta(event.y - lastY)
                }
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                if (scrolling) {
                    velocityTracker?.computeCurrentVelocity(1000, maximumFlingVelocity.toFloat())
                    val scaledVelocity = ((velocityTracker?.yVelocity ?: 0f) * inertiaScale)
                        .coerceIn(-maximumFlingVelocity.toFloat(), maximumFlingVelocity.toFloat())
                    if (inertiaScale > 0f && abs(scaledVelocity) >= minimumFlingVelocity) {
                        startFling(scaledVelocity.roundToInt())
                    }
                } else {
                    performClick()
                }
                finishTouch()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                finishTouch()
                detentAccumulator.reset()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun computeScroll() {
        if (!scroller.computeScrollOffset()) return
        val currentY = scroller.currY
        val delta = currentY - lastScrollerY
        lastScrollerY = currentY
        if (delta != 0) processScrollDelta(delta.toFloat())
        if (!scroller.isFinished) postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val top = paddingTop.toFloat()
        val bottom = height - paddingBottom.toFloat()
        if (bottom <= top) return

        val centerX = width / 2f
        val centerY = (top + bottom) / 2f
        val halfWidth = GROOVE_WIDTH_DP * density / 2f
        val fadeDistance = (bottom - top) / 2f
        val pitch = detentAccumulator.spacingPx
        var y = centerY + visualOffsetPx
        while (y > top) y -= pitch
        while (y < top) y += pitch

        while (y <= bottom) {
            val normalizedDistance = (abs(y - centerY) / fadeDistance).coerceIn(0f, 1f)
            val visibility = 1f - normalizedDistance
            groovePaint.alpha = (MAX_GROOVE_ALPHA * visibility * visibility).roundToInt()
            if (groovePaint.alpha > 0) {
                canvas.drawLine(centerX - halfWidth, y, centerX + halfWidth, y, groovePaint)
            }
            y += pitch
        }
        groovePaint.alpha = MAX_GROOVE_ALPHA
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this && visibility != VISIBLE) cancelMotion()
    }

    override fun onDetachedFromWindow() {
        cancelMotion()
        super.onDetachedFromWindow()
    }

    private fun processScrollDelta(deltaPx: Float) {
        if (deltaPx == 0f) return
        val pitch = detentAccumulator.spacingPx
        visualOffsetPx = (visualOffsetPx + deltaPx) % pitch
        val detents = detentAccumulator.add(deltaPx)
        if (detents != 0) {
            listener?.onWheel(detents * WINDOWS_WHEEL_DELTA)
            performScrollHaptic()
        }
        invalidate()
    }

    private fun startFling(velocityY: Int) {
        lastScrollerY = 0
        scroller.fling(
            0,
            0,
            0,
            velocityY,
            0,
            0,
            -FLING_DISTANCE_LIMIT,
            FLING_DISTANCE_LIMIT,
        )
        postInvalidateOnAnimation()
    }

    private fun finishTouch() {
        velocityTracker?.recycle()
        velocityTracker = null
        scrolling = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun performScrollHaptic() {
        if (hapticProfile == ScrollHapticProfile.OFF) return
        val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            when (hapticProfile) {
                ScrollHapticProfile.OFF -> return
                ScrollHapticProfile.LIGHT -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                ScrollHapticProfile.STANDARD -> HapticFeedbackConstants.SEGMENT_TICK
                ScrollHapticProfile.STRONG -> HapticFeedbackConstants.CONTEXT_CLICK
            }
        } else {
            legacyHapticConstant()
        }
        if (!performHapticFeedback(primary) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            performHapticFeedback(legacyHapticConstant())
        }
    }

    private fun legacyHapticConstant(): Int = when (hapticProfile) {
        ScrollHapticProfile.OFF -> HapticFeedbackConstants.CLOCK_TICK
        ScrollHapticProfile.LIGHT -> HapticFeedbackConstants.CLOCK_TICK
        ScrollHapticProfile.STANDARD -> HapticFeedbackConstants.KEYBOARD_TAP
        ScrollHapticProfile.STRONG -> HapticFeedbackConstants.CONTEXT_CLICK
    }

    private companion object {
        const val WINDOWS_WHEEL_DELTA = 120
        const val FLING_DISTANCE_LIMIT = 1_000_000
        const val GROOVE_WIDTH_DP = 16f
        const val GROOVE_STROKE_DP = 2f
        const val MAX_GROOVE_ALPHA = 255
    }
}
