package com.local.virtualkeyboard.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.View.MeasureSpec
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.virtualkeyboard.R
import com.local.virtualkeyboard.protocol.ShortcutModifier
import com.local.virtualkeyboard.settings.ThemeColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutPanelViewTest {
    @Test
    fun activeShortcutKeepsThePanelExpandedWhenTheImeAppears() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val panel = ShortcutPanelView(context)
            panel.selection.tap(ShortcutModifier.CONTROL)

            panel.setImeVisible(visible = true, animate = false)

            val collapseControls = arrayListOf<View>()
            panel.findViewsWithText(
                collapseControls,
                context.getString(R.string.shortcut_panel_collapse),
                View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION,
            )
            assertEquals(1, collapseControls.size)
        }
    }

    @Test
    fun expandedShortcutBodyTracksItsImeEdgeTranslation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val panel = ShortcutPanelView(context)
            panel.selection.tap(ShortcutModifier.CONTROL)
            panel.notifySelectionChanged()
            panel.setImeVisible(visible = true, animate = false)
            val width = (393 * context.resources.displayMetrics.density).toInt()
            val height = (158 * context.resources.displayMetrics.density).toInt()
            panel.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
            )
            panel.layout(0, 0, width, height)

            val beforeButtonTop = accentTop(panel, width, height)
            val beforeArrowTop = arrowIconTop(panel, width, height)
            panel.setImeEdgeTranslationY(
                toggleTranslationY = 24f,
                bodyTranslationY = 24f,
            )

            assertEquals(beforeButtonTop + 24, accentTop(panel, width, height))
            assertEquals(beforeArrowTop + 24, arrowIconTop(panel, width, height))
        }
    }

    @Test
    fun lightArmedShortcutRendersWhiteTextAtWeightEightHundred() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val panel = ShortcutPanelView(context)
            panel.applyTheme(ThemeColors(), isDarkSemanticTheme = false)
            panel.selection.tap(ShortcutModifier.CONTROL)
            panel.notifySelectionChanged()

            val armedControls = arrayListOf<View>()
            panel.findViewsWithText(
                armedControls,
                "Control，一次性待命",
                View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION,
            )
            val button = armedControls.single() as TextView
            assertEquals(0xFFFFFFFF.toInt(), button.currentTextColor)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                assertEquals(800, button.typeface.weight)
            }
        }
    }

    @Test
    fun changingTheSemanticThemeRestylesAnArmedShortcutWithoutClearingIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val panel = ShortcutPanelView(context)
            panel.applyTheme(ThemeColors(), isDarkSemanticTheme = false)
            panel.selection.tap(ShortcutModifier.CONTROL)
            panel.notifySelectionChanged()

            panel.applyTheme(ThemeColors.darkDefaults(), isDarkSemanticTheme = true)

            val armedControls = arrayListOf<View>()
            panel.findViewsWithText(
                armedControls,
                "Control，一次性待命",
                View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION,
            )
            val button = armedControls.single() as TextView
            assertEquals(0xFF000000.toInt(), button.currentTextColor)
            assertEquals(
                setOf(ShortcutModifier.CONTROL),
                panel.selection.activeModifiers().toSet(),
            )
        }
    }

    @Test
    fun shortcutBodyKeepsTheSameUpwardShadowWhenTheImeExpandsIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val panel = ShortcutPanelView(context)
            val width = (393 * context.resources.displayMetrics.density).toInt()
            val height = (158 * context.resources.displayMetrics.density).toInt()
            panel.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
            )
            panel.layout(0, 0, width, height)
            val shadowY = height -
                (110 * context.resources.displayMetrics.density).toInt() -
                (4 * context.resources.displayMetrics.density).toInt()
            val shadowX = width / 2

            val defaultShadowAlpha = renderedPixelAlpha(panel, width, height, shadowX, shadowY)
            panel.selection.tap(ShortcutModifier.CONTROL)
            panel.notifySelectionChanged()
            panel.setImeVisible(visible = true, animate = false)
            panel.setImeEdgeTranslationY(
                toggleTranslationY = 24f,
                bodyTranslationY = 24f,
            )
            val imeShadowAlpha = renderedPixelAlpha(panel, width, height, shadowX, shadowY + 24)

            assertTrue("The visible shortcut body must cast an upward shadow.", defaultShadowAlpha > 0)
            assertEquals(defaultShadowAlpha, imeShadowAlpha)
        }
    }

    private fun accentTop(panel: ShortcutPanelView, width: Int, height: Int): Int {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        panel.draw(Canvas(bitmap))
        val accent = ThemeColors().accentArgb
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (bitmap.getPixel(x, y) == accent) return y
            }
        }
        error("No armed shortcut pixels were rendered.")
    }

    private fun arrowIconTop(panel: ShortcutPanelView, width: Int, height: Int): Int {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        panel.draw(Canvas(bitmap))
        val density = panel.resources.displayMetrics.density
        val startX = (24 * density).toInt()
        val endX = (88 * density).toInt().coerceAtMost(width)
        val icon = ThemeColors().iconArgb
        for (y in 0 until height) {
            for (x in startX until endX) {
                if (bitmap.getPixel(x, y) == icon) return y
            }
        }
        error("No shortcut arrow icon pixels were rendered.")
    }

    private fun renderedPixelAlpha(
        panel: ShortcutPanelView,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
    ): Int {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        panel.draw(Canvas(bitmap))
        return Color.alpha(bitmap.getPixel(x, y))
    }
}
