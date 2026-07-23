package com.local.virtualkeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/**
 * Draws only the body panel's upward shadow.
 *
 * The view spans the blur above the body and the complete top-corner arc inside it. Its lower edge
 * clips the shadow after the arc, avoiding a shadow/color band below the panel.
 */
internal class ShortcutPanelTopShadowView(
    context: Context,
    private val cornerRadiusDp: Int,
    private val shadowBlurDp: Int,
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
        val sourceTop = dp(shadowBlurDp)
        paint.setShadowLayer(dp(shadowBlurDp), 0f, 0f, SHADOW_COLOR)
        canvas.drawRoundRect(
            0f,
            sourceTop,
            width.toFloat(),
            sourceTop + radius * 2f,
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
