package com.local.virtualkeyboard.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    private val body = LinearLayout(context)
    private val toggleMotionLayer = FrameLayout(context)
    private val toggle = FrameLayout(context)
    private val toggleIcon = ImageView(context)
    private val buttons = linkedMapOf<ShortcutModifier, TextView>()
    private var themeColors = ThemeColors()
    private var imeVisible = false
    private var panelExpanded = true
    private var selectionChangedListener: () -> Unit = {}

    init {
        clipChildren = false
        clipToPadding = false
        buildBody()
        buildToggle()
        applyTheme(themeColors)
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
        toggle.background = topRoundedBackground(colors.inputBackgroundArgb, TOGGLE_CORNER_DP)
        toggleIcon.imageTintList = ColorStateList.valueOf(colors.iconArgb)
        renderButtons()
    }

    fun setImeEdgeTranslationY(translationY: Float) {
        toggleMotionLayer.translationY = translationY
    }

    fun prepareForImeHide(bodyOffsetY: Float = 0f) {
        settlePanelAnimation()
        body.translationY = bodyOffsetY
        body.visibility = View.VISIBLE
    }

    fun restoreForImeShow() {
        settlePanelAnimation()
    }

    fun setImeTransitionToggleVisible(visible: Boolean) {
        if (imeVisible) toggle.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    fun setImeVisible(visible: Boolean, animate: Boolean = true) {
        if (imeVisible == visible) return
        imeVisible = visible
        if (visible) {
            panelExpanded = selection.activeModifiers().isNotEmpty()
            toggle.visibility = View.VISIBLE
            updatePanelPosition(expanded = panelExpanded, animate = animate)
        } else {
            panelExpanded = true
            updatePanelPosition(expanded = true, animate = animate)
            toggle.visibility = View.INVISIBLE
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

        body.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(BODY_PADDING_DP),
                dp(BODY_PADDING_DP),
                dp(BODY_PADDING_DP),
                dp(BODY_PADDING_DP),
            )
        }
        bodyClipLayer.addView(
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
            elevation = dp(8).toFloat()
            visibility = View.INVISIBLE
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
            LayoutParams(dp(64), dp(48), Gravity.TOP or Gravity.START).apply {
                marginStart = dp(24)
            },
        )

        toggleIcon.apply {
            setImageResource(R.drawable.ic_arrow_up)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(8))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        toggle.addView(
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
            setTypeface(typeface, Typeface.BOLD)
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
            val fill = when (state) {
                ShortcutModifierState.OFF -> themeColors.inputBackgroundArgb
                ShortcutModifierState.ARMED -> themeColors.accentArgb
                ShortcutModifierState.LATCHED -> themeColors.primaryTextArgb
            }
            val stroke = if (state == ShortcutModifierState.OFF) themeColors.iconArgb else null
            button.background = roundedBackground(fill, stroke)
            button.setTextColor(
                if (state == ShortcutModifierState.OFF) themeColors.primaryTextArgb else contrastColor(fill),
            )
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

        if (expanded) updatePanelLayering(expanded = true)
        val travel = bodyHeight().toFloat()
        body.visibility = View.VISIBLE
        toggle.isClickable = false
        body.animate()
            .translationY(if (expanded) 0f else travel)
            .setDuration(PANEL_ANIMATION_MILLIS)
            .start()
        toggle.animate()
            .translationY(if (expanded) -travel else travel)
            .setDuration(PANEL_ANIMATION_MILLIS)
            .withEndAction { commitPanelPosition(expanded) }
            .start()
        toggleIcon.animate()
            .rotation(rotation)
            .setDuration(PANEL_ANIMATION_MILLIS)
            .start()
    }

    private fun settlePanelAnimation() {
        cancelPanelAnimations()
        commitPanelPosition(panelExpanded)
    }

    private fun cancelPanelAnimations() {
        body.animate().cancel()
        toggle.animate().cancel()
        toggleIcon.animate().cancel()
    }

    private fun commitPanelPosition(expanded: Boolean) {
        val travel = bodyHeight()
        body.translationY = if (expanded) 0f else travel.toFloat()
        body.visibility = if (expanded) View.VISIBLE else View.INVISIBLE
        (toggle.layoutParams as LayoutParams).also { params ->
            params.topMargin = if (expanded) 0 else travel
            toggle.layoutParams = params
        }
        toggle.translationY = 0f
        toggleIcon.rotation = if (expanded) 180f else 0f
        toggle.isClickable = true
        updatePanelLayering(expanded)
    }

    private fun updatePanelLayering(expanded: Boolean) {
        if (expanded) {
            body.elevation = if (imeVisible) dp(BODY_ELEVATION_DP).toFloat() else 0f
            bodyClipLayer.bringToFront()
        } else {
            body.elevation = 0f
            toggleMotionLayer.bringToFront()
        }
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

    private fun contrastColor(background: Int): Int =
        if (Color.luminance(background) > 0.179f) Color.BLACK else Color.WHITE

    private fun bodyHeight(): Int = dp(BODY_HEIGHT_DP)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val BODY_HEIGHT_DP = 110
        const val BODY_PADDING_DP = 33
        const val BODY_ELEVATION_DP = 4
        const val BODY_CORNER_DP = 22
        const val TOGGLE_CORNER_DP = 22
        const val BUTTON_HEIGHT_DP = 44
        const val BUTTON_GAP_DP = 10
        const val BUTTON_WEIGHT_SUM = 317f
        const val LONG_PRESS_MILLIS = 1_000L
        const val PANEL_ANIMATION_MILLIS = 180L
    }
}
