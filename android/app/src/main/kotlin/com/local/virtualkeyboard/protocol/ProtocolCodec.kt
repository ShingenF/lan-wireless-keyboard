package com.local.virtualkeyboard.protocol

object ProtocolCodec {
    fun encodeCommand(command: OutgoingCommand, seq: Long, timestamp: Long): String {
        val (type, fields) = when (command) {
            is OutgoingCommand.TextCommit ->
                "textCommit" to
                    ",\"text\":\"${escape(command.text)}\"${physicalKeyPreference(command.preferPhysicalKeys)}"
            is OutgoingCommand.ReplaceTail ->
                "replaceTail" to
                    ",\"deleteCodePoints\":${command.deleteCodePoints}," +
                    "\"text\":\"${escape(command.text)}\"${physicalKeyPreference(command.preferPhysicalKeys)}"
            is OutgoingCommand.KeyPress -> "keyPress" to ",\"key\":\"${command.key.wireName}\""
            is OutgoingCommand.KeyState ->
                "keyState" to
                    ",\"key\":\"${command.key.wireName}\",\"action\":\"${command.action.wireName}\""
            is OutgoingCommand.SystemShortcutPress ->
                "systemShortcut" to ",\"shortcut\":\"${command.shortcut.wireName}\""
            is OutgoingCommand.PointerMove ->
                "pointerMove" to ",\"dx\":${command.dx},\"dy\":${command.dy}"
            is OutgoingCommand.PointerButton ->
                "pointerButton" to
                    ",\"button\":\"${command.button.wireName}\",\"action\":\"${command.action.wireName}\""
            is OutgoingCommand.Wheel -> "wheel" to ",\"delta\":${command.delta}"
            OutgoingCommand.Ping -> "ping" to ""
        }
        return "{\"version\":1,\"type\":\"$type\",\"seq\":$seq,\"timestamp\":$timestamp$fields}"
    }

    fun encodeAuth(proof: String, device: String): String =
        "{\"version\":1,\"type\":\"auth\",\"proof\":\"${escape(proof)}\",\"device\":\"${escape(device)}\"}"

    internal fun escape(value: String): String = buildString(value.length) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
    }

    private fun physicalKeyPreference(enabled: Boolean): String =
        if (enabled) ",\"preferPhysicalKeys\":true" else ""
}
