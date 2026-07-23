package com.local.virtualkeyboard.ui

import com.local.virtualkeyboard.input.ShortcutModifierState
import com.local.virtualkeyboard.settings.ThemeColors
import kotlin.math.pow

internal data class ShortcutButtonStyle(
    val fill: Int,
    val stroke: Int?,
    val textColor: Int,
    val fontWeight: Int,
)

internal fun shortcutButtonStyle(
    state: ShortcutModifierState,
    colors: ThemeColors,
    isDarkSemanticTheme: Boolean,
): ShortcutButtonStyle {
    val fill = when (state) {
        ShortcutModifierState.OFF -> colors.inputBackgroundArgb
        ShortcutModifierState.ARMED -> colors.accentArgb
        ShortcutModifierState.LATCHED -> colors.primaryTextArgb
    }
    return ShortcutButtonStyle(
        fill = fill,
        stroke = if (state == ShortcutModifierState.OFF) colors.iconArgb else null,
        textColor = when {
            state == ShortcutModifierState.OFF -> colors.primaryTextArgb
            state == ShortcutModifierState.ARMED && !isDarkSemanticTheme -> WHITE
            else -> contrastColor(fill)
        },
        fontWeight = if (state == ShortcutModifierState.OFF) 700 else 800,
    )
}

private fun contrastColor(background: Int): Int {
    fun linearChannel(shift: Int): Double {
        val channel = ((background shr shift) and 0xFF) / 255.0
        return if (channel <= 0.03928) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
    }
    val luminance =
        0.2126 * linearChannel(16) +
            0.7152 * linearChannel(8) +
            0.0722 * linearChannel(0)
    return if (luminance > CONTRAST_LUMINANCE_THRESHOLD) BLACK else WHITE
}

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val CONTRAST_LUMINANCE_THRESHOLD = 0.179
