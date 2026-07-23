package com.local.virtualkeyboard.ui

import com.local.virtualkeyboard.settings.ThemeColors
import kotlin.math.pow

internal data class SystemBarStyle(
    val statusBarColor: Int,
    val navigationBarColor: Int,
    val useDarkStatusBarIcons: Boolean,
    val useDarkNavigationBarIcons: Boolean,
)

internal fun systemBarStyleFor(colors: ThemeColors): SystemBarStyle =
    SystemBarStyle(
        statusBarColor = colors.backgroundArgb,
        navigationBarColor = colors.inputBackgroundArgb,
        useDarkStatusBarIcons = usesDarkIcons(colors.backgroundArgb),
        useDarkNavigationBarIcons = usesDarkIcons(colors.inputBackgroundArgb),
    )

internal fun navigationBarBackdropTranslation(
    systemBarBottom: Int,
    imeBottom: Int,
): Int = maxOf(systemBarBottom, imeBottom)

private fun usesDarkIcons(color: Int): Boolean {
    fun linearized(component: Int): Double {
        val value = component / 255.0
        return if (value <= 0.03928) {
            value / 12.92
        } else {
            ((value + 0.055) / 1.055).pow(2.4)
        }
    }

    val red = linearized(color shr 16 and 0xFF)
    val green = linearized(color shr 8 and 0xFF)
    val blue = linearized(color and 0xFF)
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue > 0.179
}
