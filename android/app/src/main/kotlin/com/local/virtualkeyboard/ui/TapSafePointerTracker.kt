package com.local.virtualkeyboard.ui

internal data class PointerDelta(val dx: Float, val dy: Float)

/** Holds micro-movement locally until the gesture is definitely a drag. */
internal class TapSafePointerTracker(
    private val touchSlop: Float,
) {
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    fun onDown(x: Float, y: Float) {
        downX = x
        downY = y
        lastX = x
        lastY = y
        dragging = false
    }

    fun onMove(x: Float, y: Float): PointerDelta? {
        if (!dragging) {
            val totalDx = x - downX
            val totalDy = y - downY
            if (totalDx * totalDx + totalDy * totalDy <= touchSlop * touchSlop) return null
            dragging = true
            lastX = x
            lastY = y
            return PointerDelta(totalDx, totalDy)
        }

        val delta = PointerDelta(x - lastX, y - lastY)
        lastX = x
        lastY = y
        return delta
    }
}
