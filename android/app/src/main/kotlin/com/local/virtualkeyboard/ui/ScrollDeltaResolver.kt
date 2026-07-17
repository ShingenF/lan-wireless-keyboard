package com.local.virtualkeyboard.ui

import kotlin.math.roundToInt

internal object ScrollDeltaResolver {
    const val DEFAULT_MULTIPLIER = 4f

    fun resolve(previousY: Float, currentY: Float, multiplier: Float): Int =
        ((currentY - previousY) * multiplier).roundToInt()
}
