package com.local.virtualkeyboard.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
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
    fun activeImeTransitionShowsTheWholeArrowInOneFrameWithoutSlidingThroughTheBody() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val panel = ShortcutPanelView(context)
            panel.selection.tap(ShortcutModifier.CONTROL)
            panel.setImeVisible(
                visible = true,
                animate = false,
                deferToggleVisibility = true,
            )
            val collapseControls = arrayListOf<View>()
            panel.findViewsWithText(
                collapseControls,
                context.getString(R.string.shortcut_panel_collapse),
                View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION,
            )
            val toggle = collapseControls.single()

            assertEquals(1f, toggle.translationY)
            assertEquals(0f, toggle.alpha)
            assertEquals(false, toggle.isClickable)
            assertEquals(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                toggle.importantForAccessibility,
            )

            panel.setImeTransitionToggleVisible(true)

            assertEquals(1f, toggle.translationY)
            assertEquals(1f, toggle.alpha)
            assertEquals(true, toggle.isClickable)
            assertEquals(
                View.IMPORTANT_FOR_ACCESSIBILITY_YES,
                toggle.importantForAccessibility,
            )
        }
    }

    @Test
    fun legacyImeRollbackRestoresTheArrowAfterAnEarlyHide() {
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
            val toggle = collapseControls.single()

            panel.setImeTransitionToggleVisible(false)
            assertEquals(0f, toggle.alpha)

            panel.setImeTransitionToggleVisible(true)
            assertEquals(1f, toggle.alpha)
        }
    }

    @Test
    fun arrowLabelUsesAContentShadowInsteadOfPlatformElevation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val panel = ShortcutPanelView(context)
            panel.selection.tap(ShortcutModifier.CONTROL)
            panel.setImeVisible(visible = true, animate = false)

            val toggle = findToggle(
                panel,
                context.getString(R.string.shortcut_panel_collapse),
            )

            assertEquals(0f, toggle.elevation)
        }
    }

    @Test
    fun shortcutBodyAlwaysDrawsAboveTheArrowLabel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val panel = ShortcutPanelView(context)
            panel.selection.tap(ShortcutModifier.CONTROL)
            panel.setImeVisible(visible = true, animate = false)
            val toggle = findToggle(
                panel,
                context.getString(R.string.shortcut_panel_collapse),
            )
            val shift = findToggle(panel, "Shift，未启用")

            assertTrue(
                panel.indexOfChild(directChildOf(panel, toggle)) <
                    panel.indexOfChild(directChildOf(panel, shift)),
            )
        }
    }

    @Test
    fun arrowAndShortcutBodyUseTheSameUpwardShadowStrength() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val width = (393 * density).toInt()
            val height = (158 * density).toInt()
            val expandedPanel = ShortcutPanelView(context)
            measureAndLayout(expandedPanel, width, height)
            val bodyTop = height - (110 * density).toInt()
            val bodyShadowAlpha = renderedPixelAlpha(
                expandedPanel,
                width,
                height,
                x = width / 2,
                y = bodyTop - (4 * density).toInt(),
            )

            val collapsedPanel = ShortcutPanelView(context)
            collapsedPanel.setImeVisible(visible = true, animate = false)
            measureAndLayout(collapsedPanel, width, height)
            val toggleTop = (110 * density).toInt() + 1
            val toggleShadowAlpha = renderedPixelAlpha(
                collapsedPanel,
                width,
                height,
                x = (56 * density).toInt(),
                y = toggleTop - (4 * density).toInt(),
            )

            assertTrue(bodyShadowAlpha > 0)
            assertEquals(bodyShadowAlpha, toggleShadowAlpha)
        }
    }

    @Test
    fun arrowShadowExtendsBeyondBothEdgesForTheFullLabelHeight() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val width = (393 * density).toInt()
            val height = (158 * density).toInt()
            val panel = ShortcutPanelView(context)
            panel.setImeVisible(visible = true, animate = false)
            measureAndLayout(panel, width, height)
            val toggleTop = (110 * density).toInt() + 1
            val toggleLeft = (24 * density).toInt()
            val toggleRight = toggleLeft + (64 * density).toInt()
            val verticalSamplesDp = 16..44 step 4
            val horizontalOffsetsDp = listOf(2, 4)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            panel.draw(Canvas(bitmap))
            val sideShadowAlphas = verticalSamplesDp.flatMap { yDp ->
                horizontalOffsetsDp.flatMap { offsetDp ->
                    val y = toggleTop + (yDp * density).toInt()
                    val offset = (offsetDp * density).toInt()
                    listOf(
                        Color.alpha(bitmap.getPixel(toggleLeft - offset, y)),
                        Color.alpha(bitmap.getPixel(toggleRight + offset, y)),
                    )
                }
            }
            val toggle = findToggle(
                panel,
                context.getString(R.string.shortcut_panel_expand),
            )
            val shadow = (toggle as ViewGroup).getChildAt(0)
            val reservedOutset = (24 * density).toInt()

            assertTrue(sideShadowAlphas.all { it > 0 })
            assertTrue(shadow is ShortcutPanelTopShadowView)
            assertTrue(shadow.left <= -reservedOutset)
            assertTrue(shadow.right >= toggle.width + reservedOutset)
            assertTrue(shadow.top <= -reservedOutset)
        }
    }

    @Test
    fun arrowLabelOverlapsItsAdjacentSurfaceByOnePhysicalPixel() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val expandedPanel = ShortcutPanelView(context)
            expandedPanel.selection.tap(ShortcutModifier.CONTROL)
            expandedPanel.setImeVisible(visible = true, animate = false)
            val expandedToggle = findToggle(
                expandedPanel,
                context.getString(R.string.shortcut_panel_collapse),
            )
            assertEquals(1f, expandedToggle.translationY)

            val collapsedPanel = ShortcutPanelView(context)
            collapsedPanel.setImeVisible(visible = true, animate = false)
            val collapsedToggle = findToggle(
                collapsedPanel,
                context.getString(R.string.shortcut_panel_expand),
            )
            val bodyHeight = (110 * context.resources.displayMetrics.density).toInt()
            assertEquals(bodyHeight + 1f, collapsedToggle.translationY)
        }
    }

    @Test
    fun imeStateChangeRejectsAStaleManualPanelAnimationEndAction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var panel: ShortcutPanelView
        lateinit var toggle: View
        var width = 0
        var height = 0

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            panel = ShortcutPanelView(context)
            panel.selection.tap(ShortcutModifier.CONTROL)
            panel.notifySelectionChanged()
            panel.setImeVisible(visible = true, animate = false)
            width = (393 * context.resources.displayMetrics.density).toInt()
            height = (158 * context.resources.displayMetrics.density).toInt()
            panel.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
            )
            panel.layout(0, 0, width, height)

            val collapseControls = arrayListOf<View>()
            panel.findViewsWithText(
                collapseControls,
                context.getString(R.string.shortcut_panel_collapse),
                View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION,
            )
            toggle = collapseControls.single()
            toggle.performClick()
            panel.setImeVisible(visible = false, animate = false)
        }

        Thread.sleep(250)

        instrumentation.runOnMainSync {
            assertTrue(accentTop(panel, width, height) >= 0)
            assertEquals(0f, toggle.alpha)
            assertEquals(false, toggle.isClickable)
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
    fun armedShortcutRendersWhiteTextAtWeightSevenHundred() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val panel = ShortcutPanelView(context)
            panel.applyTheme(ThemeColors())
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
                assertEquals(700, button.typeface.weight)
            }
        }
    }

    @Test
    fun changingTheSemanticThemeRestylesAnArmedShortcutWithoutClearingIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val panel = ShortcutPanelView(context)
            panel.applyTheme(ThemeColors())
            panel.selection.tap(ShortcutModifier.CONTROL)
            panel.notifySelectionChanged()

            panel.applyTheme(ThemeColors.darkDefaults())

            val armedControls = arrayListOf<View>()
            panel.findViewsWithText(
                armedControls,
                "Control，一次性待命",
                View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION,
            )
            val button = armedControls.single() as TextView
            assertEquals(0xFFFFFFFF.toInt(), button.currentTextColor)
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

    @Test
    fun shortcutBodyShadowWrapsItsRoundedTopCorner() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            val panel = ShortcutPanelView(context)
            val width = (393 * density).toInt()
            val height = (158 * density).toInt()
            panel.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
            )
            panel.layout(0, 0, width, height)
            val bodyTop = height - (110 * density).toInt()
            val cornerAlpha = renderedPixelAlpha(
                panel,
                width,
                height,
                x = (2 * density).toInt(),
                y = bodyTop + (4 * density).toInt(),
            )

            assertTrue(
                "The upward shadow must continue around the body's rounded top corner.",
                cornerAlpha > 0,
            )
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

    private fun findToggle(panel: ShortcutPanelView, description: String): View {
        val controls = arrayListOf<View>()
        panel.findViewsWithText(
            controls,
            description,
            View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION,
        )
        return controls.single()
    }

    private fun directChildOf(panel: ShortcutPanelView, descendant: View): View {
        var child = descendant
        while (child.parent !== panel) {
            child = child.parent as View
        }
        return child
    }

    private fun measureAndLayout(panel: ShortcutPanelView, width: Int, height: Int) {
        panel.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        panel.layout(0, 0, width, height)
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
