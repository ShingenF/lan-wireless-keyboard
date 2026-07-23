package com.local.virtualkeyboard.ui

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.virtualkeyboard.R
import com.local.virtualkeyboard.protocol.ShortcutModifier
import org.junit.Assert.assertEquals
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
}
