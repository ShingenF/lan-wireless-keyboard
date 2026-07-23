package com.local.virtualkeyboard.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.local.virtualkeyboard.R
import com.local.virtualkeyboard.input.ShortcutModifierState
import com.local.virtualkeyboard.input.ShortcutSelection
import com.local.virtualkeyboard.input.ShortcutSelectionSnapshot
import com.local.virtualkeyboard.protocol.ShortcutModifier
import com.local.virtualkeyboard.settings.ThemeColors

class ShortcutPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    val selection = ShortcutSelection()

    private val bodyClipLayer = FrameLayout(context)
    private val bodyMotionLayer = FrameLayout(context)
    private val bodyShadow = ShortcutPanelTopShadowView(
        context = context,
        cornerRadiusDp = BODY_CORNER_DP,
        shadowBlurDp = BODY_SHADOW_HEIGHT_DP,
    )
    private val body = LinearLayout(context)
    private val toggleMotionLayer = FrameLayout(context)
    private val toggle = FrameLayout(context)
    private val toggleShadow = ShortcutPanelTopShadowView(
        context = context,
        cornerRadiusDp = TOGGLE_CORNER_DP,
        shadowBlurDp = BODY_SHADOW_HEIGHT_DP,
        horizontalOutsetDp = TOGGLE_SHADOW_OUTSET_DP,
        topOutsetDp = TOGGLE_SHADOW_OUTSET_DP,
    )
    private val toggleSurface = FrameLayout(context)
    private val toggleIcon = ImageView(context)
    private val buttons = linkedMapOf<ShortcutModifier, TextView>()
    private var themeColors = ThemeColors()
    private var imeVisible = false
    private var panelExpanded = true
    private var imeTransitionToggleVisible = false
    private var panelAnimationGeneration = 0
    private var selectionChangedListener: () -> Unit = {}

    init {
        clipChildren = false
        clipToPadding = false
        buildToggle()
        buildBody()
        applyTheme(themeColors)
        commitBodyPosition(expanded = true)
        applyImeToggleReveal()
    }

    fun setOnSelectionChangedListener(listener: () -> Unit) {
        selectionChangedListener = listener
    }

    fun notifySelectionChanged() {
        renderButtons()
        selectionChangedListener()
    }

    fun resetSelection() {
        selection.reset()
        notifySelectionChanged()
    }

    fun restoreSelection(snapshot: ShortcutSelectionSnapshot) {
        selection.restore(snapshot)
        notifySelectionChanged()
    }

    fun applyTheme(colors: ThemeColors) {
        themeColors = colors
        body.background = topRoundedBackground(colors.inputBackgroundArgb, BODY_CORNER_DP)
        bodyShadow.setSurfaceColor(colors.inputBackgroundArgb)
        toggleShadow.setSurfaceColor(colors.inputBackgroundArgb)
        toggleSurface.background = topRoundedBackground(colors.inputBackgroundArgb, TOGGLE_CORNER_DP)
        toggleIcon.imageTintList = ColorStateList.valueOf(colors.iconArgb)
        renderButtons()
    }

    fun setImeEdgeTranslationY(toggleTranslationY: Float, bodyTranslationY: Float) {
        toggleMotionLayer.translationY = toggleTranslationY
        bodyClipLayer.translationY = bodyTranslationY
    }

    fun prepareForImeHide(bodyOffsetY: Float = 0f) {
        cancelPanelAnimations()
        bodyMotionLayer.translationY = bodyOffsetY
        bodyMotionLayer.visibility = View.VISIBLE
    }

    fun restoreForImeShow() {
        bodyMotionLayer.animate().cancel()
        bodyMotionLayer.translationY = if (panelExpanded) 0f else bodyHeight().toFloat()
        bodyMotionLayer.visibility = if (panelExpanded) View.VISIBLE else View.INVISIBLE
    }

    fun setImeTransitionToggleVisible(visible: Boolean) {
        imeTransitionToggleVisible = visible
        applyImeToggleReveal()
    }

    fun setImeVisible(
        visible: Boolean,
        animate: Boolean = true,
        deferToggleVisibility: Boolean = false,
    ) {
        if (imeVisible == visible) return
        cancelPanelAnimations()
        imeVisible = visible
        imeTransitionToggleVisible = visible && !deferToggleVisibility
        if (visible) {
            panelExpanded = selection.activeModifiers().isNotEmpty()
            if (animate) {
                updatePanelPosition(expanded = panelExpanded, animate = true)
            } else {
                commitBodyPosition(panelExpanded)
                applyImeToggleReveal()
            }
        } else {
            panelExpanded = true
            commitBodyPosition(expanded = true)
            applyImeToggleReveal()
        }
    }

    private fun buildBody() {
        bodyClipLayer.apply {
            clipChildren = true
            clipToPadding = true
        }
        addView(
            bodyClipLayer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        bodyClipLayer.addView(
            bodyMotionLayer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        bodyMotionLayer.addView(
            bodyShadow,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                dp(BODY_SHADOW_HEIGHT_DP + BODY_CORNER_DP),
                Gravity.BOTTOM,
            ).apply {
                bottomMargin = dp(BODY_HEIGHT_DP - BODY_CORNER_DP)
            },
        )

        body.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(BODY_PADDING_DP),
                dp(BODY_PADDING_DP),
                dp(BODY_PADDING_DP),
                dp(BODY_PADDING_DP),
            )
        }
        bodyMotionLayer.addView(
            body,
            LayoutParams(LayoutParams.MATCH_PARENT, dp(BODY_HEIGHT_DP), Gravity.BOTTOM),
        )

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            weightSum = BUTTON_WEIGHT_SUM
        }
        body.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(BUTTON_HEIGHT_DP)))

        addModifierButton(row, ShortcutModifier.SHIFT, R.string.shortcut_shift, 63f)
        addModifierButton(row, ShortcutModifier.CONTROL, R.string.shortcut_control, 81f)
        addModifierButton(row, ShortcutModifier.ALT, R.string.shortcut_alt, 81f)
        addModifierButton(row, ShortcutModifier.META, R.string.shortcut_meta, 92f)
    }

    private fun buildToggle() {
        toggleMotionLayer.apply {
            clipChildren = false
            clipToPadding = false
        }
        addView(
            toggleMotionLayer,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        toggle.apply {
            clipChildren = false
            clipToPadding = false
            visibility = View.VISIBLE
            isFocusable = false
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityDelegate = object : AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfo,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = Button::class.java.name
                }
            }
            contentDescription = context.getString(R.string.shortcut_panel_expand)
            setOnClickListener {
                if (!imeVisible) return@setOnClickListener
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                panelExpanded = !panelExpanded
                updatePanelPosition(panelExpanded, animate = true)
            }
        }
        toggleMotionLayer.addView(
            toggle,
            LayoutParams(dp(TOGGLE_WIDTH_DP), dp(TOGGLE_HEIGHT_DP), Gravity.TOP or Gravity.START).apply {
                marginStart = dp(TOGGLE_MARGIN_START_DP)
            },
        )

        toggle.addView(
            toggleShadow,
            LayoutParams(
                dp(TOGGLE_WIDTH_DP + TOGGLE_SHADOW_OUTSET_DP * 2),
                dp(TOGGLE_SHADOW_OUTSET_DP + TOGGLE_CORNER_DP),
                Gravity.TOP or Gravity.START,
            ).apply {
                topMargin = -dp(TOGGLE_SHADOW_OUTSET_DP)
                marginStart = -dp(TOGGLE_SHADOW_OUTSET_DP)
            },
        )
        toggle.addView(
            toggleSurface,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        toggleIcon.apply {
            setImageResource(R.drawable.ic_arrow_up)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        toggleSurface.addView(
            toggleIcon,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    private fun addModifierButton(
        row: LinearLayout,
        modifier: ShortcutModifier,
        labelResource: Int,
        weight: Float,
    ) {
        val button = TextView(context).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setText(labelResource)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isClickable = true
            isFocusable = false
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(4), 0, dp(4), 0)
        }
        buttons[modifier] = button
        row.addView(
            button,
            LinearLayout.LayoutParams(0, dp(BUTTON_HEIGHT_DP), weight).apply {
                if (buttons.size > 1) marginStart = dp(BUTTON_GAP_DP)
            },
        )
        bindModifierGesture(button, modifier)
    }

    @Suppress("ClickableViewAccessibility")
    private fun bindModifierGesture(button: TextView, modifier: ShortcutModifier) {
        button.setOnClickListener {
            selection.tap(modifier)
            button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            notifySelectionChanged()
        }
        var tracking = false
        var longPressTriggered = false
        val triggerLongPress = Runnable {
            if (!tracking) return@Runnable
            longPressTriggered = true
            if (selection.longPress(modifier)) {
                button.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                notifySelectionChanged()
            }
        }
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tracking = true
                    longPressTriggered = false
                    view.isPressed = true
                    view.postDelayed(triggerLongPress, LONG_PRESS_MILLIS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.x !in 0f..view.width.toFloat() || event.y !in 0f..view.height.toFloat()) {
                        tracking = false
                        view.isPressed = false
                        view.removeCallbacks(triggerLongPress)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(triggerLongPress)
                    val shouldClick = tracking && !longPressTriggered
                    tracking = false
                    view.isPressed = false
                    if (shouldClick) view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    tracking = false
                    view.isPressed = false
                    view.removeCallbacks(triggerLongPress)
                    true
                }
                else -> true
            }
        }
    }

    private fun renderButtons() {
        buttons.forEach { (modifier, button) ->
            val state = selection.stateOf(modifier)
            val style = shortcutButtonStyle(state, themeColors)
            button.background = roundedBackground(style.fill, style.stroke)
            button.setTextColor(style.textColor)
            applyFontWeight(button, style.fontWeight)
            button.isSelected = state != ShortcutModifierState.OFF
            button.contentDescription = buildString {
                append(button.text)
                append(
                    when (state) {
                        ShortcutModifierState.OFF -> "，未启用"
                        ShortcutModifierState.ARMED -> "，一次性待命"
                        ShortcutModifierState.LATCHED -> "，半锁定"
                    },
                )
            }
        }
    }

    private fun applyFontWeight(button: TextView, weight: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            button.typeface = Typeface.create(Typeface.DEFAULT, weight, false)
            button.paint.isFakeBoldText = false
        } else {
            button.typeface = if (weight >= ACTIVE_BUTTON_FONT_WEIGHT) {
                Typeface.create("sans-serif", Typeface.BOLD)
            } else {
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            button.paint.isFakeBoldText = false
        }
    }

    private fun updatePanelPosition(expanded: Boolean, animate: Boolean) {
        cancelPanelAnimations()
        val rotation = if (expanded) 180f else 0f
        toggle.contentDescription = context.getString(
            if (expanded) R.string.shortcut_panel_collapse else R.string.shortcut_panel_expand,
        )
        if (!animate || !isLaidOut) {
            commitPanelPosition(expanded)
            return
        }

        val travel = bodyHeight().toFloat()
        bodyMotionLayer.visibility = View.VISIBLE
        toggle.isClickable = false
        val animationGeneration = panelAnimationGeneration
        bodyMotionLayer.animate()
            .translationY(if (expanded) 0f else travel)
            .setDuration(PANEL_ANIMATION_MILLIS)
            .start()
        toggle.animate()
            .translationY(togglePosition(expanded))
            .setDuration(PANEL_ANIMATION_MILLIS)
            .withEndAction {
                if (animationGeneration == panelAnimationGeneration) {
                    commitPanelPosition(expanded)
                }
            }
            .start()
        toggleIcon.animate()
            .rotation(rotation)
            .setDuration(PANEL_ANIMATION_MILLIS)
            .start()
    }

    private fun cancelPanelAnimations() {
        panelAnimationGeneration += 1
        bodyMotionLayer.animate().cancel()
        toggle.animate().cancel()
        toggleIcon.animate().cancel()
    }

    private fun commitPanelPosition(expanded: Boolean) {
        val travel = bodyHeight()
        commitBodyPosition(expanded)
        toggle.translationY = togglePosition(expanded)
        toggleIcon.rotation = if (expanded) 180f else 0f
        toggle.isClickable = true
    }

    private fun commitBodyPosition(expanded: Boolean) {
        val travel = bodyHeight().toFloat()
        bodyMotionLayer.translationY = if (expanded) 0f else travel
        bodyMotionLayer.visibility = if (expanded) View.VISIBLE else View.INVISIBLE
    }

    private fun applyImeToggleReveal() {
        val presentationAlpha = if (imeVisible && imeTransitionToggleVisible) 1f else 0f
        toggle.translationY = togglePosition(panelExpanded)
        toggle.alpha = presentationAlpha
        toggleIcon.rotation = if (panelExpanded) 180f else 0f
        val fullyInteractive = presentationAlpha >= 0.999f
        toggle.isClickable = fullyInteractive
        toggle.importantForAccessibility = if (fullyInteractive) {
            View.IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        toggle.contentDescription = context.getString(
            if (panelExpanded) R.string.shortcut_panel_collapse else R.string.shortcut_panel_expand,
        )
    }

    private fun roundedBackground(fill: Int, stroke: Int?): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            color = ColorStateList.valueOf(fill)
            cornerRadius = dp(16).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun topRoundedBackground(fill: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            color = ColorStateList.valueOf(fill)
            val radius = dp(radiusDp).toFloat()
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        }

    private fun bodyHeight(): Int = dp(BODY_HEIGHT_DP)

    private fun togglePosition(expanded: Boolean): Float =
        (if (expanded) 0 else bodyHeight()).toFloat() + SEAM_OVERLAP_PX

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val BODY_HEIGHT_DP = 110
        const val BODY_SHADOW_HEIGHT_DP = 8
        const val BODY_PADDING_DP = 33
        const val BODY_CORNER_DP = 22
        const val TOGGLE_CORNER_DP = 22
        const val TOGGLE_WIDTH_DP = 64
        const val TOGGLE_HEIGHT_DP = 48
        const val TOGGLE_MARGIN_START_DP = 24
        const val TOGGLE_SHADOW_OUTSET_DP = BODY_SHADOW_HEIGHT_DP * 3
        const val BUTTON_HEIGHT_DP = 44
        const val BUTTON_GAP_DP = 10
        const val BUTTON_WEIGHT_SUM = 317f
        const val ACTIVE_BUTTON_FONT_WEIGHT = 700
        const val SEAM_OVERLAP_PX = 1f
        const val LONG_PRESS_MILLIS = 1_000L
        const val PANEL_ANIMATION_MILLIS = 180L
    }
}
