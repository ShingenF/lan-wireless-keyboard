package com.local.virtualkeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/**
 * Draws only a panel surface's upward shadow.
 *
 * The view spans the blur above the surface and as much of its vertical sides as the caller wants
 * visible. Its lower edge clips the remaining shadow, avoiding a shadow/color band below the
 * panel. A narrow surface can reserve a horizontal outset so its side blur is not clipped by the
 * shadow canvas.
 */
internal class ShortcutPanelTopShadowView(
    context: Context,
    private val cornerRadiusDp: Int,
    private val shadowBlurDp: Int,
    private val horizontalOutsetDp: Int = 0,
    private val topOutsetDp: Int = shadowBlurDp,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setSurfaceColor(color: Int) {
        paint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = dp(cornerRadiusDp)
        val sourceTop = dp(topOutsetDp)
        val horizontalOutset = dp(horizontalOutsetDp)
        paint.setShadowLayer(dp(shadowBlurDp), 0f, 0f, SHADOW_COLOR)
        canvas.drawRoundRect(
            horizontalOutset,
            sourceTop,
            width - horizontalOutset,
            height + radius,
            radius,
            radius,
            paint,
        )
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density

    private companion object {
        const val SHADOW_COLOR = 0x24000000
    }
}
