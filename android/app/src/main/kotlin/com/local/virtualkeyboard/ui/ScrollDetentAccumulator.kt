package com.local.virtualkeyboard.ui

internal class ScrollDetentAccumulator(spacingPx: Float) {
    var spacingPx: Float = spacingPx
        set(value) {
            require(value > 0f) { "spacingPx must be positive" }
            field = value
            reset()
        }

    private var remainderPx = 0f

    init {
        require(spacingPx > 0f) { "spacingPx must be positive" }
    }

    fun add(deltaPx: Float): Int {
        remainderPx += deltaPx
        val detents = (remainderPx / spacingPx).toInt()
        remainderPx -= detents * spacingPx
        return detents
    }

    fun reset() {
        remainderPx = 0f
    }
}
