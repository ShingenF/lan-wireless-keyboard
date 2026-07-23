package com.local.virtualkeyboard.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import com.local.virtualkeyboard.R

/**
 * Keeps the touchpad surface at its unoccluded height while only shortening the scroll strip.
 *
 * The IME owns the pixels below its top edge, so the fixed-height touchpad can safely continue
 * behind it. Android lays out the app using the IME's final inset before the animation begins, so
 * the final layout occlusion restores the touchpad's hidden span while the animated occlusion
 * controls only the scroll strip's visible height.
 */
class StableTouchpadLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private var touchpad: View? = null
    private var scrollStrip: View? = null
    private var animatedImeOcclusion = 0
    private var layoutImeOcclusion = 0

    val currentImeOcclusion: Int
        get() = animatedImeOcclusion

    init {
        clipChildren = false
        clipToPadding = false
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        touchpad = findViewById(R.id.touchpad)
        scrollStrip = findViewById(R.id.scrollStrip)
    }

    fun setImeOcclusion(animatedOcclusionPx: Int, layoutOcclusionPx: Int = animatedOcclusionPx) {
        val nextAnimated = animatedOcclusionPx.coerceAtLeast(0)
        val nextLayout = layoutOcclusionPx.coerceAtLeast(0)
        if (
            animatedImeOcclusion == nextAnimated &&
            layoutImeOcclusion == nextLayout
        ) {
            return
        }
        animatedImeOcclusion = nextAnimated
        layoutImeOcclusion = nextLayout
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val touchpadView = touchpad ?: return
        val scrollStripView = scrollStrip ?: return

        remeasureHeight(
            touchpadView,
            touchpadView.measuredHeight + layoutImeOcclusion,
            widthMeasureSpec,
        )
        remeasureHeight(
            scrollStripView,
            (
                scrollStripView.measuredHeight +
                    layoutImeOcclusion -
                    animatedImeOcclusion
                ).coerceAtLeast(0),
            widthMeasureSpec,
        )
    }

    private fun remeasureHeight(view: View, height: Int, parentWidthMeasureSpec: Int) {
        val layoutParams = view.layoutParams as MarginLayoutParams
        val childWidthMeasureSpec = ViewGroup.getChildMeasureSpec(
            parentWidthMeasureSpec,
            paddingLeft + paddingRight + layoutParams.leftMargin + layoutParams.rightMargin,
            layoutParams.width,
        )
        view.measure(
            childWidthMeasureSpec,
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }
}
