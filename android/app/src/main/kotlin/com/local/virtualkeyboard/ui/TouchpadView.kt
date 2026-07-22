package com.local.virtualkeyboard.ui

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.local.virtualkeyboard.R
import com.local.virtualkeyboard.settings.PointerAcceleration
import kotlin.math.sqrt

class TouchpadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    interface Listener {
        fun onMove(dx: Int, dy: Int)
        fun onLeftClick()
        fun onRightClick()
        fun onWheel(delta: Int)
        fun onLeftButtonDown() = Unit
        fun onLeftButtonUp() = Unit
    }

    var listener: Listener? = null
    var sensitivity: Float = 1.0f
    var maximumAccelerationGain: Float = PointerAcceleration.DEFAULT_GAIN

    private val viewConfiguration = ViewConfiguration.get(context)
    private val pointerTracker = TapSafePointerTracker(viewConfiguration.scaledTouchSlop.toFloat())
    private val tapResolver = TapSequenceResolver(
        doubleTapTimeoutMillis = ViewConfiguration.getDoubleTapTimeout().toLong(),
        doubleTapSlop = viewConfiguration.scaledDoubleTapSlop.toFloat(),
        touchSlop = viewConfiguration.scaledTouchSlop.toFloat(),
        tapTimeoutMillis = TAP_TIMEOUT_MILLIS,
        longPressTimeoutMillis = LONG_PRESS_TIMEOUT_MILLIS,
    )
    private val multiTouchMovementTracker =
        MultiTouchMovementTracker(viewConfiguration.scaledTouchSlop.toFloat())
    private var downTime = 0L
    private var lastAverageY = 0f
    private var tapEligible = false
    private val movementAccumulator = PointerDeltaAccumulator()
    private var lastMoveEmission = 0L
    private var lastPointerSampleTime = 0L
    private val pendingTapRunnable = Runnable {
        dispatchTapActions(tapResolver.onTapTimeout(SystemClock.uptimeMillis()))
    }
    private val longPressRunnable = Runnable {
        val actions = tapResolver.onLongPress(SystemClock.uptimeMillis())
        if (TapAction.LEFT_BUTTON_DOWN in actions) tapEligible = false
        dispatchTapActions(actions)
    }

    init {
        isFocusable = false
        isClickable = true
        contentDescription = context.getString(R.string.touchpad)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(pendingTapRunnable)
                removeCallbacks(longPressRunnable)
                multiTouchMovementTracker.reset()
                downTime = event.eventTime
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                dispatchTapActions(tapResolver.onDown(event.x, event.y, event.eventTime))
                pointerTracker.onDown(event.x, event.y)
                lastAverageY = event.y
                tapEligible = true
                movementAccumulator.reset()
                lastPointerSampleTime = event.eventTime
                postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MILLIS)
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(longPressRunnable)
                val actions = if (tapResolver.isMultiTouchActive()) {
                    if (multiTouchMovementTracker.addContacts(touchContacts(event))) {
                        tapResolver.onMultiTouchMoved()
                    }
                    emptyList()
                } else {
                    multiTouchMovementTracker.start(touchContacts(event))
                    tapResolver.onMultiTouchStarted(
                        gestureStartTimeMillis = downTime,
                        rightClickEligible = tapEligible,
                    )
                }
                flushMovement()
                dispatchTapActions(actions)
                lastAverageY = averageY(event)
                tapEligible = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (tapResolver.isMultiTouchActive()) {
                    val moved = multiTouchMovementTracker.update(touchContacts(event))
                    if (moved) tapResolver.onMultiTouchMoved()
                    if (event.pointerCount >= 2) {
                        val averageY = averageY(event)
                        if (moved) {
                            val delta = ScrollDeltaResolver.resolve(
                                previousY = lastAverageY,
                                currentY = averageY,
                                multiplier = ScrollDeltaResolver.DEFAULT_MULTIPLIER,
                            )
                            if (delta != 0) {
                                listener?.onWheel(delta)
                            }
                            lastAverageY = averageY
                        }
                    }
                    tapEligible = false
                } else if (event.pointerCount == 1) {
                    pointerTracker.onMove(event.x, event.y)?.let { delta ->
                        removeCallbacks(longPressRunnable)
                        tapEligible = false
                        dispatchTapActions(tapResolver.onMove(event.x, event.y))
                        val accelerated = accelerate(delta, event.eventTime)
                        movementAccumulator.add(accelerated.dx, accelerated.dy)
                        maybeEmitMovement(event.eventTime)
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (multiTouchMovementTracker.update(touchContacts(event))) {
                    tapResolver.onMultiTouchMoved()
                }
                val remainingCount = event.pointerCount - 1
                if (remainingCount >= 2) {
                    lastAverageY = averageYExcluding(event, event.actionIndex)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (tapResolver.isMultiTouchActive() &&
                    multiTouchMovementTracker.update(touchContacts(event))
                ) {
                    tapResolver.onMultiTouchMoved()
                }
                flushMovement()
                if (tapResolver.isMultiTouchActive()) {
                    dispatchTapActions(tapResolver.onMultiTouchEnded(event.eventTime))
                } else if (tapEligible && event.eventTime - downTime <= TAP_TIMEOUT_MILLIS) {
                    performClick()
                    dispatchTapActions(tapResolver.onTap(event.x, event.y, event.eventTime))
                    removeCallbacks(pendingTapRunnable)
                    postDelayed(pendingTapRunnable, DOUBLE_TAP_TIMEOUT_MILLIS)
                } else {
                    dispatchTapActions(tapResolver.onGestureEnded())
                }
                multiTouchMovementTracker.reset()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(pendingTapRunnable)
                removeCallbacks(longPressRunnable)
                flushMovement()
                dispatchTapActions(tapResolver.cancel())
                multiTouchMovementTracker.reset()
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

    override fun onDetachedFromWindow() {
        removeCallbacks(pendingTapRunnable)
        removeCallbacks(longPressRunnable)
        flushMovement()
        dispatchTapActions(tapResolver.cancel())
        multiTouchMovementTracker.reset()
        super.onDetachedFromWindow()
    }

    private fun dispatchTapActions(actions: List<TapAction>) {
        actions.forEach { action ->
            when (action) {
                TapAction.LEFT_CLICK -> {
                    listener?.onLeftClick()
                }
                TapAction.RIGHT_CLICK -> {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener?.onRightClick()
                }
                TapAction.LEFT_BUTTON_DOWN -> {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener?.onLeftButtonDown()
                }
                TapAction.LEFT_BUTTON_UP -> {
                    listener?.onLeftButtonUp()
                }
            }
        }
    }

    private fun maybeEmitMovement(eventTime: Long) {
        if (eventTime - lastMoveEmission >= MOVE_INTERVAL_MILLIS) {
            flushMovement()
            lastMoveEmission = eventTime
        }
    }

    private fun flushMovement() {
        val (integralDx, integralDy) = movementAccumulator.drain(sensitivity)
        if (integralDx != 0 || integralDy != 0) listener?.onMove(integralDx, integralDy)
    }

    private fun accelerate(delta: PointerDelta, eventTime: Long): PointerDelta {
        val elapsedMillis = (eventTime - lastPointerSampleTime).coerceIn(4L, 64L)
        lastPointerSampleTime = eventTime
        val distancePixels = sqrt(delta.dx * delta.dx + delta.dy * delta.dy)
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val speedDpPerMillis = distancePixels / density / elapsedMillis
        val normalizedSpeed =
            ((speedDpPerMillis - ACCELERATION_START_DP_PER_MILLISECOND) /
                (ACCELERATION_FULL_DP_PER_MILLISECOND - ACCELERATION_START_DP_PER_MILLISECOND))
                .coerceIn(0f, 1f)
        val maximumGain = maximumAccelerationGain.coerceIn(
            PointerAcceleration.MIN_GAIN,
            PointerAcceleration.MAX_GAIN,
        )
        val gain = 1f + (maximumGain - 1f) * normalizedSpeed * normalizedSpeed
        return PointerDelta(delta.dx * gain, delta.dy * gain)
    }

    private fun averageY(event: MotionEvent): Float {
        var total = 0f
        repeat(event.pointerCount) { total += event.getY(it) }
        return total / event.pointerCount
    }

    private fun averageYExcluding(event: MotionEvent, excludedIndex: Int): Float {
        var total = 0f
        var count = 0
        repeat(event.pointerCount) { index ->
            if (index != excludedIndex) {
                total += event.getY(index)
                count += 1
            }
        }
        return total / count
    }

    private fun touchContacts(event: MotionEvent): List<TouchContact> =
        List(event.pointerCount) { index ->
            TouchContact(
                id = event.getPointerId(index),
                x = event.getX(index),
                y = event.getY(index),
            )
        }

    private companion object {
        const val MOVE_INTERVAL_MILLIS = 16L
        const val TAP_TIMEOUT_MILLIS = 250L
        val DOUBLE_TAP_TIMEOUT_MILLIS = ViewConfiguration.getDoubleTapTimeout().toLong()
        const val LONG_PRESS_TIMEOUT_MILLIS = 1_000L
        const val ACCELERATION_START_DP_PER_MILLISECOND = 0.08f
        const val ACCELERATION_FULL_DP_PER_MILLISECOND = 1.2f
    }
}
