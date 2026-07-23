package com.local.virtualkeyboard.protocol

sealed interface OutgoingCommand {
    data class TextCommit(
        val text: String,
        val preferPhysicalKeys: Boolean = false,
    ) : OutgoingCommand
    data class ReplaceTail(
        val deleteCodePoints: Int,
        val text: String,
        val preferPhysicalKeys: Boolean = false,
    ) : OutgoingCommand
    data class KeyPress(val key: RemoteKey) : OutgoingCommand
    data class KeyState(val key: GameKey, val action: ButtonAction) : OutgoingCommand
    data class SystemShortcutPress(val shortcut: SystemShortcut) : OutgoingCommand
    data class ShortcutChord(
        val modifiers: List<ShortcutModifier>,
        val key: ShortcutKey,
    ) : OutgoingCommand {
        init {
            require(modifiers.isNotEmpty()) { "Shortcut chord requires at least one modifier." }
            require(modifiers.distinct().size == modifiers.size) { "Shortcut modifiers must be unique." }
        }
    }
    data class PointerMove(val dx: Int, val dy: Int) : OutgoingCommand
    data class PointerButton(val button: MouseButton, val action: ButtonAction) : OutgoingCommand
    data class Wheel(val delta: Int) : OutgoingCommand
    data object Ping : OutgoingCommand
}

enum class SystemShortcut(val wireName: String) {
    SHIFT("shift"),
    CONTROL_SPACE("controlSpace"),
    CAPS_LOCK("capsLock"),
    WINDOWS_SPACE("windowsSpace"),
    CONTROL_SHIFT("controlShift"),
    ALT_SHIFT("altShift"),
    ;

}

enum class ShortcutModifier(val wireName: String) {
    SHIFT("shift"),
    CONTROL("control"),
    ALT("alt"),
    META("meta"),
}

sealed interface ShortcutKey {
    val wireName: String

    data class Character(val value: Char) : ShortcutKey {
        init {
            require(value.code in 0x21..0x7E) { "Shortcut character must be printable ASCII." }
        }

        override val wireName: String = value.toString()
    }

    data object Space : ShortcutKey { override val wireName = "space" }
    data object Enter : ShortcutKey { override val wireName = "enter" }
    data object Backspace : ShortcutKey { override val wireName = "backspace" }
}

enum class RemoteKey(val wireName: String) {
    UP("up"),
    DOWN("down"),
    LEFT("left"),
    RIGHT("right"),
    BACKSPACE("backspace"),
    ENTER("enter"),
    ESCAPE("escape"),
}

enum class GameKey(val wireName: String) {
    W("w"),
    A("a"),
    S("s"),
    D("d"),
}

enum class MouseButton(val wireName: String) {
    LEFT("left"),
    RIGHT("right"),
}

enum class ButtonAction(val wireName: String) {
    DOWN("down"),
    UP("up"),
}
