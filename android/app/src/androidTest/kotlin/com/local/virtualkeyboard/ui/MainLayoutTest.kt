package com.local.virtualkeyboard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.virtualkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainLayoutTest {
    @Test
    fun mainRootAllowsTheImeEdgeLabelToDrawOutsideThePanelBounds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = LayoutInflater.from(context).inflate(R.layout.activity_main, null) as ViewGroup

        assertFalse(root.clipChildren)
        assertFalse(root.clipToPadding)
    }

    @Test
    fun imeResizeKeepsTheTouchpadSurfaceHeightAndOnlyShortensTheScrollStrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = LayoutInflater.from(context).inflate(R.layout.activity_main, null) as ViewGroup
        val width = dp(context.resources.displayMetrics.density, 393)
        val fullHeight = dp(context.resources.displayMetrics.density, 800)
        val imeHeight = dp(context.resources.displayMetrics.density, 200)
        val touchpad = root.findViewById<View>(R.id.touchpad)
        val scrollStrip = root.findViewById<View>(R.id.scrollStrip)
        val touchpadContainer = root.findViewById<StableTouchpadLayout>(R.id.touchpadContainer)

        touchpadContainer.setImeOcclusion(0, 0)
        measureAndLayout(root, width, fullHeight)
        val fullTouchpadHeight = touchpad.measuredHeight
        val fullScrollStripHeight = scrollStrip.measuredHeight

        touchpadContainer.setImeOcclusion(0, imeHeight)
        measureAndLayout(root, width, fullHeight - imeHeight)
        assertEquals(fullTouchpadHeight, touchpad.measuredHeight)
        assertEquals(fullScrollStripHeight, scrollStrip.measuredHeight)

        touchpadContainer.setImeOcclusion(imeHeight / 2, imeHeight)
        measureAndLayout(root, width, fullHeight - imeHeight)
        assertEquals(fullTouchpadHeight, touchpad.measuredHeight)
        assertEquals(fullScrollStripHeight - imeHeight / 2, scrollStrip.measuredHeight)

        touchpadContainer.setImeOcclusion(imeHeight, imeHeight)
        measureAndLayout(root, width, fullHeight - imeHeight)
        assertEquals(fullTouchpadHeight, touchpad.measuredHeight)
        assertEquals(fullScrollStripHeight - imeHeight, scrollStrip.measuredHeight)

        touchpadContainer.setImeOcclusion(imeHeight, 0)
        measureAndLayout(root, width, fullHeight)
        assertEquals(fullTouchpadHeight, touchpad.measuredHeight)
        assertEquals(fullScrollStripHeight - imeHeight, scrollStrip.measuredHeight)

        touchpadContainer.setImeOcclusion(imeHeight / 2, 0)
        measureAndLayout(root, width, fullHeight)
        assertEquals(fullTouchpadHeight, touchpad.measuredHeight)
        assertEquals(fullScrollStripHeight - imeHeight / 2, scrollStrip.measuredHeight)

        touchpadContainer.setImeOcclusion(0, 0)
        measureAndLayout(root, width, fullHeight)
        assertEquals(fullTouchpadHeight, touchpad.measuredHeight)
        assertEquals(fullScrollStripHeight, scrollStrip.measuredHeight)
    }

    @Test
    fun shortcutPanelDrawsAboveTheTouchpadContent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = LayoutInflater.from(context).inflate(R.layout.activity_main, null) as ViewGroup
        val mainContent = root.findViewById<View>(R.id.mainContent)
        val shortcutPanel = root.findViewById<View>(R.id.shortcutPanel)

        assertTrue(root.indexOfChild(mainContent) < root.indexOfChild(shortcutPanel))
    }

    private fun measureAndLayout(root: View, width: Int, height: Int) {
        root.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
    }

    private fun dp(density: Float, value: Int): Int = (density * value).toInt()
}
