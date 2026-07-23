package com.local.virtualkeyboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.local.virtualkeyboard.R
import org.junit.Assert.assertFalse
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
}
