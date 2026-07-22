package com.local.virtualkeyboard.ui

internal enum class TapAction {
    LEFT_CLICK,
    RIGHT_CLICK,
    LEFT_BUTTON_DOWN,
    LEFT_BUTTON_UP,
}

/** Resolves taps, tap-to-drag, and multi-touch without moving tap-sized contacts. */
internal class TapSequenceResolver(
    private val doubleTapTimeoutMillis: Long,
    private val doubleTapSlop: Float,
    private val touchSlop: Float = doubleTapSlop,
    private val tapTimeoutMillis: Long = doubleTapTimeoutMillis,
    private val longPressTimeoutMillis: Long = 1_000L,
) {
    private data class PendingTap(val x: Float, val y: Float, val timeMillis: Long)

    private var pendingTap: PendingTap? = null
    private var secondTapArmed = false
    private var secondTapDown: PendingTap? = null
    private var dragActive = false
    private var currentContactDownTimeMillis: Long? = null
    private var longPressEligible = false
    private var multiTouchActive = false
    private var multiTouchMoved = false
    private var multiTouchRightClickEligible = false
    private var multiTouchDownTimeMillis = 0L

    fun onDown(x: Float, y: Float, eventTimeMillis: Long): List<TapAction> {
        currentContactDownTimeMillis = eventTimeMillis
        longPressEligible = true
        val previous = pendingTap ?: return emptyList()
        if (!isDoubleTap(previous, x, y, eventTimeMillis)) {
            pendingTap = null
            return listOf(TapAction.LEFT_CLICK)
        }

        secondTapArmed = true
        secondTapDown = PendingTap(x, y, eventTimeMillis)
        return emptyList()
    }

    fun onTap(x: Float, y: Float, eventTimeMillis: Long): List<TapAction> {
        currentContactDownTimeMillis = null
        longPressEligible = false
        if (secondTapArmed) {
            secondTapArmed = false
            secondTapDown = null
            pendingTap = null
            return listOf(TapAction.LEFT_CLICK, TapAction.LEFT_CLICK)
        }

        pendingTap = PendingTap(x, y, eventTimeMillis)
        return emptyList()
    }

    fun onTapTimeout(eventTimeMillis: Long): List<TapAction> {
        val pending = pendingTap ?: return emptyList()
        if (secondTapArmed || eventTimeMillis - pending.timeMillis < doubleTapTimeoutMillis) {
            return emptyList()
        }
        pendingTap = null
        return listOf(TapAction.LEFT_CLICK)
    }

    fun onContactAborted(): List<TapAction> {
        currentContactDownTimeMillis = null
        longPressEligible = false
        if (dragActive) {
            dragActive = false
            return listOf(TapAction.LEFT_BUTTON_UP)
        }
        if (!secondTapArmed) return emptyList()
        secondTapArmed = false
        secondTapDown = null
        if (pendingTap == null) return emptyList()
        pendingTap = null
        return listOf(TapAction.LEFT_CLICK)
    }

    fun onMove(x: Float, y: Float): List<TapAction> {
        longPressEligible = false
        if (!secondTapArmed || dragActive) return emptyList()
        val down = secondTapDown ?: return emptyList()
        val dx = x - down.x
        val dy = y - down.y
        if (dx * dx + dy * dy <= touchSlop * touchSlop) return emptyList()

        secondTapArmed = false
        secondTapDown = null
        pendingTap = null
        dragActive = true
        return listOf(TapAction.LEFT_BUTTON_DOWN)
    }

    fun onLongPress(eventTimeMillis: Long): List<TapAction> {
        val downTimeMillis = currentContactDownTimeMillis ?: return emptyList()
        if (!longPressEligible || dragActive || multiTouchActive) return emptyList()
        if (eventTimeMillis - downTimeMillis < longPressTimeoutMillis) return emptyList()

        pendingTap = null
        secondTapArmed = false
        secondTapDown = null
        longPressEligible = false
        dragActive = true
        return listOf(TapAction.LEFT_BUTTON_DOWN)
    }

    fun onGestureEnded(): List<TapAction> {
        currentContactDownTimeMillis = null
        longPressEligible = false
        if (!dragActive) return onContactAborted()
        dragActive = false
        return listOf(TapAction.LEFT_BUTTON_UP)
    }

    fun onMultiTouchStarted(
        gestureStartTimeMillis: Long,
        rightClickEligible: Boolean,
    ): List<TapAction> {
        if (multiTouchActive) return emptyList()
        val actions = mutableListOf<TapAction>()
        if (dragActive) actions += TapAction.LEFT_BUTTON_UP
        else if (pendingTap != null) actions += TapAction.LEFT_CLICK

        pendingTap = null
        secondTapArmed = false
        secondTapDown = null
        dragActive = false
        currentContactDownTimeMillis = null
        longPressEligible = false
        multiTouchActive = true
        multiTouchMoved = false
        multiTouchRightClickEligible = rightClickEligible
        multiTouchDownTimeMillis = gestureStartTimeMillis
        return actions
    }

    fun onMultiTouchMoved() {
        if (multiTouchActive) multiTouchMoved = true
    }

    fun isMultiTouchActive(): Boolean = multiTouchActive

    fun onMultiTouchEnded(eventTimeMillis: Long): List<TapAction> {
        if (!multiTouchActive) return emptyList()
        val duration = eventTimeMillis - multiTouchDownTimeMillis
        val wasTap = multiTouchRightClickEligible &&
            !multiTouchMoved &&
            duration in 0..tapTimeoutMillis
        multiTouchActive = false
        multiTouchMoved = false
        multiTouchRightClickEligible = false
        multiTouchDownTimeMillis = 0L
        return if (wasTap) listOf(TapAction.RIGHT_CLICK) else emptyList()
    }

    fun cancel(): List<TapAction> {
        val actions = when {
            dragActive -> listOf(TapAction.LEFT_BUTTON_UP)
            secondTapArmed && pendingTap != null -> listOf(TapAction.LEFT_CLICK)
            else -> emptyList()
        }
        pendingTap = null
        secondTapArmed = false
        secondTapDown = null
        dragActive = false
        currentContactDownTimeMillis = null
        longPressEligible = false
        multiTouchActive = false
        multiTouchMoved = false
        multiTouchRightClickEligible = false
        multiTouchDownTimeMillis = 0L
        return actions
    }

    private fun isDoubleTap(previous: PendingTap, x: Float, y: Float, eventTimeMillis: Long): Boolean {
        if (eventTimeMillis - previous.timeMillis !in 0..doubleTapTimeoutMillis) return false
        val dx = x - previous.x
        val dy = y - previous.y
        return dx * dx + dy * dy <= doubleTapSlop * doubleTapSlop
    }
}
