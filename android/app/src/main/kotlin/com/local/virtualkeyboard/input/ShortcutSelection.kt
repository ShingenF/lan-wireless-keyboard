package com.local.virtualkeyboard.input

import com.local.virtualkeyboard.protocol.ShortcutModifier

enum class ShortcutModifierState { OFF, ARMED, LATCHED }

data class ShortcutSelectionSnapshot(
    val armed: Set<ShortcutModifier>,
    val latched: Set<ShortcutModifier>,
)

class ShortcutSelection {
    private val states = ShortcutModifier.entries.associateWith { ShortcutModifierState.OFF }.toMutableMap()

    val isLatchMode: Boolean
        get() = states.values.any { it == ShortcutModifierState.LATCHED }

    fun stateOf(modifier: ShortcutModifier): ShortcutModifierState = states.getValue(modifier)

    fun activeModifiers(): List<ShortcutModifier> =
        ShortcutModifier.entries.filter { stateOf(it) != ShortcutModifierState.OFF }

    fun snapshot(): ShortcutSelectionSnapshot = ShortcutSelectionSnapshot(
        armed = states.filterValues { it == ShortcutModifierState.ARMED }.keys,
        latched = states.filterValues { it == ShortcutModifierState.LATCHED }.keys,
    )

    fun restore(snapshot: ShortcutSelectionSnapshot) {
        require(snapshot.armed.intersect(snapshot.latched).isEmpty()) {
            "A shortcut modifier cannot be both armed and latched."
        }
        states.keys.forEach { modifier ->
            states[modifier] = when (modifier) {
                in snapshot.armed -> ShortcutModifierState.ARMED
                in snapshot.latched -> ShortcutModifierState.LATCHED
                else -> ShortcutModifierState.OFF
            }
        }
    }

    fun tap(modifier: ShortcutModifier) {
        states[modifier] = when (stateOf(modifier)) {
            ShortcutModifierState.OFF ->
                if (isLatchMode) ShortcutModifierState.LATCHED else ShortcutModifierState.ARMED
            ShortcutModifierState.ARMED -> ShortcutModifierState.OFF
            ShortcutModifierState.LATCHED -> ShortcutModifierState.OFF
        }
    }

    fun longPress(modifier: ShortcutModifier): Boolean {
        if (stateOf(modifier) == ShortcutModifierState.LATCHED) return false
        states.entries.forEach { entry ->
            if (entry.value == ShortcutModifierState.ARMED) {
                entry.setValue(ShortcutModifierState.LATCHED)
            }
        }
        states[modifier] = ShortcutModifierState.LATCHED
        return true
    }

    fun clearArmedAfterQueued(): Boolean {
        var changed = false
        states.entries.forEach { entry ->
            if (entry.value == ShortcutModifierState.ARMED) {
                entry.setValue(ShortcutModifierState.OFF)
                changed = true
            }
        }
        return changed
    }

    fun reset() {
        states.keys.forEach { states[it] = ShortcutModifierState.OFF }
    }
}
