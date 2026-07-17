package com.local.virtualkeyboard.ui

internal class PointerDeltaAccumulator {
    private var pendingX = 0f
    private var pendingY = 0f

    fun add(dx: Float, dy: Float) {
        pendingX += dx
        pendingY += dy
    }

    fun drain(sensitivity: Float): Pair<Int, Int> {
        val safeSensitivity = sensitivity.coerceIn(0.5f, 2f)
        val scaledX = pendingX * safeSensitivity
        val scaledY = pendingY * safeSensitivity
        val integralX = scaledX.toInt()
        val integralY = scaledY.toInt()
        pendingX = (scaledX - integralX) / safeSensitivity
        pendingY = (scaledY - integralY) / safeSensitivity
        return integralX to integralY
    }

    fun reset() {
        pendingX = 0f
        pendingY = 0f
    }
}
